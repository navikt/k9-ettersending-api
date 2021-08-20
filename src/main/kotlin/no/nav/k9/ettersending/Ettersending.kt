package no.nav.k9.ettersending

import io.ktor.http.*
import no.nav.helse.dusseldorf.ktor.client.buildURL
import no.nav.k9.ettersendelse.Ettersendelse
import no.nav.k9.soker.Søker
import java.net.URI
import java.net.URL
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.*

data class Ettersending(
    val søknadId: String = UUID.randomUUID().toString(),
    val språk: String,
    val vedlegg: List<URL>,
    val harForståttRettigheterOgPlikter: Boolean,
    val harBekreftetOpplysninger: Boolean,
    val beskrivelse: String?,
    val søknadstype: Søknadstype
) {
    fun tilKomplettEttersending(k9Format: Ettersendelse, søker: Søker, k9MellomlagringIngress: URI) = KomplettEttersending(
        søker = søker,
        språk = språk,
        mottatt = ZonedDateTime.now(ZoneOffset.UTC),
        vedlegg = vedlegg.tilK9MellomLagringUrl(k9MellomlagringIngress),
        søknadId = søknadId,
        harForståttRettigheterOgPlikter = harForståttRettigheterOgPlikter,
        harBekreftetOpplysninger = harBekreftetOpplysninger,
        beskrivelse = beskrivelse,
        søknadstype = søknadstype,
        k9Format = k9Format
    )

    // TODO: 20/08/2021 Lage test som verifiserer at omgjøringen blir til riktig url med ingress og ikke service discovery
    private fun List<URL>.tilK9MellomLagringUrl(baseUrl: URI): List<URL> = map {
        val idFraUrl = it.path.substringAfterLast("/")
        Url.buildURL(
            baseUrl = baseUrl,
            pathParts = listOf(idFraUrl)
        ).toURL()
    }
}

enum class Søknadstype {
    PLEIEPENGER_SYKT_BARN,
    OMP_UTV_KS, // Omsorgspenger utvidet rett - kronisk syke eller funksjonshemming.
    OMP_UT_SNF, // Omsorgspenger utbetaling SNF ytelse.
    OMP_UT_ARBEIDSTAKER, // Omsorgspenger utbetaling arbeidstaker ytelse.
    OMP_UTV_MA, // Omsorgspenger utvidet rett - midlertidig alene
    OMP_DELE_DAGER
}