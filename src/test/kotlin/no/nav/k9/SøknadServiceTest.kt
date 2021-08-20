package no.nav.k9

import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import kotlinx.coroutines.runBlocking
import no.nav.helse.dusseldorf.testsupport.jws.Azure
import no.nav.k9.EttersendingUtils.hentGyldigEttersending
import no.nav.k9.ettersending.EttersendingService
import no.nav.k9.ettersending.MeldingRegistreringFeiletException
import no.nav.k9.general.CallId
import no.nav.k9.general.auth.IdToken
import no.nav.k9.kafka.KafkaProducer
import no.nav.k9.kafka.Metadata
import no.nav.k9.soker.Søker
import no.nav.k9.soker.SøkerService
import no.nav.k9.vedlegg.DokumentEier
import no.nav.k9.vedlegg.Vedlegg
import no.nav.k9.vedlegg.VedleggService
import org.junit.Before
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.assertThrows
import java.net.URI
import java.time.LocalDate
import kotlin.test.Test

internal class SøknadServiceTest{
    @RelaxedMockK
    lateinit var kafkaProducer: KafkaProducer

    @RelaxedMockK
    lateinit var søkerService: SøkerService

    @RelaxedMockK
    lateinit var vedleggService: VedleggService

    lateinit var ettersendingService: EttersendingService

    @Before
    internal fun setUp() {
        MockKAnnotations.init(this)
        ettersendingService = EttersendingService(
            søkerService = søkerService,
            kafkaProducer = kafkaProducer,
            k9MellomLagringIngress = URI("http://localhost:8080/v1/dokument"),
            vedleggService = vedleggService
        )
        assertNotNull(kafkaProducer)
        assertNotNull(ettersendingService)
    }

    @Test
    internal fun `Tester at den sletter persistert vedlegg dersom kafka feiler`() {
        assertThrows<MeldingRegistreringFeiletException> {
            runBlocking {
                coEvery {søkerService.getSoker(any(), any()) } returns Søker(
                    aktørId = "123",
                    fødselsdato = LocalDate.parse("2000-01-01"),
                    fødselsnummer = "290990123456"
                )

                coEvery {vedleggService.hentVedlegg(vedleggUrls = any(), any(), any(), any()) } returns listOf(Vedlegg("bytearray".toByteArray(), "vedlegg", "vedlegg", DokumentEier("290990123456")))

                every { kafkaProducer.produserKafkaMelding(any(), any()) } throws Exception("Mocket feil ved kafkaProducer")

                ettersendingService.registrer(
                    ettersending = hentGyldigEttersending(),
                    metadata = Metadata(
                        version = 1,
                        correlationId = "123"
                    ),
                    idToken = IdToken(Azure.V2_0.generateJwt(clientId = "ikke-authorized-client", audience = "omsorgsdager-melding-api")),
                    callId = CallId("abc")
                )
            }
        }

        coVerify(exactly = 1) { vedleggService.slettPersistertVedlegg(any(), any(), any()) }
    }
}