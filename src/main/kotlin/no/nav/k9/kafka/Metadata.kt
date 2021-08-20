package no.nav.k9.kafka

data class Metadata(
    val version : Int,
    val correlationId : String
)