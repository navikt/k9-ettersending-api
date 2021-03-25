package no.nav.k9

import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.github.kittinunf.fuel.coroutines.awaitStringResponseResult
import com.github.kittinunf.fuel.httpPost
import io.ktor.http.*
import no.nav.helse.dusseldorf.ktor.client.buildURL
import no.nav.helse.dusseldorf.ktor.health.HealthCheck
import no.nav.helse.dusseldorf.ktor.health.Healthy
import no.nav.helse.dusseldorf.ktor.health.Result
import no.nav.helse.dusseldorf.ktor.health.UnHealthy
import no.nav.helse.dusseldorf.ktor.jackson.dusseldorfConfigured
import no.nav.helse.dusseldorf.ktor.metrics.Operation
import no.nav.helse.dusseldorf.oauth2.client.AccessTokenClient
import no.nav.helse.dusseldorf.oauth2.client.CachedAccessTokenClient
import no.nav.k9.ettersendelse.Ettersendelse
import no.nav.k9.ettersending.KomplettEttersending
import no.nav.k9.general.CallId
import no.nav.k9.general.auth.ApiGatewayApiKey
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import java.net.URI

class K9EttersendingMottakGateway(
    baseUrl: URI,
    private val accessTokenClient: AccessTokenClient,
    private val sendeSoknadTilProsesseringScopes: Set<String>,
    private val apiGatewayApiKey: ApiGatewayApiKey
) : HealthCheck {

    private companion object {
        private val logger: Logger = LoggerFactory.getLogger(K9EttersendingMottakGateway::class.java)
        private val objectMapper = jacksonObjectMapper().dusseldorfConfigured()
            .setPropertyNamingStrategy(PropertyNamingStrategies.LOWER_CAMEL_CASE)
            .configure(SerializationFeature.WRITE_DURATIONS_AS_TIMESTAMPS, false)
    }

    private val komplettUrlEttersend = Url.buildURL(
        baseUrl = baseUrl,
        pathParts = listOf("v1", "ettersend")
    ).toString()

    private val cachedAccessTokenClient = CachedAccessTokenClient(accessTokenClient)

    override suspend fun check(): Result {
        return try {
            accessTokenClient.getAccessToken(sendeSoknadTilProsesseringScopes)
            Healthy("K9EttersendingMottakGateway", "Henting av access token for å legge søknad til prosessering OK.")
        } catch (cause: Throwable) {
            logger.error("Feil ved henting av access token for å legge søknad til prosessering", cause)
            UnHealthy("K9EttersendingMottakGateway", "Henting av access token for å legge søknad til prosessering.")
        }
    }

    suspend fun leggTilProsesseringEttersending(
        ettersending: KomplettEttersending,
        callId: CallId
    ) {
        val authorizationHeader = cachedAccessTokenClient.getAccessToken(sendeSoknadTilProsesseringScopes).asAuthoriationHeader()

        val body = objectMapper.writeValueAsBytes(ettersending)
        val contentStream = { ByteArrayInputStream(body) }

        logger.info("K9Format som sendes videre fra API: ${Ettersendelse.SerDes.serialize(ettersending.k9Format)}") //TODO 24.03.2021 - Fjernes før prodsettes

        val httpRequet = komplettUrlEttersend
            .httpPost()
            .timeout(20_000)
            .timeoutRead(20_000)
            .body(contentStream)
            .header(
                HttpHeaders.ContentType to "application/json",
                HttpHeaders.XCorrelationId to callId.value,
                HttpHeaders.Authorization to authorizationHeader,
                apiGatewayApiKey.headerKey to apiGatewayApiKey.value
            )

        val (request, _, result) = Operation.monitored(
            app = "k9-ettersending-api",
            operation = "ettersending-til-prosessering",
            resultResolver = { 202 == it.second.statusCode }
        ) { httpRequet.awaitStringResponseResult() }

        result.fold(
            { },
            { error ->
                logger.error("Error response = '${error.response.body().asString("text/plain")}' fra '${request.url}'")
                logger.error(error.toString())
                throw IllegalStateException("Feil ved ettersending til prosessering.")
            }
        )
    }

}
