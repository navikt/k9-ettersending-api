package no.nav.k9.redis

import io.ktor.util.KtorExperimentalAPI
import io.lettuce.core.RedisClient
import no.nav.k9.Configuration

class RedisConfig(private val redisConfigurationProperties: RedisConfigurationProperties) {

    @KtorExperimentalAPI
    fun redisClient(configuration: Configuration): RedisClient {
        redisConfigurationProperties.startInMemoryRedisIfMocked()
        return RedisClient.create("redis://${configuration.getRedisHost()}:${configuration.getRedisPort()}")
    }

}
