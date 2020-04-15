package no.nav.k9.general.auth

import io.ktor.application.ApplicationCall

class IdTokenProvider(
    private val cookieName : String
) {
    fun getIdToken(call: ApplicationCall) : IdToken {
        val cookie = call.request.cookies[cookieName] ?: throw CookieNotSetException(cookieName)
        return IdToken(value = cookie)
    }
}
