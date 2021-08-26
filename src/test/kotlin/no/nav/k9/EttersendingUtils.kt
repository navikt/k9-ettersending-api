package no.nav.k9

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.k9.ettersending.Ettersending
import no.nav.k9.ettersending.Søknadstype
import no.nav.k9.soker.Søker
import java.net.URL
import java.time.LocalDate
import java.util.*

internal object EttersendingUtils {
    internal val objectMapper = jacksonObjectMapper().k9EttersendingKonfiguert()

    val søker = Søker(
        aktørId = "12345",
        fødselsdato = LocalDate.parse("2000-01-01"),
        fødselsnummer = "02119970078",
        fornavn = "Ole",
        mellomnavn = "Dole",
        etternavn = "Doffen"
    )

    fun hentGyldigEttersending() = Ettersending(
        søknadId  = UUID.randomUUID().toString(),
        språk = "nb",
        søknadstype = Søknadstype.OMP_UTV_KS,
        beskrivelse = "Masse tekst",
        vedlegg = listOf(
            URL("http://localhost:8080/vedlegg/1")
        ),
        harForståttRettigheterOgPlikter = true,
        harBekreftetOpplysninger = true
    )

        fun gyldigEttersendingSomJson(
            vedleggUrl1: String,
            vedleggUrl2: String,
            søknadstype: String = "PLEIEPENGER_SYKT_BARN"
        ): String {
            //language=JSON

            return """
                {
                  "språk": "nb",
                  "søknadId" : "${UUID.randomUUID().toString()}",
                  "vedlegg": [
                    "$vedleggUrl1",
                    "$vedleggUrl2"
                  ],
                  "harForståttRettigheterOgPlikter": true,
                  "harBekreftetOpplysninger": true,
                  "beskrivelse": "Masse tekst",
                  "søknadstype": "$søknadstype"
                }
            """.trimIndent()
        }
}

internal fun Ettersending.somJson() = EttersendingUtils.objectMapper.writeValueAsString(this)
fun Any.somJson() = objectMapper.writeValueAsString(this)