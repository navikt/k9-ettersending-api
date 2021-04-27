package no.nav.k9

import com.github.fppt.jedismock.RedisServer
import com.github.tomakehurst.wiremock.http.Cookie
import com.typesafe.config.ConfigFactory
import io.ktor.config.*
import io.ktor.http.*
import io.ktor.server.testing.*
import io.ktor.util.*
import no.nav.helse.dusseldorf.ktor.core.fromResources
import no.nav.helse.dusseldorf.testsupport.wiremock.WireMockBuilder
import no.nav.helse.getAuthCookie
import no.nav.k9.EttersendingUtils.gyldigEttersendingSomJson
import no.nav.k9.ettersending.Søknadstype
import no.nav.k9.mellomlagring.started
import no.nav.k9.wiremock.*
import org.junit.AfterClass
import org.junit.BeforeClass
import org.skyscreamer.jsonassert.JSONAssert
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.URL
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val fnr = "290990123456"
private const val ikkeMyndigFnr = "12125012345"

// Se https://github.com/navikt/dusseldorf-ktor#f%C3%B8dselsnummer
private val gyldigFodselsnummerA = "02119970078"
private val ikkeMyndigDato = "2050-12-12"

@KtorExperimentalAPI
class ApplicationTest {

    private companion object {

        private val logger: Logger = LoggerFactory.getLogger(ApplicationTest::class.java)

        val wireMockServer = WireMockBuilder()
            .withAzureSupport()
            .withNaisStsSupport()
            .withLoginServiceSupport()
            .k9EttersendingApiConfig()
            .build()
            .stubK9DokumentHealth()
            .stubK9EttersendingMottakHealth()
            .stubOppslagHealth()
            .stubLeggSoknadTilProsessering("v1/ettersend")
            .stubK9OppslagSoker()
            .stubK9Dokument()

        val redisServer: RedisServer = RedisServer
            .newRedisServer().started()

        fun getConfig(): ApplicationConfig {

            val fileConfig = ConfigFactory.load()
            val testConfig = ConfigFactory.parseMap(TestConfiguration.asMap(
                wireMockServer = wireMockServer,
                redisServer = redisServer
            ))
            val mergedConfig = testConfig.withFallback(fileConfig)

            return HoconApplicationConfig(mergedConfig)
        }


        val engine = TestApplicationEngine(createTestEnvironment {
            config = getConfig()
        })


        @BeforeClass
        @JvmStatic
        fun buildUp() {
            engine.start(wait = true)
        }

        @AfterClass
        @JvmStatic
        fun tearDown() {
            logger.info("Tearing down")
            wireMockServer.stop()
            redisServer.stop()
            logger.info("Tear down complete")
        }
    }

    @Test
    fun `test metrics`() {

        val cookie = getAuthCookie(gyldigFodselsnummerA)
        val jpegUrl = engine.jpegUrl(cookie)
        val finnesIkkeUrl = jpegUrl.substringBeforeLast("/").plus("/").plus(UUID.randomUUID().toString())

        requestAndAssert(
            httpMethod = HttpMethod.Post,
            path = "/ettersend",
            expectedResponse = """
            {
              "type": "/problem-details/invalid-request-parameters",
              "title": "invalid-request-parameters",
              "status": 400,
              "detail": "Requesten inneholder ugyldige paramtere.",
              "instance": "about:blank",
              "invalid_parameters": [
                {
                  "type": "entity",
                  "name": "vedlegg",
                  "reason": "Mottok referanse til 2 vedlegg, men fant kun 1 vedlegg.",
                  "invalid_value": [
                    "$jpegUrl",
                    "$finnesIkkeUrl"
                  ]
                }
              ]
            }""".trimIndent(),
            expectedCode = HttpStatusCode.BadRequest,
            cookie = cookie,
            requestEntity = EttersendingUtils.gyldigEttersending.copy(
                vedlegg = listOf(
                    URL(jpegUrl), URL(finnesIkkeUrl)
                )
            ).somJson()
        )

        with(engine) {
            handleRequest(HttpMethod.Get, "/metrics") {}.apply {
                System.err.println(response.content)
                assertEquals(HttpStatusCode.OK, response.status())
            }
        }
    }

