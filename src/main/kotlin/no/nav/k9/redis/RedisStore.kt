package no.nav.k9.redis

import io.lettuce.core.RedisClient
import java.util.*
import java.util.concurrent.TimeUnit

class RedisStore constructor(
        redisClient: RedisClient) {

    private val connection = redisClient.connect()
    private val async = connection.async()!!
    fun get(key: String): String? {
        val get = async.get(key)
        val await = get.await(10, TimeUnit.SECONDS)
        if (await) {
            return get.get()
        }
        return null
    }

    fun set(key: String, value: String, expirationDate: Date): String? {
        val set = async.set(key, value)

        if (set.await(10, TimeUnit.SECONDS)) {
            async.pexpireat(key, expirationDate)
            return set.get()
        }

        return null
    }

    fun delete(key: String): Boolean {
        val del = async.del(key)

        if (del.await(10, TimeUnit.SECONDS)) {
            return true
        }

        return false
    }
}
