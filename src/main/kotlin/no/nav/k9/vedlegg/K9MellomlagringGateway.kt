package no.nav.k9.vedlegg

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.github.kittinunf.fuel.core.Request
import com.github.kittinunf.fuel.coroutines.awaitStringResponseResult
import com.github.kittinunf.fuel.httpDelete
import com.github.kittinunf.fuel.httpPost
import com.github.kittinunf.fuel.httpPut
import io.ktor.http.*
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import no.nav.helse.dusseldorf.ktor.client.buildURL
import no.nav.helse.dusseldorf.ktor.core.Retry.Companion.retry
import no.nav.helse.dusseldorf.ktor.health.HealthCheck
import no.nav.helse.dusseldorf.ktor.health.Healthy
import no.nav.helse.dusseldorf.ktor.health.Result
import no.nav.helse.dusseldorf.ktor.health.UnHealthy
import no.nav.helse.dusseldorf.ktor.metrics.Operation
import no.nav.helse.dusseldorf.ktor.metrics.Operation.Companion.monitored
import no.nav.helse.dusseldorf.oauth2.client.AccessTokenClient
import no.nav.helse.dusseldorf.oauth2.client.CachedAccessTokenClient
import no.nav.k9.general.CallId
import no.nav.k9.general.auth.IdToken
import no.nav.k9.k9MellomlagringKonfigurert
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import java.net.URI
import java.time.Duration