    @Test
    fun `test isready, isalive, health og metrics`() {
        with(engine) {
            handleRequest(HttpMethod.Get, "/isready") {}.apply {
                assertEquals(HttpStatusCode.OK, response.status())
                handleRequest(HttpMethod.Get, "/isalive") {}.apply {
                    assertEquals(HttpStatusCode.OK, response.status())
                    handleRequest(HttpMethod.Get, "/metrics") {}.apply {
                        assertEquals(HttpStatusCode.OK, response.status())
                        handleRequest(HttpMethod.Get, "/health") {}.apply {
                            assertEquals(HttpStatusCode.OK, response.status())
                        }
                    }
                }
            }
        }
    }

    fun expectedGetSokerJson(
        fodselsnummer: String,
        fodselsdato: String = "1997-05-25",
        myndig: Boolean = true
    ) = """
            {
                "etternavn": "MORSEN",
                "fornavn": "MOR",
                "mellomnavn": "HEISANN",
                "fødselsnummer": "$fodselsnummer",
                "aktørId": "12345",
                "fødselsdato": "$fodselsdato",
                "myndig": $myndig
            }
        """.trimIndent()

    @Test
    fun `Hente søker`() {
        requestAndAssert(
            httpMethod = HttpMethod.Get,
            path = "/soker",
            expectedCode = HttpStatusCode.OK,
            expectedResponse = expectedGetSokerJson(fnr)
        )
    }

    @Test
    fun `Hente søker med tilgangsnivå 3`() {
        requestAndAssert(
            httpMethod = HttpMethod.Get,
            path = "/soker",
            cookie = getAuthCookie(fnr = fnr, level = 3),
            expectedCode = HttpStatusCode.Forbidden,
            expectedResponse = null
        )
    }

    @Test
    fun `Hente søker som ikke er myndig`() {
        requestAndAssert(
            httpMethod = HttpMethod.Get,
            path = "/soker",
            expectedCode = HttpStatusCode.OK,
            expectedResponse = expectedGetSokerJson(
                fodselsnummer = ikkeMyndigFnr,
                fodselsdato = ikkeMyndigDato,
                myndig = false
            ),
            cookie = getAuthCookie(ikkeMyndigFnr)
        )
    }

    @Test
    fun `Test håndtering av vedlegg`() {
        val cookie = getAuthCookie(fnr)
        val jpeg = "vedlegg/iPhone_6.jpg".fromResources().readBytes()

        with(engine) {
            // LASTER OPP VEDLEGG
            val url = handleRequestUploadImage(
                cookie = cookie,
                vedlegg = jpeg
            )
            val path = Url(url).fullPath
            // HENTER OPPLASTET VEDLEGG
            handleRequest(HttpMethod.Get, path) {
                addHeader("Cookie", cookie.toString())
            }.apply {
                assertEquals(HttpStatusCode.OK, response.status())
                assertTrue(Arrays.equals(jpeg, response.byteContent))
                // SLETTER OPPLASTET VEDLEGG
                handleRequest(HttpMethod.Delete, path) {
                    addHeader("Cookie", cookie.toString())
                }.apply {
                    assertEquals(HttpStatusCode.NoContent, response.status())
                    // VERIFISERER AT VEDLEGG ER SLETTET
                    handleRequest(HttpMethod.Get, path) {
                        addHeader("Cookie", cookie.toString())
                    }.apply {
                        assertEquals(HttpStatusCode.NotFound, response.status())
                    }
                }
            }
        }
    }

    @Test
    fun `Test opplasting av ikke støttet vedleggformat`() {
        engine.handleRequestUploadImage(
            cookie = getAuthCookie(gyldigFodselsnummerA),
            vedlegg = "jwkset.json".fromResources().readBytes(),
            contentType = "application/json",
            fileName = "jwkset.json",
            expectedCode = HttpStatusCode.BadRequest
        )
    }

