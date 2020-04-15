package no.nav.k9.redis

import no.nav.k9.redis.RedisMockUtil.startRedisMocked

class RedisConfigurationProperties(private val redisMocked: Boolean) {

    fun startInMemoryRedisIfMocked() {
        if (redisMocked) {
            startRedisMocked()
        }
    }
}
