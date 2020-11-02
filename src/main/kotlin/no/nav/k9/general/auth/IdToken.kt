package no.nav.k9.general.auth

import com.auth0.jwt.JWT
import io.ktor.http.auth.HttpAuthHeader

data class IdToken(val value: String) {
    private val jwt = try {
        JWT.decode(value)
    } catch (cause: Throwable) {
        throw IdTokenInvalidFormatException(this, cause)
    }

    internal fun somHttpAuthHeader() : HttpAuthHeader = HttpAuthHeader.Single("Bearer", value)

    internal fun getId() : String? = jwt.id
    internal fun getSubject() : String? = jwt.subject

}