    @Test
    fun `Test opplasting av for stort vedlegg`() {
        engine.handleRequestUploadImage(
            cookie = getAuthCookie(gyldigFodselsnummerA),
            vedlegg = ByteArray(8 * 1024 * 1024 + 10),
            contentType = "image/png",
            fileName = "big_picture.png",
            expectedCode = HttpStatusCode.PayloadTooLarge
        )
    }

    @Test
    fun `Sende full gyldig ettersending`() {
        val cookie = getAuthCookie(gyldigFodselsnummerA)
        val jpegUrl = engine.jpegUrl(cookie)
        val pdfUrl = engine.pdUrl(cookie)

        requestAndAssert(
            httpMethod = HttpMethod.Post,
            path = "/ettersend",
            expectedResponse = null,
            expectedCode = HttpStatusCode.Accepted,
            cookie = cookie,
            requestEntity = EttersendingUtils.gyldigEttersending.copy(
                vedlegg = listOf(
                    URL(jpegUrl), URL(pdfUrl)
                )
            ).somJson()
        )
    }

    @Test
    fun `Sende full gyldig ettersending uten beskrivelse`() {
        val cookie = getAuthCookie(gyldigFodselsnummerA)
        val jpegUrl = engine.jpegUrl(cookie)
        val pdfUrl = engine.pdUrl(cookie)

        requestAndAssert(
            httpMethod = HttpMethod.Post,
            path = "/ettersend",
            expectedResponse = null,
            expectedCode = HttpStatusCode.Accepted,
            cookie = cookie,
            requestEntity = EttersendingUtils.gyldigEttersending.copy(
                beskrivelse = null,
                søknadstype = Søknadstype.OMP_UT_SNF,
                vedlegg = listOf(
                    URL(jpegUrl), URL(pdfUrl)
                )
            ).somJson()
        )
    }

    @Test
    fun `Sende gyldig ettersending som raw json for OMP_UTV_MA`() {
        val cookie = getAuthCookie(gyldigFodselsnummerA)
        val jpegUrl = engine.jpegUrl(cookie)
        val pdfUrl = engine.pdUrl(cookie)

        requestAndAssert(
            httpMethod = HttpMethod.Post,
            path = "/ettersend",
            expectedCode = HttpStatusCode.Accepted,
            cookie = cookie,
            expectedResponse = null,
            requestEntity = gyldigEttersendingSomJson(jpegUrl, pdfUrl, "OMP_UTV_MA")
        )
    }

    @Test
    fun `Sende gyldig ettersending som raw json for OMP_UTV_KS`() {
        val cookie = getAuthCookie(gyldigFodselsnummerA)
        val jpegUrl = engine.jpegUrl(cookie)
        val pdfUrl = engine.pdUrl(cookie)

        requestAndAssert(
            httpMethod = HttpMethod.Post,
            path = "/ettersend",
            expectedCode = HttpStatusCode.Accepted,
            cookie = cookie,
            expectedResponse = null,
            requestEntity = gyldigEttersendingSomJson(jpegUrl, pdfUrl, "OMP_UTV_KS")
        )
    }

    @Test
    fun `Sende gyldig ettersending som raw json for OMP_UT_SNF`() {
        val cookie = getAuthCookie(gyldigFodselsnummerA)
        val jpegUrl = engine.jpegUrl(cookie)
        val pdfUrl = engine.pdUrl(cookie)

        requestAndAssert(
            httpMethod = HttpMethod.Post,
            path = "/ettersend",
            expectedCode = HttpStatusCode.Accepted,
            cookie = cookie,
            expectedResponse = null,
            requestEntity = gyldigEttersendingSomJson(jpegUrl, pdfUrl, "OMP_UT_SNF")
        )
    }

    @Test
    fun `Sende gyldig ettersending som raw json for OMP_UT_ARBEIDSTAKER`() {
        val cookie = getAuthCookie(gyldigFodselsnummerA)
        val jpegUrl = engine.jpegUrl(cookie)
        val pdfUrl = engine.pdUrl(cookie)

        requestAndAssert(
            httpMethod = HttpMethod.Post,
            path = "/ettersend",
            expectedCode = HttpStatusCode.Accepted,
            cookie = cookie,
            expectedResponse = null,
            requestEntity = gyldigEttersendingSomJson(jpegUrl, pdfUrl, "OMP_UT_ARBEIDSTAKER")
        )
    }

