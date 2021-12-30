package no.nav.helse

import com.github.tomakehurst.wiremock.http.Cookie
import no.nav.helse.dusseldorf.testsupport.jws.IDPorten
import no.nav.helse.dusseldorf.testsupport.jws.LoginService

fun getAuthCookie(
    fnr: String,
    level: Int = 4,
    cookieName: String = "localhost-idtoken",
    expiry: Long? = null) : Cookie {

    val overridingClaims : Map<String, Any> = if (expiry == null) emptyMap() else mapOf(
        "exp" to expiry
    )

    val jwt = LoginService.V1_0.generateJwt(fnr = fnr, level = level, overridingClaims = overridingClaims)
    return Cookie(listOf(String.format("%s=%s", cookieName, jwt), "Path=/", "Domain=localhost"))
}

fun getIdportenAuthCookie(
    fnr: String,
    level: Int = 4,
    cookieName: String = "localhost-idtoken",
    expiry: Long? = null) : Cookie {

    val overridingClaims : Map<String, Any> = if (expiry == null) emptyMap() else mapOf(
        "exp" to expiry
    )

    val jwt = IDPorten.generateIdToken(fnr = fnr, level = level, overridingClaims = overridingClaims)
    return Cookie(listOf(String.format("%s=%s", cookieName, jwt), "Path=/", "Domain=localhost"))
}
