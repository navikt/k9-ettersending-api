package no.nav.k9

import com.github.fppt.jedismock.RedisServer
import io.ktor.server.testing.withApplication
import no.nav.helse.dusseldorf.testsupport.asArguments
import no.nav.helse.dusseldorf.testsupport.wiremock.WireMockBuilder
import no.nav.k9.mellomlagring.started
import no.nav.k9.wiremock.*
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
                .stubK9DokumentHealth()
                .stubK9EttersendingMottakHealth()
                .stubOppslagHealth()
                .stubLeggSoknadTilProsessering("v1/ettersend")
                .stubK9Dokument()
                .stubK9OppslagSoker()

            val redisServer: RedisServer = RedisServer
                .newRedisServer(6379)
                .started()

            val testArgs = TestConfiguration.asMap(
                port = 8082,
                wireMockServer = wireMockServer,
                redisServer = redisServer
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
