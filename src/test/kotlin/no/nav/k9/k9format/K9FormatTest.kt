package no.nav.k9.k9format

import no.nav.k9.EttersendingUtils
import no.nav.k9.ettersendelse.Ettersendelse
import no.nav.k9.ettersending.Søknadstype
import org.junit.Test
import org.skyscreamer.jsonassert.JSONAssert
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.*

class K9FormatTest {

    @Test
    fun `Gyldig ettersending blir til forventet K9-Format`(){
        val mottatt = ZonedDateTime.of(2020, 1, 2, 3, 4, 5, 6, ZoneId.of("UTC"))
        val søknadId = UUID.randomUUID().toString()

        val ettersending = EttersendingUtils.gyldigEttersending.copy(
            søknadId = søknadId,
            søknadstype = Søknadstype.OMP_UTV_KS
        )
        val k9Format = ettersending.tilK9Format(søker = EttersendingUtils.søker, mottatt = mottatt)

        val forventetK9Format = """
            {
              "søknadId" : "$søknadId",
              "versjon" : "0.0.1",
              "mottattDato" : "2020-01-02T03:04:05.000Z",
              "søker" : {
                "norskIdentitetsnummer" : "02119970078"
              },
              "ytelse" : "OMP_UTV_KS"
            }
        """.trimIndent()

        JSONAssert.assertEquals(forventetK9Format, Ettersendelse.SerDes.serialize(k9Format), true)

    }
}