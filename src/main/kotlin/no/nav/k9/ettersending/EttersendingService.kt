package no.nav.k9.ettersending

import no.nav.k9.general.CallId
import no.nav.k9.general.auth.IdToken
import no.nav.k9.general.formaterStatuslogging
import no.nav.k9.k9format.tilK9Format
import no.nav.k9.kafka.KafkaProducer
import no.nav.k9.kafka.Metadata
import no.nav.k9.soker.Søker
import no.nav.k9.soker.SøkerService
import no.nav.k9.soker.validate
import no.nav.k9.vedlegg.DokumentEier
import no.nav.k9.vedlegg.VedleggService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.URL
import java.time.ZoneOffset
import java.time.ZonedDateTime

class EttersendingService(
    private val søkerService: SøkerService,
    private val vedleggService: VedleggService,
    private val k9MellomLagringIngress: URI,
    private val kafkaProducer: KafkaProducer
) {
    private val logger: Logger = LoggerFactory.getLogger(EttersendingService::class.java)

    suspend fun registrer(
        ettersending: Ettersending,
        idToken: IdToken,
        callId: CallId,
        metadata: Metadata
    ) {
        logger.info(formaterStatuslogging(ettersending.søknadId, "registreres"))

        ettersending.valider()

        val søker: Søker = søkerService.getSoker(idToken = idToken, callId = callId)
        søker.validate()

        val dokumentEier = DokumentEier(søker.fødselsnummer)
        håndterVedlegg(idToken, callId, dokumentEier, ettersending.vedlegg)

        val k9Format = ettersending.tilK9Format(ZonedDateTime.now(ZoneOffset.UTC), søker)
        val komplettEttersending = ettersending.tilKomplettEttersending(k9Format, søker, k9MellomLagringIngress)

        try {
            kafkaProducer.produserKafkaMelding(komplettEttersending, metadata)
        } catch (exception: Exception) {
            logger.info("Feilet ved å legge melding på Kafka. Sletter persisterte vedlegg")
            vedleggService.slettPersistertVedlegg(ettersending.vedlegg, callId, dokumentEier)
        }
    }

    private suspend fun håndterVedlegg(
        idToken: IdToken,
        callId: CallId,
        dokumentEier: DokumentEier,
        vedlegg: List<URL>
    ) {

        logger.info("Validerer ${vedlegg.size} vedlegg")
        vedleggService.hentVedlegg(vedlegg, idToken, callId, dokumentEier).validerVedlegg(vedlegg)

        logger.info("Persisterer vedlegg")
        vedleggService.persisterVedlegg(vedlegg, callId, dokumentEier)
    }
}