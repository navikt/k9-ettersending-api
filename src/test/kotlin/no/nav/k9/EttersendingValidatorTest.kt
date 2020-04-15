package no.nav.k9

import no.nav.helse.dusseldorf.ktor.core.Throwblem
import no.nav.k9.ettersending.Ettersending
import no.nav.k9.ettersending.valider
import org.junit.Test
import java.net.URL

class EttersendingValidatorTest{

    @Test
    fun `Gyldig ettersending skal ikke gi feil`(){
        hentGyldigEttersending().valider()
    }

    @Test(expected = Throwblem::class)
    fun `Skal feile dersom harBekreftetOpplysninger er false`(){
        Ettersending("nb", listOf(), true, false, "Masse forklaringer", "omsorgspenger").valider()
    }

    @Test(expected = Throwblem::class)
    fun `Skal feile dersom harForståttRettigheterOgPlikter er false`(){
        Ettersending("nb", listOf(), false, true, "Masse forklaringer", "omsorgspenger").valider()
    }

    @Test(expected = Throwblem::class)
    fun `Skal feile dersom beskrivelse er tom`(){
        Ettersending("nb", listOf(), true, true, "", "omsorgspenger").valider()
    }

    @Test(expected = Throwblem::class)
    fun `Skal feile dersom beskrivelse kun består av tomrom`(){
        Ettersending("nb", listOf(), true, true, "    ", "omsorgspenger").valider()
    }

    @Test(expected = Throwblem::class)
    fun `Skal feile dersom søknadstype er tom`(){
        Ettersending("nb", listOf(), true, true, "forklaringer", "").valider()
    }

    @Test(expected = Throwblem::class)
    fun `Skal feile dersom søknadstype kun består av tomrom`(){
        Ettersending("nb", listOf(), true, true, "forklaringer", "  ").valider()
    }

    @Test(expected = Throwblem::class)
    fun `Skal feile dersom søknadstype ikke er pleiepenger eller omsorgspenger`(){
        Ettersending("nb", listOf(), true, true, "forklaringer", "koronapenger").valider()
    }



    private fun hentGyldigEttersending() = Ettersending(
        språk = "nb",
        harBekreftetOpplysninger = true,
        harForståttRettigheterOgPlikter = true,
        beskrivelse = "Masse tekst",
        søknadstype = "omsorgspenger",
        vedlegg = listOf(
            URL("http://localhodt:8080/vedlegg/1"),
            URL("http://localhodt:8080/vedlegg/2"),
            URL("http://localhodt:8080/vedlegg/3")
        )
    )
}
