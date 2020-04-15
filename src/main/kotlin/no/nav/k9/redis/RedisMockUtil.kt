package no.nav.k9.redis

import com.github.fppt.jedismock.RedisServer
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicBoolean

object RedisMockUtil {
    private val log = LoggerFactory.getLogger(RedisMockUtil::class.java)
    private var mockedRedisServer = RedisServer.newRedisServer(6379)
    private var  redisStarted = AtomicBoolean(false)

    @JvmStatic
    fun startRedisMocked() {
        log.warn("Starter MOCKET in-memory redis-server. Denne meldingen skal du aldri se i prod")
        if (!redisStarted.get()) {
            mockedRedisServer.start()
            redisStarted.set(true)
        }
    }

    @JvmStatic
    fun stopRedisMocked() {
        if (redisStarted.get()) {
            mockedRedisServer.stop()
            redisStarted.set(false)
        }
    }


}
