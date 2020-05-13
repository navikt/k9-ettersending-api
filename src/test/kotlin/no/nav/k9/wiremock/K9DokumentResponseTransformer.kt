package no.nav.k9.wiremock

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.github.tomakehurst.wiremock.common.FileSource
import com.github.tomakehurst.wiremock.extension.Parameters
import com.github.tomakehurst.wiremock.extension.ResponseTransformer
import com.github.tomakehurst.wiremock.http.*
import no.nav.k9.k9DokumentKonfigurert
import no.nav.k9.vedlegg.Vedlegg
import no.nav.k9.vedlegg.VedleggId
import java.util.*

class K9DokumentResponseTransformer() : ResponseTransformer() {

    val storage = mutableMapOf<VedleggId, Vedlegg>()
    val objectMapper = jacksonObjectMapper().k9DokumentKonfigurert()

    override fun getName(): String {
        return "K9DokumentResponseTransformer"
    }

    override fun applyGlobally(): Boolean {
        return false
    }

    override fun transform(
        request: Request?,
        response: Response?,
        files: FileSource?,
        parameters: Parameters?
    ): Response {
        return when {
            request == null -> throw IllegalStateException("request == null")
            request.method == RequestMethod.GET -> {

                val vedleggId = request.getVedleggId()
                return if (storage.containsKey(vedleggId)) {
                    Response.Builder.like(response)
                        .status(200)
                        .headers(HttpHeaders(
                            HttpHeader.httpHeader("Content-Type", "application/json")
                        ))
                        .body(objectMapper.writeValueAsString(storage[vedleggId]))
                        .build()
                } else {
                    Response.Builder.like(response)
                        .status(404)
                        .build()
                }
            }

            request.method == RequestMethod.POST -> {
                val vedlegg = objectMapper.readValue<Vedlegg>(request.bodyAsString)
                val vedleggId = VedleggId(UUID.randomUUID().toString())
                storage[vedleggId] = vedlegg
                Response.Builder.like(response)
                    .status(201)
                    .headers(HttpHeaders(
                        HttpHeader.httpHeader("Location", "http://localhost:8080/v1/dokument/${vedleggId.value}"),
                        HttpHeader.httpHeader("Content-Type", "application/json")
                    ))
                    .body("""
                        {
                            "id" : "${vedleggId.value}"
                        }
                    """.trimIndent())
                    .build()

            }
            request.method == RequestMethod.DELETE -> {
                val vedleggId = request.getVedleggId()
                if (storage.containsKey(vedleggId)) {
                    storage.remove(vedleggId)
                    Response.Builder.like(response)
                        .status(204)
                        .build()
                } else {
                    Response.Builder.like(response)
                        .status(404)
                        .build()
                }
            }
            else -> throw IllegalStateException("Uventet request.")
        }
    }
}

private fun Request.getVedleggId() : VedleggId = VedleggId(url.substringAfterLast("/"))
