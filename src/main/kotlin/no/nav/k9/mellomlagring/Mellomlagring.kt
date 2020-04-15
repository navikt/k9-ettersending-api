package no.nav.k9.mellomlagring

import io.ktor.application.call
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.locations.*
import io.ktor.request.receive
import io.ktor.response.respond
import io.ktor.response.respondText
import io.ktor.routing.Route
import no.nav.k9.general.auth.IdTokenProvider
import org.slf4j.Logger
import org.slf4j.LoggerFactory

private val logger: Logger = LoggerFactory.getLogger("nav.soknadApis")

@KtorExperimentalLocationsAPI
fun Route.mellomlagringApis(
    mellomlagringService: MellomlagringService,
    idTokenProvider: IdTokenProvider
) {

    @Location("/mellomlagring")
    class mellomlagring

    post { _: mellomlagring ->
        val midlertidigSøknad = call.receive<String>()
        val idToken = idTokenProvider.getIdToken(call)
        mellomlagringService.setMellomlagring(idToken.getSubject()!!, midlertidigSøknad)
        call.respond(HttpStatusCode.NoContent)
    }

    get { _: mellomlagring ->
        val idToken = idTokenProvider.getIdToken(call)
        val mellomlagring = mellomlagringService.getMellomlagring(idToken.getSubject()!!)
        if (mellomlagring != null) {
            call.respondText(
                contentType = ContentType.Application.Json,
                text = mellomlagring,
                status = HttpStatusCode.OK
            )
        } else {
            call.respondText(
                contentType = ContentType.Application.Json,
                text = "{}",
                status = HttpStatusCode.OK
            )
        }
    }

    delete { _: mellomlagring ->
        val idToken = idTokenProvider.getIdToken(call)
        mellomlagringService.deleteMellomlagring(idToken.getSubject()!!)
        call.respond(HttpStatusCode.Accepted)
    }
}
