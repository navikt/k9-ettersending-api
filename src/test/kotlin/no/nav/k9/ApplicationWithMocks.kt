package no.nav.k9

import io.ktor.server.testing.*
import no.nav.helse.dusseldorf.testsupport.asArguments
import no.nav.helse.dusseldorf.testsupport.wiremock.WireMockBuilder
import no.nav.k9.wiremock.k9EttersendingApiConfig
import no.nav.k9.wiremock.stubK9Mellomlagring
import no.nav.k9.wiremock.stubK9OppslagSoker
import no.nav.k9.wiremock.stubOppslagHealth
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class ApplicationWithMocks {
    companion object {

        private val logger: Logger = LoggerFactory.getLogger(ApplicationWithMocks::class.java)

        @JvmStatic
        fun main(args: Array<String>) {

            val wireMockServer = WireMockBuilder()
                .withPort(8081)
                .withAzureSupport()
                .withNaisStsSupport()
                .withLoginServiceSupport()
                .k9EttersendingApiConfig()
                .build()
                .stubOppslagHealth()
                .stubK9Mellomlagring()
                .stubK9OppslagSoker()

            val testArgs = TestConfiguration.asMap(
                port = 8082,
                wireMockServer = wireMockServer
            ).asArguments()

            Runtime.getRuntime().addShutdownHook(object : Thread() {
                override fun run() {
                    logger.info("Tearing down")
                    wireMockServer.stop()
                    logger.info("Tear down complete")
                }
            })

            withApplication { no.nav.k9.main(testArgs) }
        }
    }
}
