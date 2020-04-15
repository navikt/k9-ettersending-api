package no.nav.helse

import com.auth0.jwt.JWT
import com.github.tomakehurst.wiremock.http.Request
import io.ktor.http.HttpHeaders

class TestUtils {
    companion object {
        fun getIdentFromIdToken(request: Request?): String {
            val idToken: String = request!!.getHeader(HttpHeaders.Authorization).substringAfter("Bearer ")
            return JWT.decode(idToken).subject ?: throw IllegalStateException("Token mangler 'sub' claim.")
        }
    }
}