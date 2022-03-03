package no.nav.k9.ettersending

import no.nav.k9.ettersendelse.Ettersendelse
import no.nav.k9.soker.Søker
import no.nav.k9.vedlegg.vedleggId
import java.net.URL
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
    fun tilKomplettEttersending(
        k9Format: Ettersendelse,
        søker: Søker,
        mottatt: ZonedDateTime,
        titler: List<String>
    ) = KomplettEttersending(
        søker = søker,
        språk = språk,
        mottatt = mottatt,
        vedleggId = vedlegg.map { it.vedleggId() },
        søknadId = søknadId,
        harForståttRettigheterOgPlikter = harForståttRettigheterOgPlikter,
        harBekreftetOpplysninger = harBekreftetOpplysninger,
        beskrivelse = beskrivelse,
        søknadstype = søknadstype,
        titler = titler,
        k9Format = k9Format
    )
}

enum class Søknadstype {
    PLEIEPENGER_SYKT_BARN,
    PLEIEPENGER_LIVETS_SLUTTFASE,
    OMP_UTV_KS, // Omsorgspenger utvidet rett - kronisk syke eller funksjonshemming.
    OMP_UT_SNF, // Omsorgspenger utbetaling SNF ytelse.
    OMP_UT_ARBEIDSTAKER, // Omsorgspenger utbetaling arbeidstaker ytelse.
    OMP_UTV_MA, // Omsorgspenger utvidet rett - midlertidig alene
    OMP_DELE_DAGER
}