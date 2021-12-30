package no.nav.helse

import com.github.tomakehurst.wiremock.http.Request
import io.ktor.http.*
import no.nav.helse.dusseldorf.ktor.auth.IdToken

class TestUtils {
    companion object {
        fun getIdentFromIdToken(request: Request?): String {
            val idToken = IdToken(request!!.getHeader(HttpHeaders.Authorization).substringAfter("Bearer "))
            return idToken.getNorskIdentifikasjonsnummer()
        }
    }
}
