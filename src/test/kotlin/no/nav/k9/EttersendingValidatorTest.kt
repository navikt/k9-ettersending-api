package no.nav.k9

import no.nav.helse.dusseldorf.ktor.core.Throwblem
import no.nav.k9.EttersendingUtils.hentGyldigEttersending
import no.nav.k9.ettersending.Søknadstype
import no.nav.k9.ettersending.valider
import org.junit.jupiter.api.Assertions
import kotlin.test.Test

class EttersendingValidatorTest{

    @Test
    fun `Gyldig ettersending skal ikke gi feil`(){
        hentGyldigEttersending().valider()
    }

    @Test
    fun `Skal feile dersom harBekreftetOpplysninger er false`(){
        Assertions.assertThrows(Throwblem::class.java){
            hentGyldigEttersending().copy(harBekreftetOpplysninger = false).valider()
        }
    }

    @Test
    fun `Skal feile dersom harForståttRettigheterOgPlikter er false`(){
        Assertions.assertThrows(Throwblem::class.java) {
            hentGyldigEttersending().copy(harForståttRettigheterOgPlikter = false).valider()
        }
    }

    @Test
    fun `Skal feile dersom beskrivelse er tom`(){
        Assertions.assertThrows(Throwblem::class.java) {
            hentGyldigEttersending().copy(beskrivelse = "", søknadstype = Søknadstype.PLEIEPENGER_SYKT_BARN).valider()
        }
    }

    @Test
    fun `Skal feile dersom beskrivelse kun består av tomrom`(){
        Assertions.assertThrows(Throwblem::class.java) {
            hentGyldigEttersending().copy(beskrivelse = "   ", søknadstype = Søknadstype.PLEIEPENGER_SYKT_BARN)
                .valider()
        }
    }

    @Test
    fun `Skal feile dersom søknaden har null vedlegg`() {
        Assertions.assertThrows(Throwblem::class.java) {
            hentGyldigEttersending().copy(vedlegg = listOf()).valider()
        }
    }
}