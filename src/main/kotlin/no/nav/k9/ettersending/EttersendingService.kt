package no.nav.k9.ettersending

import no.nav.k9.K9EttersendingMottakGateway
import no.nav.k9.ettersendelse.Ettersendelse
import no.nav.k9.general.CallId
import no.nav.k9.general.auth.IdToken
import no.nav.k9.soker.Søker
import no.nav.k9.soker.SøkerService
import no.nav.k9.soker.validate
import no.nav.k9.vedlegg.DokumentEier
import no.nav.k9.vedlegg.VedleggService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.ZoneOffset
import java.time.ZonedDateTime

class EttersendingService(
    private val k9EttersendingMottakGateway: K9EttersendingMottakGateway,
    private val søkerService: SøkerService,
    private val vedleggService: VedleggService
){
    private companion object {
        private val logger: Logger = LoggerFactory.getLogger(EttersendingService::class.java)
    }

    suspend fun registrer(
        ettersending: Ettersending,
        k9Format: Ettersendelse,
        idToken: IdToken,
        callId: CallId
    ){
        logger.info("Registrerer ettersending av typen ${ettersending.søknadstype.name}. Henter søker")
        val søker: Søker = søkerService.getSoker(idToken = idToken, callId = callId)

        logger.trace("Søker hentet. Validerer søker.")
        søker.validate()
        logger.trace("Søker Validert.")

        logger.info("Henter ${ettersending.vedlegg.size} vedlegg.")
        val vedlegg = vedleggService.hentVedlegg(
            idToken = idToken,
            vedleggUrls = ettersending.vedlegg,
            callId = callId,
            eier = DokumentEier(søker.fødselsnummer)
        )

        logger.trace("Vedlegg hentet. Validerer vedlegg.")
        vedlegg.validerVedlegg(ettersending.vedlegg)
        logger.info("Vedlegg validert")

        logger.info("Legger ettersending til prosessering")

        val komplettEttersending = KomplettEttersending(
            søker = søker,
            språk = ettersending.språk,
            mottatt = ZonedDateTime.now(ZoneOffset.UTC),
            vedlegg = vedlegg,
            søknadId = ettersending.søknadId,
            harForståttRettigheterOgPlikter = ettersending.harForståttRettigheterOgPlikter,
            harBekreftetOpplysninger = ettersending.harBekreftetOpplysninger,
            beskrivelse = ettersending.beskrivelse,
            søknadstype = ettersending.søknadstype,
            k9Format = k9Format
        )

        k9EttersendingMottakGateway.leggTilProsesseringEttersending(
            ettersending = komplettEttersending,
            callId = callId
        )

        logger.trace("Ettersending lagt til mottak.")
    }
}