    @Test
    fun `Sende gyldig ettersending som raw json for PLEIEPENGER_SYKT_BARN`() {
        val cookie = getAuthCookie(gyldigFodselsnummerA)
        val jpegUrl = engine.jpegUrl(cookie)
        val pdfUrl = engine.pdUrl(cookie)

        requestAndAssert(
            httpMethod = HttpMethod.Post,
            path = "/ettersend",
            expectedCode = HttpStatusCode.Accepted,
            cookie = cookie,
            expectedResponse = null,
            requestEntity = gyldigEttersendingSomJson(jpegUrl, pdfUrl, "PLEIEPENGER_SYKT_BARN")
        )
    }

    @Test
    fun `Sende gyldig ettersending som raw json for OMP_DELE_DAGER`() {
        val cookie = getAuthCookie(gyldigFodselsnummerA)
        val jpegUrl = engine.jpegUrl(cookie)
        val pdfUrl = engine.pdUrl(cookie)

        requestAndAssert(
            httpMethod = HttpMethod.Post,
            path = "/ettersend",
            expectedCode = HttpStatusCode.Accepted,
            cookie = cookie,
            expectedResponse = null,
            requestEntity = gyldigEttersendingSomJson(jpegUrl, pdfUrl, "OMP_DELE_DAGER")
        )
    }

    @Test
    fun `Sende ettersending som mangler påkrevd felt`() {
        val cookie = getAuthCookie(gyldigFodselsnummerA)
        val jpegUrl = engine.jpegUrl(cookie)
        val pdfUrl = engine.pdUrl(cookie)

        requestAndAssert(
            httpMethod = HttpMethod.Post,
            path = "/ettersend",
            expectedResponse = """
                {
                  "type": "/problem-details/invalid-request-parameters",
                  "title": "invalid-request-parameters",
                  "status": 400,
                  "detail": "Requesten inneholder ugyldige paramtere.",
                  "instance": "about:blank",
                  "invalid_parameters": [
                    {
                      "type": "entity",
                      "name": "språk",
                      "reason": "Må være satt.",
                      "invalid_value": null
                    }
                  ]
                }
            """.trimIndent(),
            expectedCode = HttpStatusCode.BadRequest,
            cookie = cookie,
            requestEntity = """
                {
                  "språk": null,
                  "vedlegg": [
                    "$jpegUrl",
                    "$pdfUrl"
                  ],
                  "harForståttRettigheterOgPlikter": true,
                  "harBekreftetOpplysninger": true,
                  "beskrivelse": "Masse tekst",
                  "søknadstype": "OMP_UTV_KS"
                }
            """.trimIndent()
        )
    }

    @Test
    fun `Sende ettersending hvor et vedlegg ikke finnes`() {
        val cookie = getAuthCookie(gyldigFodselsnummerA)
        val jpegUrl = engine.jpegUrl(cookie)
        val finnesIkkeUrl = jpegUrl.substringBeforeLast("/").plus("/").plus(UUID.randomUUID().toString())

        requestAndAssert(
            httpMethod = HttpMethod.Post,
            path = "/ettersend",
            expectedResponse = """
            {
              "type": "/problem-details/invalid-request-parameters",
              "title": "invalid-request-parameters",
              "status": 400,
              "detail": "Requesten inneholder ugyldige paramtere.",
              "instance": "about:blank",
              "invalid_parameters": [
                {
                  "type": "entity",
                  "name": "vedlegg",
                  "reason": "Mottok referanse til 2 vedlegg, men fant kun 1 vedlegg.",
                  "invalid_value": [
                    "$jpegUrl",
                    "$finnesIkkeUrl"
                  ]
                }
              ]
            }""".trimIndent(),
            expectedCode = HttpStatusCode.BadRequest,
            cookie = cookie,
            requestEntity = EttersendingUtils.gyldigEttersending.copy(
                vedlegg = listOf(
                    URL(jpegUrl), URL(finnesIkkeUrl)
                )
            ).somJson()
        )
    }

