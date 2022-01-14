package no.nav.k9.ettersending

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import no.nav.helse.dusseldorf.ktor.auth.idToken
import no.nav.k9.ETTERSEND_URL
import no.nav.k9.VALIDERING_URL
import no.nav.k9.general.formaterStatuslogging
import no.nav.k9.general.getCallId
import no.nav.k9.general.getMetadata
import org.slf4j.Logger
import org.slf4j.LoggerFactory

private val logger: Logger = LoggerFactory.getLogger("nav.ettersendingApis")

fun Route.ettersendingApis(
    ettersendingService: EttersendingService
) {

    post(ETTERSEND_URL){
        val ettersending = call.receive<Ettersending>()
        logger.info(formaterStatuslogging(ettersending.søknadId, "av typen ${ettersending.søknadstype} mottatt"))

        ettersendingService.registrer(
            ettersending = ettersending,
            idToken = call.idToken(),
            callId = call.getCallId(),
            metadata = call.getMetadata()
        )

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