class K9MellomlagringGateway(
    private val accessTokenClient: AccessTokenClient,
    private val k9MellomlagringScope: Set<String>,
    private val baseUrl: URI
) : HealthCheck {

    private companion object {
        private val logger: Logger = LoggerFactory.getLogger(K9MellomlagringGateway::class.java)
        private val objectMapper = jacksonObjectMapper().k9MellomlagringKonfigurert()
        private const val SLETTE_VEDLEGG_OPERATION = "slette-vedlegg"
        private const val HENTE_VEDLEGG_OPERATION = "hente-vedlegg"
        private const val LAGRE_VEDLEGG_OPERATION = "lagre-vedlegg"
        private const val PERSISTER_VEDLEGG = "persister-vedlegg"
        private const val SLETT_PERSISTERT_VEDLEGG = "slett-persistert-vedlegg"
        private const val TJENESTE = "k9-ettersending-api"
    }

    private val cachedAccessTokenClient = CachedAccessTokenClient(accessTokenClient)
    private val komplettUrl = Url.buildURL(
        baseUrl = baseUrl,
        pathParts = listOf("v1", "dokument")
    )

    override suspend fun check(): Result {
        return try {
            accessTokenClient.getAccessToken(k9MellomlagringScope)
            Healthy("K9MellomlagringGateway", "Henting av access token for K9MellomlagringGateway.")
        } catch (cause: Throwable) {
            logger.error("Feil ved henting av access token for K9MellomlagringGateway", cause)
            UnHealthy("K9MellomlagringGateway", "Henting av access token for K9MellomlagringGateway.")
        }
    }

    suspend fun lagreVedlegg(
        vedlegg: Vedlegg,
        idToken: IdToken,
        callId: CallId
    ): VedleggId {
        val body = objectMapper.writeValueAsBytes(vedlegg)

        return retry(
            operation = LAGRE_VEDLEGG_OPERATION,
            initialDelay = Duration.ofMillis(200),
            factor = 2.0,
            logger = logger
        ) {
            val (request, _, result) = monitored(
                app = TJENESTE,
                operation = LAGRE_VEDLEGG_OPERATION,
                resultResolver = { 201 == it.second.statusCode }
            ) {
                val contentStream = { ByteArrayInputStream(body) }

                komplettUrl
                    .toString()
                    .httpPost()
                    .body(contentStream)
                    .header(
                        HttpHeaders.Authorization to "Bearer ${idToken.value}",
                        HttpHeaders.ContentType to "application/json",
                        HttpHeaders.Accept to "application/json",
                        HttpHeaders.XCorrelationId to callId.value
                    )
                    .awaitStringResponseResult()
            }
            result.fold(
                { success -> VedleggId(objectMapper.readValue<CreatedResponseEntity>(success).id) },
                { error ->
                    logger.error(
                        "Error response = '${
                            error.response.body().asString("text/plain")
                        }' fra '${request.url}'"
                    )
                    logger.error(error.toString())
                    throw IllegalStateException("Feil ved lagring av vedlegg.")
                })
        }
    }

    suspend fun slettVedlegg(
        vedleggId: VedleggId,
        idToken: IdToken,
        callId: CallId,
        eier: DokumentEier
    ): Boolean {
        val body = objectMapper.writeValueAsBytes(eier)

        val urlMedId = Url.buildURL(
            baseUrl = komplettUrl,
            pathParts = listOf(vedleggId.value)
        )

        val httpRequest = urlMedId
            .toString()
            .httpDelete()
            .body(body)
            .header(
                HttpHeaders.Authorization to "Bearer ${idToken.value}",
                HttpHeaders.XCorrelationId to callId.value,
                HttpHeaders.ContentType to "application/json"
            )
        return requestSlettVedlegg(httpRequest)
    }

    private suspend fun requestSlettVedlegg(
        httpRequest: Request
    ): Boolean = retry(
        operation = SLETTE_VEDLEGG_OPERATION,
        initialDelay = Duration.ofMillis(200),
        factor = 2.0,
        logger = logger
    ) {
        val (request, _, result) = monitored(
            app = TJENESTE,
            operation = SLETTE_VEDLEGG_OPERATION,
            resultResolver = { 204 == it.second.statusCode }
        ) { httpRequest.awaitStringResponseResult() }

        result.fold(
            { _ ->
                logger.info("Suksess ved sletting av vedlegg")
                true
            },
            { error ->
                logger.error("Error response = '${error.response.body().asString("text/plain")}' fra '${request.url}'")
                logger.error(error.toString())
                throw IllegalStateException("Feil ved sletting av vedlegg.")
            }
        )
    }

    private suspend fun requestHentVedlegg(
        httpRequest: Request
    ): Vedlegg? = retry(
        operation = HENTE_VEDLEGG_OPERATION,
        initialDelay = Duration.ofMillis(200),
        factor = 2.0,
        logger = logger
    ) {
        val (request, response, result) = monitored(
            app = TJENESTE,
            operation = HENTE_VEDLEGG_OPERATION,
            resultResolver = { 200 == it.second.statusCode }
        ) { httpRequest.awaitStringResponseResult() }

        result.fold(
            { success ->
                logger.info("Suksess ved henting av vedlegg")
                ResolvedVedlegg(objectMapper.readValue<Vedlegg>(success))
            },
            { error ->
                if (404 == response.statusCode) ResolvedVedlegg()
                else {
                    logger.error(
                        "Error response = '${
                            error.response.body().asString("text/plain")
                        }' fra '${request.url}'"
                    )
                    logger.error(error.toString())
                    throw IllegalStateException("Feil ved henting av vedlegg.")
                }
            }
        ).vedlegg
    }

    internal suspend fun persisterVedlegg(
        vedleggId: List<VedleggId>,
        callId: CallId,
        eier: DokumentEier
    ) {
        val authorizationHeader: String = cachedAccessTokenClient.getAccessToken(k9MellomlagringScope).asAuthoriationHeader()

        coroutineScope {
            val deferred = mutableListOf<Deferred<Unit>>()
            vedleggId.forEach {
                deferred.add(async {
                    requestPersisterVedlegg(
                        vedleggId = it,
                        callId = callId,
                        eier = eier,
                        authorizationHeader = authorizationHeader
                    )
                })
            }
            deferred.awaitAll()
        }
    }


    private suspend fun requestPersisterVedlegg(
        vedleggId: VedleggId,
        callId: CallId,
        eier: DokumentEier,
        authorizationHeader: String
    ) {

        val urlMedId = Url.buildURL(
            baseUrl = komplettUrl,
            pathParts = listOf(vedleggId.value, "persister")
        )

        val body = objectMapper.writeValueAsBytes(eier)

        val httpRequest = urlMedId.toString()
            .httpPut()
            .body(body)
            .header(
                HttpHeaders.Authorization to authorizationHeader,
                HttpHeaders.XCorrelationId to callId.value,
                HttpHeaders.ContentType to "application/json"
            )

        val (request, _, result) = Operation.monitored(
            app = TJENESTE,
            operation = PERSISTER_VEDLEGG,
            resultResolver = { 204 == it.second.statusCode }
        ) {
            httpRequest.awaitStringResponseResult()
        }

        result.fold(
            { _ -> logger.info("Vellykket persistering av vedlegg") },
            { error ->
                logger.error("Error response = '${error.response.body().asString("text/plain")}' fra '${request.url}'")
                logger.error("Feil ved persistering av vedlegg. $error")
                throw IllegalStateException("Feil ved persistering av vedlegg.")
            }
        )
    }

    internal suspend fun slettPersistertVedlegg(
        vedleggId: List<VedleggId>,
        callId: CallId,
        eier: DokumentEier
    ) {
        val authorizationHeader: String =
            cachedAccessTokenClient.getAccessToken(k9MellomlagringScope).asAuthoriationHeader()

        coroutineScope {
            val deferred = mutableListOf<Deferred<Unit>>()
            vedleggId.forEach {
                deferred.add(async {
                    requestSlettPersisterVedlegg(
                        vedleggId = it,
                        callId = callId,
                        eier = eier,
                        authorizationHeader = authorizationHeader
                    )
                })
            }
            deferred.awaitAll()
        }
    }

    private suspend fun requestSlettPersisterVedlegg(
        vedleggId: VedleggId,
        callId: CallId,
        eier: DokumentEier,
        authorizationHeader: String
    ) {

        val urlMedId = Url.buildURL(
            baseUrl = komplettUrl,
            pathParts = listOf(vedleggId.value)
        )

        val body = objectMapper.writeValueAsBytes(eier)

        val httpRequest = urlMedId.toString()
            .httpDelete()
            .body(body)
            .header(
                HttpHeaders.Authorization to authorizationHeader,
                HttpHeaders.XCorrelationId to callId.value,
                HttpHeaders.ContentType to "application/json"
            )

        val (request, _, result) = Operation.monitored(
            app = TJENESTE,
            operation = SLETT_PERSISTERT_VEDLEGG,
            resultResolver = { 204 == it.second.statusCode }
        ) {
            httpRequest.awaitStringResponseResult()
        }


        result.fold(
            { _ -> logger.info("Vellykket sletting av persistert vedlegg") },
            { error ->
                logger.error("Error response = '${error.response.body().asString("text/plain")}' fra '${request.url}'")
                logger.error("Feil ved sletting av persistert vedlegg. $error")
                throw IllegalStateException("Feil ved sletting av persistert vedlegg.")
            }
        )
    }




    suspend fun hentVedlegg(vedleggId: VedleggId, idToken: IdToken, eier: DokumentEier, callId: CallId): Vedlegg? {
        val body = objectMapper.writeValueAsBytes(eier)

        val urlMedId = Url.buildURL(
            baseUrl = komplettUrl,
            pathParts = listOf(vedleggId.value)
        )

        val httpRequest = urlMedId
            .toString()
            .httpPost()
            .body(body)
            .header(
                HttpHeaders.Authorization to "Bearer ${idToken.value}",
                HttpHeaders.XCorrelationId to callId.value,
                HttpHeaders.ContentType to "application/json",
                HttpHeaders.Accept to "application/json"
            )
        return requestHentVedlegg(httpRequest)
    }

}

data class CreatedResponseEntity(val id: String)
private data class ResolvedVedlegg(val vedlegg: Vedlegg? = null)
