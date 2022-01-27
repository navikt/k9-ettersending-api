package no.nav.k9.soker

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.github.kittinunf.fuel.coroutines.awaitStringResponseResult
import io.ktor.http.*
import no.nav.helse.dusseldorf.ktor.auth.IdToken
import no.nav.helse.dusseldorf.ktor.client.buildURL
import no.nav.helse.dusseldorf.ktor.core.Retry
import no.nav.helse.dusseldorf.ktor.metrics.Operation
import no.nav.helse.dusseldorf.oauth2.client.CachedAccessTokenClient
import no.nav.k9.general.CallId
import no.nav.k9.general.oppslag.K9OppslagGateway
import no.nav.k9.general.oppslag.throwable
import no.nav.k9.k9SelvbetjeningOppslagKonfigurert
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.URI
import java.time.Duration
import java.time.LocalDate

class SøkerGateway(
    baseUrl: URI,
    private val accessTokenClient: CachedAccessTokenClient,
    private val k9SelvbetjeningOppslagTokenxAudience: Set<String>,
) : K9OppslagGateway(baseUrl) {

    private companion object {
        private val logger: Logger = LoggerFactory.getLogger("nav.SokerGateway")
        private const val HENTE_SOKER_OPERATION = "hente-soker"
        private val objectMapper = jacksonObjectMapper().k9SelvbetjeningOppslagKonfigurert()
        private val attributter = Pair("a", listOf("aktør_id", "fornavn", "mellomnavn", "etternavn", "fødselsdato"))
    }

    suspend fun hentSøker(
        idToken: IdToken,
        callId: CallId
    ): SokerOppslagRespons {
        val sokerUrl = Url.buildURL(
            baseUrl = baseUrl,
            pathParts = listOf("meg"),
            queryParameters = mapOf(
                attributter
            )
        ).toString()
        val exchangeToken = IdToken(accessTokenClient.getAccessToken(k9SelvbetjeningOppslagTokenxAudience, idToken.value).token)
        logger.info("Utvekslet token fra {} med token fra {}.", idToken.issuer(), exchangeToken.issuer())

        val httpRequest = generateHttpRequest(exchangeToken, sokerUrl, callId)

        val oppslagRespons = Retry.retry(
            operation = HENTE_SOKER_OPERATION,
            initialDelay = Duration.ofMillis(200),
            factor = 2.0,
            logger = logger
        ) {
            val (request, _, result) = Operation.monitored(
                app = "k9-ettersending-api",
                operation = HENTE_SOKER_OPERATION,
                resultResolver = { 200 == it.second.statusCode }
            ) { httpRequest.awaitStringResponseResult() }

            result.fold(
                { success -> objectMapper.readValue<SokerOppslagRespons>(success) },
                { error ->
                    throw error.throwable(
                        request = request,
                        logger = logger,
                        errorMessage = "Feil ved henting av søkers personinformasjon"
                    )
                }
            )
        }
        return oppslagRespons
    }

    data class SokerOppslagRespons(
        val aktør_id: String,
        val fornavn: String,
        val mellomnavn: String?,
        val etternavn: String,
        val fødselsdato: LocalDate
    )
}
