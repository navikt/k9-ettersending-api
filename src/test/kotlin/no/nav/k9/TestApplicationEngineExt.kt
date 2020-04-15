package no.nav.k9

import com.github.tomakehurst.wiremock.http.Cookie
import io.ktor.http.*
import io.ktor.http.content.PartData
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.setBody
import io.ktor.utils.io.streams.asInput
import no.nav.helse.dusseldorf.ktor.core.fromResources
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

fun TestApplicationEngine.handleRequestUploadImage(
    cookie: Cookie,
    vedlegg: ByteArray = "vedlegg/iPhone_6.jpg".fromResources().readBytes(),
    fileName: String = "iPhone_6.jpg",
    contentType: String = "image/jpeg",
    expectedCode: HttpStatusCode = HttpStatusCode.Created
): String {
    val boundary = "***vedlegg***"

    handleRequest(HttpMethod.Post, "/vedlegg") {
        addHeader("Cookie", cookie.toString())
        addHeader(
            HttpHeaders.ContentType,
            ContentType.MultiPart.FormData.withParameter("boundary", boundary).toString()
        )
        setBody(
            boundary, listOf(
                PartData.FileItem(
                    { vedlegg.inputStream().asInput() }, {},
                    headersOf(
                        Pair(
                            HttpHeaders.ContentType,
                            listOf(contentType)
                        ),
                        Pair(
                            HttpHeaders.ContentDisposition,
                            listOf(
                                ContentDisposition.File
                                    .withParameter(ContentDisposition.Parameters.Name, "vedlegg")
                                    .withParameter(ContentDisposition.Parameters.FileName, fileName)
                                    .toString()
                            )
                        )
                    )
                )
            )
        )
    }.apply {
        assertEquals(expectedCode, response.status())
        return if (expectedCode == HttpStatusCode.Created) {
            val locationHeader = response.headers[HttpHeaders.Location]
            assertNotNull(locationHeader)
            locationHeader
        } else ""
    }
}

fun TestApplicationEngine.jpegUrl(
    cookie: Cookie
): String {
    return handleRequestUploadImage(
        cookie = cookie,
        vedlegg = "vedlegg/nav-logo.png".fromResources().readBytes(),
        fileName = "nav-logo.png",
        contentType = "image/png"
    )
}

fun TestApplicationEngine.pdUrl(
    cookie: Cookie
): String {
    return handleRequestUploadImage(
        cookie = cookie,
        vedlegg = "vedlegg/test.pdf".fromResources().readBytes(),
        fileName = "test.pdf",
        contentType = "application/pdf"
    )
}
