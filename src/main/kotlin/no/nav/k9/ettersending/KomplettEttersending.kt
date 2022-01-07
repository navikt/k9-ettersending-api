package no.nav.k9.ettersending

import no.nav.k9.ettersendelse.Ettersendelse
import no.nav.k9.soker.Søker
import java.time.ZonedDateTime

data class KomplettEttersending (
    val søknadId: String,
    val søker: Søker,
    val språk: String,
    val mottatt: ZonedDateTime,
    val vedleggId: List<String>,
    val harForståttRettigheterOgPlikter: Boolean,
    val harBekreftetOpplysninger: Boolean,
    val beskrivelse: String?,
    val søknadstype: Søknadstype,
    val titler: List<String>,
    val k9Format: Ettersendelse
)