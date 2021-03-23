package no.nav.k9

import no.nav.helse.dusseldorf.ktor.core.Throwblem
import no.nav.k9.ettersending.valider
import org.junit.Test

class EttersendingValidatorTest{

    @Test
    fun `Gyldig ettersending skal ikke gi feil`(){
        EttersendingUtils.gyldigEttersending.valider()
    }

    @Test(expected = Throwblem::class)
    fun `Skal feile dersom harBekreftetOpplysninger er false`(){
        EttersendingUtils.gyldigEttersending.copy(harBekreftetOpplysninger = false).valider()
    }

    @Test(expected = Throwblem::class)
    fun `Skal feile dersom harForståttRettigheterOgPlikter er false`(){
        EttersendingUtils.gyldigEttersending.copy(harForståttRettigheterOgPlikter= false).valider()
    }

    @Test(expected = Throwblem::class)
    fun `Skal feile dersom beskrivelse er tom`(){
        EttersendingUtils.gyldigEttersending.copy(beskrivelse = "").valider()
    }

    @Test(expected = Throwblem::class)
    fun `Skal feile dersom beskrivelse kun består av tomrom`(){
        EttersendingUtils.gyldigEttersending.copy(beskrivelse = "   ").valider()
    }
}
