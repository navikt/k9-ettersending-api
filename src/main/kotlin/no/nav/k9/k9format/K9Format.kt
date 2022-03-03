package no.nav.k9.k9format

import no.nav.k9.ettersendelse.Ettersendelse
import no.nav.k9.ettersendelse.Ytelse
import no.nav.k9.ettersending.Ettersending
import no.nav.k9.ettersending.Søknadstype
import no.nav.k9.soker.Søker
import no.nav.k9.søknad.felles.type.NorskIdentitetsnummer
import no.nav.k9.søknad.felles.type.SøknadId
import java.time.ZonedDateTime
import no.nav.k9.søknad.felles.personopplysninger.Søker as K9Søker

fun Ettersending.tilK9Format(mottatt: ZonedDateTime, søker: Søker) : Ettersendelse {
    val builder = Ettersendelse.builder()
    builder
        .søknadId(SøknadId(søknadId))
        .mottattDato(mottatt)
        .søker(K9Søker(NorskIdentitetsnummer.of(søker.fødselsnummer)))
        .ytelse(søknadstype.tilK9Ytelse())

    return builder.build()
}

private fun Søknadstype.tilK9Ytelse(): Ytelse {
    return when(this){
        Søknadstype.OMP_UTV_KS -> Ytelse.OMP_UTV_KS //TODO 26.04.2021 - Hvis dette skal inn i nytt K9-format før dele-dager blir tatt ut til årskvantum burde det lages egen Ytelse
        Søknadstype.OMP_UTV_MA -> Ytelse.OMP_UTV_MA
        Søknadstype.PLEIEPENGER_SYKT_BARN -> Ytelse.PLEIEPENGER_SYKT_BARN
        Søknadstype.OMP_UT_SNF, Søknadstype.OMP_UT_ARBEIDSTAKER -> Ytelse.OMP_UT
        Søknadstype.OMP_DELE_DAGER -> Ytelse.OMP_DELE_DAGER
        Søknadstype.PLEIEPENGER_LIVETS_SLUTTFASE -> Ytelse.PLEIEPENGER_LIVETS_SLUTTFASE
    }
}