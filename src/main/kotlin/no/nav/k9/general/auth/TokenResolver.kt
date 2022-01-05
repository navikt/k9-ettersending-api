package no.nav.k9.general.auth

import no.nav.helse.dusseldorf.ktor.auth.IdToken
import no.nav.helse.dusseldorf.oauth2.client.CachedAccessTokenClient
import org.slf4j.LoggerFactory

object TokenResolver {
    private val logger = LoggerFactory.getLogger(TokenResolver::class.java)
    fun resolveToken(
        accessTokenClient: CachedAccessTokenClient,
        idToken: IdToken,
        scope: Set<String>
    ): IdToken = when {
        idToken.issuerIsLoginservice() -> idToken

        else -> {
            val exchangeToken = IdToken(
                accessTokenClient.getAccessToken(
                    scopes = scope,
                    onBehalfOf = idToken.value
                ).token
            )
            logger.info("Utvekslet token fra {} med token fra {}.", idToken.issuer(), exchangeToken.issuer())
            exchangeToken
        }
    }
}
