package no.nav.k9.mellomlagring

import com.github.fppt.jedismock.RedisServer
import com.typesafe.config.ConfigFactory
import io.ktor.config.HoconApplicationConfig
import io.ktor.util.KtorExperimentalAPI
import no.nav.k9.Configuration
import no.nav.k9.TestConfiguration
import no.nav.k9.mellomlagring.MellomlagringTest.Companion.redisClient
import no.nav.k9.redis.RedisConfig
import no.nav.k9.redis.RedisConfigurationProperties
import no.nav.k9.redis.RedisMockUtil
import no.nav.k9.redis.RedisStore
import org.awaitility.Awaitility
import org.awaitility.Durations
import org.junit.AfterClass
import org.slf4j.LoggerFactory
import java.util.*
import kotlin.test.*

@KtorExperimentalAPI
class MellomlagringTest {
    private companion object {
        val logger = LoggerFactory.getLogger(MellomlagringTest::class.java)

        val redisServer: RedisServer = RedisServer
            .newRedisServer(6379)
            .started()

        val redisClient = RedisConfig.redisClient(
            redisHost = redisServer.host,
            redisPort = redisServer.bindPort
        )

        val redisStore = RedisStore(
            redisClient
        )
        val mellomlagringService = MellomlagringService(
            redisStore,
            "VerySecretPass"
        )

        @AfterClass
        @JvmStatic
        fun teardown() {
            redisClient.shutdown()
            redisServer.stop()
        }
    }

    @Test
    internal fun `mellomlagre verdier`() {
        mellomlagringService.setMellomlagring("test", "test")

        val mellomlagring = mellomlagringService.getMellomlagring("test")

        assertEquals("test", mellomlagring)
    }

    @Test
    internal fun `verdier skal være krypterte`() {

        mellomlagringService.setMellomlagring("test", "test")

        val mellomlagring = mellomlagringService.getMellomlagring("test")
        assertNotNull(redisStore.get("mellomlagring_test"))
        assertNotEquals(mellomlagring, redisStore.get("test"))
    }

    @Test
    internal fun `Oppdatering av mellomlagret verdi, skal ikke slette expiry`() {
        val key = "test"
        var forventetVerdi = "test"
        val expirationDate = Calendar.getInstance().let {
            it.add(Calendar.MINUTE, 1)
            it.time
        }

        mellomlagringService.setMellomlagring(
            fnr = key,
            midlertidigSøknad = forventetVerdi,
            expirationDate = expirationDate
        )

        var faktiskVerdi = mellomlagringService.getMellomlagring(key)
        assertEquals(forventetVerdi, faktiskVerdi)

        val ttl = mellomlagringService.getTTLInMs(key)
        assertNotEquals(ttl, "-2")
        assertNotEquals(ttl, "-1")

        forventetVerdi = "test2"
        mellomlagringService.updateMellomlagring(key, forventetVerdi)
        faktiskVerdi = mellomlagringService.getMellomlagring(key)
        assertEquals(forventetVerdi, faktiskVerdi)

        assertNotEquals(ttl, "-2")
        assertNotEquals(ttl, "-1")
    }

    @Test
    internal fun `mellomlagret verdier skal være utgått etter 500 ms`() {
        val fnr = "12345678910"
        val søknad = "test"

        val expirationDate = Calendar.getInstance().let {
            it.add(Calendar.MILLISECOND, 500)
            it.time
        }

        mellomlagringService.setMellomlagring(fnr, søknad, expirationDate = expirationDate)
        var faktiskVerdi = mellomlagringService.getMellomlagring(fnr)
        logger.info("Hentet mellomlagret verdi = {}", faktiskVerdi)
        assertEquals(søknad, faktiskVerdi)

        assertNotEquals(mellomlagringService.getTTLInMs(fnr), "-2")
        assertNotEquals(mellomlagringService.getTTLInMs(fnr), "-1")

        Awaitility.waitAtMost(Durations.ONE_SECOND).untilAsserted {
            faktiskVerdi = mellomlagringService.getMellomlagring(fnr)
            logger.info("Hentet mellomlagret verdi = {}", faktiskVerdi)
            assertNull(faktiskVerdi)
        }
    }

}
