package no.nav.helse

import com.github.tomakehurst.wiremock.http.Cookie
import no.nav.helse.dusseldorf.testsupport.jws.IDPorten
import no.nav.helse.dusseldorf.testsupport.jws.LoginService
import no.nav.helse.dusseldorf.testsupport.jws.Tokendings
import no.nav.helse.dusseldorf.testsupport.jws.Tokendings.generateAssertionJwt

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

fun getTokenDingsToken(
    fnr: String,
    level: Int = 4,
    expiry: Long? = null
): String {

    val overridingClaims: Map<String, Any> = if (expiry == null) emptyMap() else mapOf(
        "exp" to expiry,
        "acr" to "Level4"
    )

    return Tokendings.generateJwt(
        overridingClaims = overridingClaims,
        urlDecodedBody = Tokendings.generateUrlDecodedBody(
            grantType = "urn:ietf:params:oauth:grant-type:token-exchange",
            clientAssertionType = "urn:ietf:params:oauth:client-assertion-type:jwt-bearer",
            clientId = "dev-gcp:dusseldorf:k9-ettersending",
            clientAssertion = generateAssertionJwt(
                mapOf(
                    "iss" to Tokendings.getIssuer(),
                    "client_id" to "dev-gcp:dusseldorf:k9-ettersending",
                    "sub" to "dev-gcp:dusseldorf:k9-ettersending",
                    "aud" to Tokendings.getAudience()
                )
            ),
            subjectTokenType = "urn:ietf:params:oauth:token-type:jwt",
            subjectToken = IDPorten.generateIdToken(fnr = fnr, level = level, overridingClaims = overridingClaims)
        )
    )
}
