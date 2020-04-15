package no.nav.k9.general.auth

data class ApiGatewayApiKey(val value : String, val headerKey : String = "x-nav-apiKey")
