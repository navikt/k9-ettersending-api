package no.nav.k9.vedlegg

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.github.kittinunf.fuel.core.Request
import com.github.kittinunf.fuel.coroutines.awaitStringResponseResult
import com.github.kittinunf.fuel.httpDelete
import com.github.kittinunf.fuel.httpGet
import com.github.kittinunf.fuel.httpPost
import io.ktor.http.HttpHeaders
import io.ktor.http.Url
import no.nav.helse.dusseldorf.ktor.client.buildURL
import no.nav.helse.dusseldorf.ktor.core.Retry.Companion.retry
import no.nav.helse.dusseldorf.ktor.jackson.dusseldorfConfigured
import no.nav.helse.dusseldorf.ktor.metrics.Operation.Companion.monitored
import no.nav.k9.general.CallId
import no.nav.k9.general.auth.IdToken
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import java.net.URI
import java.time.Duration

class K9DokumentGateway(
    baseUrl : URI
) {

    private companion object {
        private val logger: Logger = LoggerFactory.getLogger(K9DokumentGateway::class.java)
        private val objectMapper = jacksonObjectMapper().dusseldorfConfigured().configure(SerializationFeature.WRITE_DURATIONS_AS_TIMESTAMPS, false)
        private const val SLETTE_VEDLEGG_OPERATION = "slette-vedlegg"
        private const val HENTE_VEDLEGG_OPERATION = "hente-vedlegg"
        private const val LAGRE_VEDLEGG_OPERATION = "lagre-vedlegg"
    }

    private val url = Url.buildURL(
        baseUrl = baseUrl,
        pathParts = listOf("v1", "dokument")
    )

    suspend fun hentVedlegg(
        vedleggId: VedleggId,
        idToken: IdToken,
        callId: CallId
    ) : Vedlegg? {

        val urlMedId = Url.buildURL(
            baseUrl = url,
            pathParts = listOf(vedleggId.value)
        )

        val httpRequest = urlMedId
            .toString()
            .httpGet()
            .header(
                HttpHeaders.Authorization to "Bearer ${idToken.value}",
                HttpHeaders.Accept to "application/json",
                HttpHeaders.XCorrelationId to callId.value
            )

        return retry(
            operation = HENTE_VEDLEGG_OPERATION,
            initialDelay = Duration.ofMillis(200),
            factor = 2.0,
            logger = logger
        ) {
            val (request, response, result) = monitored(
                app = "k9-ettersending-api",
                operation = HENTE_VEDLEGG_OPERATION,
                resultResolver = { 200 == it.second.statusCode }
            ) { httpRequest.awaitStringResponseResult() }

            result.fold(
                { success -> ResolvedVedlegg(objectMapper.readValue<Vedlegg>(success)) },
                { error ->
                    if (404 == response.statusCode) ResolvedVedlegg()
                    else {
                        logger.error("Error response = '${error.response.body().asString("text/plain")}' fra '${request.url}'")
                        logger.error(error.toString())
                        throw IllegalStateException("Feil ved henting av vedlegg.")
                    }
                }
            )
        }.vedlegg
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
                app = "k9-ettersending-api",
                operation = LAGRE_VEDLEGG_OPERATION,
                resultResolver = { 201 == it.second.statusCode }
            ) {
                val contentStream = { ByteArrayInputStream(body) }

                url
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
                    logger.error("Error response = '${error.response.body().asString("text/plain")}' fra '${request.url}'")
                    logger.error(error.toString())
                    throw IllegalStateException("Feil ved lagring av vedlegg.")
                })
        }
    }

    suspend fun slettVedlegg(
        vedleggId: VedleggId,
        idToken: IdToken,
        callId: CallId
    ) : Boolean {

        val urlMedId = Url.buildURL(
            baseUrl = url,
            pathParts = listOf(vedleggId.value)
        )

        val httpRequest = urlMedId
            .toString()
            .httpDelete()
            .header(
                HttpHeaders.Authorization to "Bearer ${idToken.value}",
                HttpHeaders.XCorrelationId to callId.value
            )

        return try { requestSlettVedlegg(httpRequest)}
        catch (cause: Throwable) {
            logger.error("Fikk ikke slettet vedlegg.")
            false
        }
    }

    private suspend fun requestSlettVedlegg(
        httpRequest: Request
    ) : Boolean = retry(
            operation = SLETTE_VEDLEGG_OPERATION,
            initialDelay = Duration.ofMillis(200),
            factor = 2.0,
            logger = logger
    ) {
        val (request, _, result) = monitored(
            app = "k9-ettersending-api",
            operation = SLETTE_VEDLEGG_OPERATION,
            resultResolver = { 204 == it.second.statusCode }
        ) { httpRequest.awaitStringResponseResult() }

        result.fold(
            { true },
            { error -> {
                logger.error("Error response = '${error.response.body().asString("text/plain")}' fra '${request.url}'")
                logger.error(error.toString())
                throw IllegalStateException("Feil ved sletting av vedlegg.")
            }}
        )
        false
    }
}

data class CreatedResponseEntity(val id : String)
private data class ResolvedVedlegg(val vedlegg: Vedlegg? = null)
