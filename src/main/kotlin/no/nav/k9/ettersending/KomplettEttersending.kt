package no.nav.k9.ettersending

import no.nav.k9.ettersendelse.Ettersendelse
import no.nav.k9.soker.Søker
import no.nav.k9.vedlegg.Vedlegg
import java.time.ZonedDateTime

data class KomplettEttersending (
    val søknadId: String,
    val søker: Søker,
    val språk: String,
    val mottatt: ZonedDateTime,
    val vedlegg: List<Vedlegg>,
    val harForståttRettigheterOgPlikter: Boolean,
    val harBekreftetOpplysninger: Boolean,
    val beskrivelse: String,
    val søknadstype: Søknadstype,
    val k9Format: Ettersendelse
)