    @Test
    fun `Sende ettersending for pleiepenger sykt barn med tom beskrivelse`() {
        val cookie = getAuthCookie(gyldigFodselsnummerA)
        val jpegUrl = engine.jpegUrl(cookie)
        val pdfUrl = engine.pdUrl(cookie)

        requestAndAssert(
            httpMethod = HttpMethod.Post,
            path = "/ettersend",
            expectedResponse = """
                {
                  "type": "/problem-details/invalid-request-parameters",
                  "title": "invalid-request-parameters",
                  "status": 400,
                  "detail": "Requesten inneholder ugyldige paramtere.",
                  "instance": "about:blank",
                  "invalid_parameters": [
                    {
                      "type": "entity",
                      "name": "beskrivelse",
                      "reason": "Beskrivelse kan ikke være tom eller blank dersom det gjelder pleiepenger sykt barn",
                      "invalid_value": ""
                    }
                  ]
                }
            """.trimIndent(),
            expectedCode = HttpStatusCode.BadRequest,
            cookie = cookie,
            requestEntity = EttersendingUtils.gyldigEttersending.copy(
                beskrivelse = "",
                søknadstype = Søknadstype.PLEIEPENGER_SYKT_BARN,
                vedlegg = listOf(
                    URL(jpegUrl), URL(pdfUrl)
                )
            ).somJson()
        )
    }

    @Test
    fun `Sende ettersending for pleiepenger sykt barn med whitespace beskrivelse`() {
        val cookie = getAuthCookie(gyldigFodselsnummerA)
        val jpegUrl = engine.jpegUrl(cookie)
        val pdfUrl = engine.pdUrl(cookie)

        requestAndAssert(
            httpMethod = HttpMethod.Post,
            path = "/ettersend",
            expectedResponse = """
                {
                  "type": "/problem-details/invalid-request-parameters",
                  "title": "invalid-request-parameters",
                  "status": 400,
                  "detail": "Requesten inneholder ugyldige paramtere.",
                  "instance": "about:blank",
                  "invalid_parameters": [
                    {
                      "type": "entity",
                      "name": "beskrivelse",
                      "reason": "Beskrivelse kan ikke være tom eller blank dersom det gjelder pleiepenger sykt barn",
                      "invalid_value": " "
                    }
                  ]
                }
            """.trimIndent(),
            expectedCode = HttpStatusCode.BadRequest,
            cookie = cookie,
            requestEntity = EttersendingUtils.gyldigEttersending.copy(
                beskrivelse = " ",
                søknadstype = Søknadstype.PLEIEPENGER_SYKT_BARN,
                vedlegg = listOf(
                    URL(jpegUrl), URL(pdfUrl)
                )
            ).somJson()
        )
    }


    private fun requestAndAssert(
        httpMethod: HttpMethod,
        path: String,
        requestEntity: String? = null,
        expectedResponse: String?,
        expectedCode: HttpStatusCode,
        leggTilCookie: Boolean = true,
        cookie: Cookie = getAuthCookie(fnr)
    ) {
        with(engine) {
            handleRequest(httpMethod, path) {
                if (leggTilCookie) addHeader(HttpHeaders.Cookie, cookie.toString())
                logger.info("Request Entity = $requestEntity")
                addHeader(HttpHeaders.Accept, "application/json")
                if (requestEntity != null) addHeader(HttpHeaders.ContentType, "application/json")
                if (requestEntity != null) setBody(requestEntity)
            }.apply {
                logger.info("Response Entity = ${response.content}")
                logger.info("Expected Entity = $expectedResponse")
                assertEquals(expectedCode, response.status())
                if (expectedResponse != null) {
                    JSONAssert.assertEquals(expectedResponse, response.content!!, true)
                    //assertNotNull(response.headers["problem-details"])
                } else {
                    assertEquals(expectedResponse, response.content)
                }
            }
        }
    }
}
