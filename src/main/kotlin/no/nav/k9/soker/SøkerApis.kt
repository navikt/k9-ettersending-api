package no.nav.k9.soker

import io.ktor.application.*
import io.ktor.response.*
import io.ktor.routing.*
import no.nav.k9.SØKER_URL
import no.nav.k9.general.auth.IdTokenProvider
import no.nav.k9.general.getCallId

fun Route.søkerApis(
    søkerService: SøkerService,
    idTokenProvider: IdTokenProvider
) {

    get(SØKER_URL) {
        call.respond(
            søkerService.getSoker(
                idToken = idTokenProvider.getIdToken(call),
                callId = call.getCallId()
            )
        )
    }
}

