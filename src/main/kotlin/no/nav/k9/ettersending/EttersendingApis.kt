package no.nav.k9.ettersending

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import no.nav.k9.ETTERSEND_URL
import no.nav.k9.VALIDERING_URL
import no.nav.k9.general.auth.IdTokenProvider
import no.nav.k9.general.getCallId
import no.nav.k9.k9format.tilK9Format
import no.nav.k9.soker.Søker
import no.nav.k9.soker.SøkerService
import no.nav.k9.soker.validate
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.ZoneOffset
import java.time.ZonedDateTime

private val logger: Logger = LoggerFactory.getLogger("nav.ettersendingApis")

fun Route.ettersendingApis(
    ettersendingService: EttersendingService,
    søkerService: SøkerService,
    idTokenProvider: IdTokenProvider
) {

    post(ETTERSEND_URL){
        logger.info("Mottatt ettersending.")
        val ettersending = call.receive<Ettersending>()
        logger.trace("Ettersending mappet.")

        val idToken = idTokenProvider.getIdToken(call)
        val callId = call.getCallId()
        val mottatt = ZonedDateTime.now(ZoneOffset.UTC)

        val søker: Søker = søkerService.getSoker(idToken = idToken, callId = callId)
        søker.validate()

        logger.info("Mapper om til K9Format")
        val k9Format = ettersending.tilK9Format(mottatt, søker)

        ettersending.valider()
        logger.trace("Validering OK. Registrerer ettersending.")

        ettersendingService.registrer(
            ettersending = ettersending,
            k9Format = k9Format,
            callId = call.getCallId(),
            idToken = idTokenProvider.getIdToken(call)
        )

        logger.trace("Ettersending registrert.")
        call.respond(HttpStatusCode.Accepted)
    }

    post(VALIDERING_URL) {
        val ettersending = call.receive<Ettersending>()
        logger.trace("Validerer ettersending...")
        ettersending.valider()
        logger.trace("Validering Ok.")
        call.respond(HttpStatusCode.Accepted)
    }
}