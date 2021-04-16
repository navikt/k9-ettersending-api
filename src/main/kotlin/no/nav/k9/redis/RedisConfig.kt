package no.nav.k9.redis

import io.ktor.util.*
import io.lettuce.core.RedisClient
import io.lettuce.core.RedisURI
import java.time.Duration

internal object RedisConfig {

    @KtorExperimentalAPI
    internal fun redisClient(redisHost: String, redisPort: Int): RedisClient {
        return RedisClient.create(RedisURI(redisHost, redisPort, Duration.ofSeconds(60)))
    }

}