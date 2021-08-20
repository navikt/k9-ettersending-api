package no.nav.k9

import no.nav.helse.dusseldorf.ktor.core.Throwblem
import no.nav.k9.EttersendingUtils.hentGyldigEttersending
import no.nav.k9.ettersending.Søknadstype
import no.nav.k9.ettersending.valider
import org.junit.Test

class EttersendingValidatorTest{

    @Test
    fun `Gyldig ettersending skal ikke gi feil`(){
        hentGyldigEttersending().valider()
    }

    @Test(expected = Throwblem::class)
    fun `Skal feile dersom harBekreftetOpplysninger er false`(){
        hentGyldigEttersending().copy(harBekreftetOpplysninger = false).valider()
    }

    @Test(expected = Throwblem::class)
    fun `Skal feile dersom harForståttRettigheterOgPlikter er false`(){
        hentGyldigEttersending().copy(harForståttRettigheterOgPlikter= false).valider()
    }

    @Test(expected = Throwblem::class)
    fun `Skal feile dersom beskrivelse er tom`(){
        hentGyldigEttersending().copy(beskrivelse = "", søknadstype = Søknadstype.PLEIEPENGER_SYKT_BARN).valider()
    }

    @Test(expected = Throwblem::class)
    fun `Skal feile dersom beskrivelse kun består av tomrom`(){
        hentGyldigEttersending().copy(beskrivelse = "   ", søknadstype = Søknadstype.PLEIEPENGER_SYKT_BARN).valider()
    }

    @Test(expected = Throwblem::class)
    fun `Skal feile dersom søknaden har null vedlegg`(){
        hentGyldigEttersending().copy(vedlegg = listOf()).valider()
    }
}