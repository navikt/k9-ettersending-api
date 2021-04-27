package no.nav.k9.ettersending

import java.net.URL
import java.util.*

data class Ettersending(
    val søknadId: String = UUID.randomUUID().toString(),
    val språk: String,
    val vedlegg: List<URL>,
    val harForståttRettigheterOgPlikter: Boolean,
    val harBekreftetOpplysninger: Boolean,
    val beskrivelse: String?,
    val søknadstype: Søknadstype
)

enum class Søknadstype{
    PLEIEPENGER_SYKT_BARN,
    OMP_UTV_KS, // Omsorgspenger utvidet rett - kronisk syke eller funksjonshemming.
    OMP_UT_SNF, // Omsorgspenger utbetaling SNF ytelse.
    OMP_UT_ARBEIDSTAKER, // Omsorgspenger utbetaling arbeidstaker ytelse.
    OMP_UTV_MA, // Omsorgspenger utvidet rett - midlertidig alene
    OMP_DELE_DAGER
}