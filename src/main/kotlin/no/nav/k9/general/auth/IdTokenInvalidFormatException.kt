package no.nav.k9.general.auth

class IdTokenInvalidFormatException(idToken: IdToken, cause: Throwable? = null) : RuntimeException("$idToken er på ugyldig format.", cause)
