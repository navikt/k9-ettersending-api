package no.nav.k9.ettersending

import com.fasterxml.jackson.annotation.JsonAlias
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
    @JsonAlias("pleiepenger") //TODO 23.03.2021 - Alias for å støtte gammel versjon fra frontend
    PLEIEPENGER_SYKT_BARN,
    @JsonAlias("omsorgspenger") //TODO 23.03.2021 - Alias for å støtte gammel versjon fra frontend
    OMP_UTV_KS, // Omsorgspenger utvidet rett - kronisk syke eller funksjonshemming.
    OMP_UT_SNF, // Omsorgspenger utbetaling SNF ytelse.
    OMP_UT_ARBEIDSTAKER, // Omsorgspenger utbetaling arbeidstaker ytelse.
    OMP_UTV_MA, // Omsorgspenger utvidet rett - midlertidig alene
    OMP_DELE_DAGER
}