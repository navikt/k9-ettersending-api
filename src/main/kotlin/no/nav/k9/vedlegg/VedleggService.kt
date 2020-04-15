package no.nav.k9.vedlegg

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import no.nav.k9.general.CallId
import no.nav.k9.general.auth.IdToken
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.URL

private val logger: Logger = LoggerFactory.getLogger("nav.VedleggService")

class VedleggService(
    private val k9DokumentGateway: K9DokumentGateway
) {
    suspend fun lagreVedlegg(
        vedlegg: Vedlegg,
        idToken: IdToken,
        callId: CallId
    ) : VedleggId {

        return k9DokumentGateway.lagreVedlegg(
            vedlegg = vedlegg,
            idToken = idToken,
            callId = callId
        )

    }

    suspend fun hentVedlegg(
        vedleggId: VedleggId,
        idToken: IdToken,
        callId: CallId
    ) : Vedlegg? {

        return k9DokumentGateway.hentVedlegg(
            vedleggId = vedleggId,
            idToken = idToken,
            callId = callId
        )
    }

    suspend fun hentVedlegg(
        vedleggUrls: List<URL>,
        idToken: IdToken,
        callId: CallId
    ) : List<Vedlegg> {
        val vedlegg = coroutineScope {
            val futures = mutableListOf<Deferred<Vedlegg?>>()
            vedleggUrls.forEach {
                futures.add(async { hentVedlegg(
                    vedleggId = vedleggIdFromUrl(it),
                    idToken = idToken,
                    callId = callId
                )})

            }
            futures.awaitAll().filter { it != null }
        }
        return vedlegg.requireNoNulls()
    }

    suspend fun slettVedlegg(
        vedleggId: VedleggId,
        idToken: IdToken,
        callId: CallId
    ) {
        k9DokumentGateway.slettVedlegg(
            vedleggId = vedleggId,
            idToken = idToken,
            callId = callId
        )
    }

    suspend fun slettVedlegg(
        vedleggUrls: List<URL>,
        idToken: IdToken,
        callId: CallId
    ) {
        coroutineScope {
            val futures = mutableListOf<Deferred<Unit>>()
            vedleggUrls.forEach {
                futures.add(async { slettVedlegg(
                    vedleggId = vedleggIdFromUrl(it),
                    idToken = idToken,
                    callId = callId
                )})

            }
            futures.awaitAll()
        }
    }

    private fun vedleggIdFromUrl(url: URL) : VedleggId {
        return VedleggId(url.path.substringAfterLast("/"))
    }
}
