package no.nav.k9.general.auth

import io.ktor.application.*
import io.ktor.auth.*
import no.nav.helse.dusseldorf.ktor.auth.IdToken
import no.nav.helse.dusseldorf.oauth2.client.CachedAccessTokenClient
import org.slf4j.LoggerFactory

object TokenResolver {
    private val logger = LoggerFactory.getLogger(TokenResolver::class.java)
    fun resolveToken(
        accessTokenClient: CachedAccessTokenClient,
        idToken: IdToken,
        scope: Set<String>
    ): IdToken {
        val exchangeToken = IdToken(
            accessTokenClient.getAccessToken(
                scopes = scope,
                onBehalfOf = idToken.value
            ).token
        )
        logger.info("Utvekslet token fra {} med token fra {}.", idToken.issuer(), exchangeToken.issuer())
        return exchangeToken
    }
}

class IdTokenProvider(
    private val cookieName : String? = null
) {
    fun getIdToken(call: ApplicationCall) : IdToken {
        if(cookieName != null) {
            val cookie = call.request.cookies[cookieName]
            if(cookie != null) return IdToken(cookie)
        }

        val jwt = call.request.parseAuthorizationHeader()?.render()
        if(jwt != null) return IdToken(jwt.substringAfter("Bearer "))

        throw NoTokenSetException()
    }
}

class NoTokenSetException() : RuntimeException("Fant ikke token som cookie eller auth header.")