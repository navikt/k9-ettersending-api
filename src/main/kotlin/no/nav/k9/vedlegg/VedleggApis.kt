package no.nav.k9.vedlegg

import io.ktor.application.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import no.nav.helse.dusseldorf.ktor.auth.idToken
import no.nav.helse.dusseldorf.ktor.core.DefaultProblemDetails
import no.nav.helse.dusseldorf.ktor.core.respondProblemDetails
import no.nav.k9.VEDLEGGID_URL
import no.nav.k9.VEDLEGG_URL
import no.nav.k9.general.getCallId
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*

private val logger: Logger = LoggerFactory.getLogger("nav.vedleggApis")
private const val MAX_VEDLEGG_SIZE = 8 * 1024 * 1024
private val supportedContentTypes = listOf("application/pdf", "image/jpeg", "image/png")

private val hasToBeMultupartTypeProblemDetails = DefaultProblemDetails(title = "multipart-form-required", status = 400, detail = "Requesten må være en 'multipart/form-data' request hvor en 'part' er en fil, har 'name=vedlegg' og har Content-Type header satt.")
private val vedleggNotFoundProblemDetails = DefaultProblemDetails(title = "attachment-not-found", status = 404, detail = "Inget vedlegg funnet med etterspurt ID.")
private val vedleggNotAttachedProblemDetails = DefaultProblemDetails(title = "attachment-not-attached", status = 400, detail = "Fant ingen 'part' som er en fil, har 'name=vedlegg' og har Content-Type header satt.")
private val vedleggTooLargeProblemDetails = DefaultProblemDetails(title = "attachment-too-large", status = 413, detail = "vedlegget var over maks tillatt størrelse på 8MB.")
private val vedleggContentTypeNotSupportedProblemDetails = DefaultProblemDetails(title = "attachment-content-type-not-supported", status = 400, detail = "Vedleggets type må være en av $supportedContentTypes")
private val feilVedSlettingAvVedlegg = DefaultProblemDetails(title = "feil-ved-sletting", status = 500, detail = "Feil ved sletting av vedlegg")

fun Route.vedleggApis(
    vedleggService: VedleggService
) {
    route(VEDLEGG_URL) {
        post{
            logger.info("Lagrer vedlegg")
            if (!call.request.isFormMultipart()) {
                call.respondProblemDetails(hasToBeMultupartTypeProblemDetails)
            } else {
                var eier = call.idToken().getNorskIdentifikasjonsnummer()
                var vedlegg: Vedlegg? = call.receiveMultipart().getVedlegg(DokumentEier(eier))

                if (vedlegg == null) {
                    call.respondProblemDetails(vedleggNotAttachedProblemDetails)
                } else if(!vedlegg.isSupportedContentType()) {
                    call.respondProblemDetails(vedleggContentTypeNotSupportedProblemDetails)
                } else {
                    if (vedlegg.content.size > MAX_VEDLEGG_SIZE) {
                        call.respondProblemDetails(vedleggTooLargeProblemDetails)
                    } else {
                        val vedleggId = vedleggService.lagreVedlegg(
                            vedlegg = vedlegg,
                            idToken = call.idToken(),
                            callId = call.getCallId()
                        )
                        logger.info("$vedleggId")
                        call.respondVedlegg(VedleggId(vedleggId))
                    }
                }
            }
        }

        get(VEDLEGGID_URL){
            val vedleggId = VedleggId(call.parameters["vedleggId"]!!)
            logger.info("Henter vedlegg")
            logger.info("$vedleggId")
            var eier = call.idToken().getNorskIdentifikasjonsnummer()
            if (eier == null) call.respond(HttpStatusCode.Forbidden) else {
                val vedlegg = vedleggService.hentVedlegg(
                    vedleggId = vedleggId.value,
                    idToken = call.idToken(),
                    callId = call.getCallId(),
                    eier = DokumentEier(eier)
                )

                if (vedlegg == null) {
                    call.respondProblemDetails(vedleggNotFoundProblemDetails)
                } else {
                    call.respondBytes(
                        bytes = vedlegg.content,
                        contentType = ContentType.parse(vedlegg.contentType),
                        status = HttpStatusCode.OK
                    )
                }
            }
        }

        delete(VEDLEGGID_URL){
            val vedleggId = VedleggId(call.parameters["vedleggId"]!!)
            logger.info("Sletter vedlegg")
            logger.info("$vedleggId")
            var eier = call.idToken().getNorskIdentifikasjonsnummer()
            if (eier == null) call.respond(HttpStatusCode.Forbidden) else {
                val resultat = vedleggService.slettVedlegg(
                    vedleggId = vedleggId.value,
                    idToken = call.idToken(),
                    callId = call.getCallId(),
                    eier = DokumentEier(eier)
                )

                when (resultat) {
                    true -> call.respond(HttpStatusCode.NoContent)
                    false -> call.respondProblemDetails(feilVedSlettingAvVedlegg)
                }
            }
        }
    }

}


private suspend fun MultiPartData.getVedlegg(eier: DokumentEier) : Vedlegg? {
    for (partData in readAllParts()) {
        if (partData is PartData.FileItem && "vedlegg".equals(partData.name, ignoreCase = true) && partData.contentType != null) {
            val vedlegg = Vedlegg(
                content = partData.streamProvider().readBytes(),
                contentType = partData.contentType.toString(),
                title = partData.originalFileName?: "Ingen tittel tilgjengelig",
                eier = eier
            )
            partData.dispose()
            return vedlegg
        }
        partData.dispose()
    }
    return null
}

private fun Vedlegg.isSupportedContentType(): Boolean = supportedContentTypes.contains(contentType.lowercase(Locale.getDefault()))

private fun ApplicationRequest.isFormMultipart(): Boolean {
    return contentType().withoutParameters().match(ContentType.MultiPart.FormData)
}

private suspend fun ApplicationCall.respondVedlegg(vedleggId: VedleggId) {
    val url = URLBuilder(getBaseUrlFromRequest()).path("vedlegg",vedleggId.value).build().toString()
    response.header(HttpHeaders.Location, url)
    response.header(HttpHeaders.AccessControlExposeHeaders, HttpHeaders.Location)
    respond(HttpStatusCode.Created)
}

private fun ApplicationCall.getBaseUrlFromRequest() : String {
    val host = request.origin.host
    val isLocalhost = "localhost".equals(host, ignoreCase = true)
    val scheme = if (isLocalhost) "http" else "https"
    val port = if (isLocalhost) ":${request.origin.port}" else ""
    return "$scheme://$host$port"
}
