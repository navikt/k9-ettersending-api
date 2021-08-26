package no.nav.k9.general

//Brukes når man logger status i flyten. Formaterer slik at loggen er mer lesbar
internal fun formaterStatuslogging(id: String, melding: String): String {
    return String.format("Ettersending med søknadID: %1$36s %2$1s", id, melding)
}