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

    private val logger = LoggerFactory.getLogger(IdTokenProvider::class.java)

    fun getIdToken(call: ApplicationCall) : IdToken {
        if(cookieName != null) {
            val cookie = call.request.cookies[cookieName]
            if(cookie != null) return IdToken(cookie)
        }

        // Betyr at vi ikke fant noe token i cookie, eller at vi ikke skal støtte cookie.
        // Da skal token ligge som header
        val jwt = call.request.parseAuthorizationHeader()?.render()
        if(jwt != null) {
            //Betyr at det fantes en auth header
            return IdToken(jwt.substringAfter("Bearer "))
        }

        throw NoTokenSetException(cookieName)
    }
}

class NoTokenSetException(cookieName : String?) : RuntimeException("Ingen cookie med navnet '$cookieName' eller auth header satt.")