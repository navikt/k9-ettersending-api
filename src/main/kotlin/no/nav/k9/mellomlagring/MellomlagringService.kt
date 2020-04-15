package no.nav.k9.mellomlagring

import io.ktor.util.KtorExperimentalAPI
import no.nav.k9.redis.RedisStore
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*

class MellomlagringService @KtorExperimentalAPI constructor(private val redisStore: RedisStore, private val passphrase:String) {
    private companion object {
        private val log: Logger = LoggerFactory.getLogger(MellomlagringService::class.java)
    }

    private val nøkkelPrefiks = "mellomlagring_"

    fun getMellomlagring(
        fnr: String
    ): String? {
        val krypto = Krypto(passphrase, fnr)
        val encrypted = redisStore.get(nøkkelPrefiks +fnr) ?: return null
        return krypto.decrypt(encrypted)
    }

    fun setMellomlagring(
        fnr: String,
        midlertidigSøknad: String
    ) {
        val krypto = Krypto(passphrase, fnr)
        val expirationDate = Calendar.getInstance().let {
            it.add(Calendar.HOUR, 24)
            it.time
        }
        redisStore.set(nøkkelPrefiks + fnr, krypto.encrypt(midlertidigSøknad),expirationDate)
    }

    fun deleteMellomlagring(
        fnr: String
    ) {
        redisStore.delete(nøkkelPrefiks + fnr)
    }
}
