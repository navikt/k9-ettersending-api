package no.nav.k9

import com.typesafe.config.ConfigFactory
import io.ktor.config.*
import io.ktor.http.*
import io.ktor.server.testing.*
import no.nav.helse.dusseldorf.ktor.core.fromResources
import no.nav.helse.dusseldorf.testsupport.wiremock.WireMockBuilder
import no.nav.k9.EttersendingUtils.gyldigEttersendingSomJson
import no.nav.k9.EttersendingUtils.hentGyldigEttersending
import no.nav.k9.ettersending.Søknadstype
import no.nav.k9.wiremock.k9EttersendingApiConfig
import no.nav.k9.wiremock.stubK9Mellomlagring
import no.nav.k9.wiremock.stubK9OppslagSoker
import no.nav.k9.wiremock.stubOppslagHealth
import org.json.JSONObject
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.skyscreamer.jsonassert.JSONAssert
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.URL
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ApplicationTest {

    private companion object {

        private val logger: Logger = LoggerFactory.getLogger(ApplicationTest::class.java)

        val wireMockServer = WireMockBuilder()
            .withAzureSupport()
            .withNaisStsSupport()
            .withLoginServiceSupport()
            .withIDPortenSupport()
            .withTokendingsSupport()
            .k9EttersendingApiConfig()
            .build()
            .stubOppslagHealth()
            .stubK9OppslagSoker()
            .stubK9Mellomlagring()

        private val kafkaEnvironment = KafkaWrapper.bootstrap()
        private val kafkaKonsumer = kafkaEnvironment.testConsumer()

        private val gyldigFodselsnummerA = "02119970078"
        val tokenxBrukerToken = getTokendingsToken(fnr = gyldigFodselsnummerA)

        // Se https://github.com/navikt/dusseldorf-ktor#f%C3%B8dselsnummer
        private val myndigDato = "1999-11-02"
        private const val ikkeMyndigFnr = "12125012345"

        fun getConfig(): ApplicationConfig {

            val fileConfig = ConfigFactory.load()
            val testConfig = ConfigFactory.parseMap(
                TestConfiguration.asMap(
                    wireMockServer = wireMockServer,
                    kafkaEnvironment = kafkaEnvironment
                )
            )
            val mergedConfig = testConfig.withFallback(fileConfig)

            return HoconApplicationConfig(mergedConfig)
        }


        val engine = TestApplicationEngine(createTestEnvironment {
            config = getConfig()
        })


        @BeforeAll
        @JvmStatic
        fun buildUp() {
            engine.start(wait = true)
        }

        @AfterAll
        @JvmStatic
        fun tearDown() {
            logger.info("Tearing down")
            wireMockServer.stop()
            logger.info("Tear down complete")
        }
    }

    @Test
    fun `test metrics`() {
        val jpegUrl = engine.jpegUrl(jwtToken = getTokendingsToken(fnr = gyldigFodselsnummerA))
        val finnesIkkeUrl = jpegUrl.substringBeforeLast("/").plus("/").plus(UUID.randomUUID().toString())

        requestAndAssert(
            httpMethod = HttpMethod.Post,
            path = ETTERSEND_URL,
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
            jwtToken = getTokendingsToken(fnr = gyldigFodselsnummerA),
            requestEntity = hentGyldigEttersending().copy(
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
        fodselsdato: String = "1999-11-02",
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
    fun `Hente søker med tokendings token`() {
        requestAndAssert(
            httpMethod = HttpMethod.Get,
            path = SØKER_URL,
            expectedCode = HttpStatusCode.OK,
            expectedResponse = expectedGetSokerJson(gyldigFodselsnummerA),
            jwtToken = getTokendingsToken(fnr = gyldigFodselsnummerA)
        )
    }

    @Test
    fun `Hente søker som ikke er myndig`() {
        wireMockServer.stubK9OppslagSoker(
            statusCode = HttpStatusCode.fromValue(451),
            responseBody =
            //language=json
            """
            {
                "detail": "Policy decision: DENY - Reason: (NAV-bruker er i live AND NAV-bruker er ikke myndig)",
                "instance": "/meg",
                "type": "/problem-details/tilgangskontroll-feil",
                "title": "tilgangskontroll-feil",
                "status": 451
            }
            """.trimIndent()
        )

        requestAndAssert(
            httpMethod = HttpMethod.Get,
            path = SØKER_URL,
            expectedCode = HttpStatusCode.fromValue(451),
            expectedResponse =
            //language=json
            """
            {
                "type": "/problem-details/tilgangskontroll-feil",
                "title": "tilgangskontroll-feil",
                "status": 451,
                "instance": "/soker",
                "detail": "Tilgang nektet."
            }
            """.trimIndent(),
            jwtToken = getTokendingsToken(fnr = ikkeMyndigFnr)
        )

        wireMockServer.stubK9OppslagSoker() // reset til default mapping
    }

    @Test
    fun `Hente søker med tilgangsnivå 3`() {
        requestAndAssert(
            httpMethod = HttpMethod.Get,
            path = SØKER_URL,
            jwtToken = getTokendingsToken(fnr = gyldigFodselsnummerA, level = 3),
            expectedCode = HttpStatusCode.Forbidden,
            expectedResponse = null
        )
    }

    @Test
    fun `Test håndtering av vedlegg`() {
        
        val jpeg = "vedlegg/iPhone_6.jpg".fromResources().readBytes()

        with(engine) {
            // LASTER OPP VEDLEGG
            val url = handleRequestUploadImage(
                jwtToken = getTokendingsToken(fnr = gyldigFodselsnummerA),
                vedlegg = jpeg
            )
            val path = Url(url).fullPath
            // HENTER OPPLASTET VEDLEGG
            handleRequest(HttpMethod.Get, path) {
                addHeader(HttpHeaders.Authorization, "Bearer $tokenxBrukerToken")
            }.apply {
                assertEquals(HttpStatusCode.OK, response.status())
                assertTrue(Arrays.equals(jpeg, response.byteContent))
                // SLETTER OPPLASTET VEDLEGG
                handleRequest(HttpMethod.Delete, path) {
                    addHeader(HttpHeaders.Authorization, "Bearer $tokenxBrukerToken")
                }.apply {
                    assertEquals(HttpStatusCode.NoContent, response.status())
                    // VERIFISERER AT VEDLEGG ER SLETTET
                    handleRequest(HttpMethod.Get, path) {
                        addHeader(HttpHeaders.Authorization, "Bearer $tokenxBrukerToken")
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
            jwtToken = getTokendingsToken(fnr = gyldigFodselsnummerA),
            vedlegg = "jwkset.json".fromResources().readBytes(),
            contentType = "application/json",
            fileName = "jwkset.json",
            expectedCode = HttpStatusCode.BadRequest
        )
    }

    @Test
    fun `Test opplasting av for stort vedlegg`() {
        engine.handleRequestUploadImage(
            jwtToken = getTokendingsToken(fnr = gyldigFodselsnummerA),
            vedlegg = ByteArray(8 * 1024 * 1024 + 10),
            contentType = "image/png",
            fileName = "big_picture.png",
            expectedCode = HttpStatusCode.PayloadTooLarge
        )
    }

    @Test
    fun `Sende full gyldig ettersending`() {
        
        val jpegUrl = engine.jpegUrl(tokenxBrukerToken)
        val pdfUrl = engine.pdUrl(tokenxBrukerToken)

        val ettersending = hentGyldigEttersending().copy(
            vedlegg = listOf(
                URL(jpegUrl), URL(pdfUrl)
            )
        )

        requestAndAssert(
            httpMethod = HttpMethod.Post,
            path = ETTERSEND_URL,
            expectedResponse = null,
            expectedCode = HttpStatusCode.Accepted,
            jwtToken = tokenxBrukerToken,
            requestEntity = ettersending.somJson()
        )

        hentOgAssertEttersending(JSONObject(ettersending))
    }

    @Test
    fun `Sende full gyldig ettersending uten beskrivelse`() {
        
        val jpegUrl = engine.jpegUrl(tokenxBrukerToken)
        val pdfUrl = engine.pdUrl(tokenxBrukerToken)
        val ettersending = hentGyldigEttersending().copy(
            beskrivelse = null,
            søknadstype = Søknadstype.OMP_UT_SNF,
            vedlegg = listOf(
                URL(jpegUrl), URL(pdfUrl)
            )
        )

        requestAndAssert(
            httpMethod = HttpMethod.Post,
            path = ETTERSEND_URL,
            expectedResponse = null,
            expectedCode = HttpStatusCode.Accepted,
            jwtToken = tokenxBrukerToken,
            requestEntity = ettersending.somJson()
        )

        hentOgAssertEttersending(JSONObject(ettersending))
    }

    @Test
    fun `Sende gyldig ettersending som raw json for OMP_UTV_MA`() {
        
        val jpegUrl = engine.jpegUrl(tokenxBrukerToken)
        val pdfUrl = engine.pdUrl(tokenxBrukerToken)
        val ettersending = gyldigEttersendingSomJson(jpegUrl, pdfUrl, "OMP_UTV_MA")
        requestAndAssert(
            httpMethod = HttpMethod.Post,
            path = ETTERSEND_URL,
            expectedCode = HttpStatusCode.Accepted,
            jwtToken = getTokendingsToken(fnr = gyldigFodselsnummerA),
            expectedResponse = null,
            requestEntity = ettersending
        )

        hentOgAssertEttersending(JSONObject(ettersending))
    }

    @Test
    fun `Sende gyldig ettersending som raw json for OMP_UTV_KS`() {
        
        val jpegUrl = engine.jpegUrl(tokenxBrukerToken)
        val pdfUrl = engine.pdUrl(tokenxBrukerToken)
        val ettersending = gyldigEttersendingSomJson(jpegUrl, pdfUrl, "OMP_UTV_KS")

        requestAndAssert(
            httpMethod = HttpMethod.Post,
            path = ETTERSEND_URL,
            expectedCode = HttpStatusCode.Accepted,
            jwtToken = tokenxBrukerToken,
            expectedResponse = null,
            requestEntity = ettersending
        )

        hentOgAssertEttersending(JSONObject(ettersending))
    }

    @Test
    fun `Sende gyldig ettersending som raw json for OMP_UT_SNF`() {
        
        val jpegUrl = engine.jpegUrl(tokenxBrukerToken)
        val pdfUrl = engine.pdUrl(tokenxBrukerToken)
        val ettersending = gyldigEttersendingSomJson(jpegUrl, pdfUrl, "OMP_UT_SNF")

        requestAndAssert(
            httpMethod = HttpMethod.Post,
            path = ETTERSEND_URL,
            expectedCode = HttpStatusCode.Accepted,
            jwtToken = tokenxBrukerToken,
            expectedResponse = null,
            requestEntity = ettersending
        )

        hentOgAssertEttersending(JSONObject(ettersending))
    }

    @Test
    fun `Sende gyldig ettersending som raw json for OMP_UT_ARBEIDSTAKER`() {
        
        val jpegUrl = engine.jpegUrl(tokenxBrukerToken)
        val pdfUrl = engine.pdUrl(tokenxBrukerToken)

        val ettersending = gyldigEttersendingSomJson(jpegUrl, pdfUrl, "OMP_UT_ARBEIDSTAKER")

        requestAndAssert(
            httpMethod = HttpMethod.Post,
            path = ETTERSEND_URL,
            expectedCode = HttpStatusCode.Accepted,
            jwtToken = tokenxBrukerToken,
            expectedResponse = null,
            requestEntity = ettersending
        )
        hentOgAssertEttersending(JSONObject(ettersending))
    }

    @Test
    fun `Sende gyldig ettersending som raw json for PLEIEPENGER_SYKT_BARN`() {
        
        val jpegUrl = engine.jpegUrl(tokenxBrukerToken)
        val pdfUrl = engine.pdUrl(tokenxBrukerToken)

        val ettersending = gyldigEttersendingSomJson(jpegUrl, pdfUrl, "PLEIEPENGER_SYKT_BARN")
        requestAndAssert(
            httpMethod = HttpMethod.Post,
            path = ETTERSEND_URL,
            expectedCode = HttpStatusCode.Accepted,
            jwtToken = tokenxBrukerToken,
            expectedResponse = null,
            requestEntity = ettersending
        )
        hentOgAssertEttersending(JSONObject(ettersending))
    }

    @Test
    fun `Sende gyldig ettersending som raw json for OMP_DELE_DAGER`() {
        
        val jpegUrl = engine.jpegUrl(tokenxBrukerToken)
        val pdfUrl = engine.pdUrl(tokenxBrukerToken)
        val ettersending = gyldigEttersendingSomJson(jpegUrl, pdfUrl, "OMP_DELE_DAGER")

        requestAndAssert(
            httpMethod = HttpMethod.Post,
            path = ETTERSEND_URL,
            expectedCode = HttpStatusCode.Accepted,
            jwtToken = tokenxBrukerToken,
            expectedResponse = null,
            requestEntity = ettersending
        )
        hentOgAssertEttersending(JSONObject(ettersending))
    }

    @Test
    fun `Sende ettersending som mangler påkrevd felt`() {
        
        val jpegUrl = engine.jpegUrl(tokenxBrukerToken)
        val pdfUrl = engine.pdUrl(tokenxBrukerToken)

        requestAndAssert(
            httpMethod = HttpMethod.Post,
            path = ETTERSEND_URL,
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
            jwtToken = tokenxBrukerToken,
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
        
        val jpegUrl = engine.jpegUrl(tokenxBrukerToken)
        val finnesIkkeUrl = jpegUrl.substringBeforeLast("/").plus("/").plus(UUID.randomUUID().toString())

        requestAndAssert(
            httpMethod = HttpMethod.Post,
            path = ETTERSEND_URL,
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
            jwtToken = tokenxBrukerToken,
            requestEntity = hentGyldigEttersending().copy(
                vedlegg = listOf(
                    URL(jpegUrl), URL(finnesIkkeUrl)
                )
            ).somJson()
        )
    }

    @Test
    fun `Sende ettersending for pleiepenger sykt barn med tom beskrivelse`() {
        
        val jpegUrl = engine.jpegUrl(tokenxBrukerToken)
        val pdfUrl = engine.pdUrl(tokenxBrukerToken)

        requestAndAssert(
            httpMethod = HttpMethod.Post,
            path = ETTERSEND_URL,
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
            jwtToken = tokenxBrukerToken,
            requestEntity = hentGyldigEttersending().copy(
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
        
        val jpegUrl = engine.jpegUrl(tokenxBrukerToken)
        val pdfUrl = engine.pdUrl(tokenxBrukerToken)

        requestAndAssert(
            httpMethod = HttpMethod.Post,
            path = ETTERSEND_URL,
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
            jwtToken = tokenxBrukerToken,
            requestEntity = hentGyldigEttersending().copy(
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
        jwtToken: String? = null
    ) {
        with(engine) {
            handleRequest(httpMethod, path) {
                if (jwtToken != null) addHeader(HttpHeaders.Authorization, "Bearer $jwtToken")
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

    private fun hentOgAssertEttersending(ettersending: JSONObject) {
        val hentet = kafkaKonsumer.hentEttersending(ettersending.getString("søknadId"))
        assertGyldigEttersending(ettersending, hentet.data)
    }

    private fun assertGyldigEttersending(
        ettersendingSendtInn: JSONObject,
        ettersendingFraTopic: JSONObject
    ) {
        assertTrue(ettersendingFraTopic.has("søker"))
        assertTrue(ettersendingFraTopic.has("mottatt"))
        assertTrue(ettersendingFraTopic.has("k9Format"))

        assertEquals(
            ettersendingSendtInn.getEnum(Søknadstype::class.java, "søknadstype"),
            ettersendingFraTopic.getEnum(Søknadstype::class.java, "søknadstype")
        )
        assertEquals(ettersendingSendtInn.getString("søknadId"), ettersendingFraTopic.getString("søknadId"))
        if (ettersendingSendtInn.has("beskrivelse")) {
            assertEquals(ettersendingSendtInn.getString("beskrivelse"), ettersendingFraTopic.getString("beskrivelse"))
        }

        assertEquals(
            ettersendingSendtInn.getJSONArray("vedlegg").length(),
            ettersendingFraTopic.getJSONArray("vedleggId").length()
        )
    }
}
