package no.nav.k9

import io.ktor.config.*
import no.nav.helse.dusseldorf.ktor.auth.EnforceEqualsOrContains
import no.nav.helse.dusseldorf.ktor.auth.issuers
import no.nav.helse.dusseldorf.ktor.auth.withAdditionalClaimRules
import no.nav.helse.dusseldorf.ktor.core.getOptionalList
import no.nav.helse.dusseldorf.ktor.core.getOptionalString
import no.nav.helse.dusseldorf.ktor.core.getRequiredList
import no.nav.helse.dusseldorf.ktor.core.getRequiredString
import no.nav.k9.kafka.KafkaConfig
import java.net.URI

data class Configuration(val config : ApplicationConfig) {

    private val serviceClaims = setOf(
        EnforceEqualsOrContains("acr", "Level4")
    )

    internal fun issuers() = config.issuers().withAdditionalClaimRules(
        mapOf(
            "login-service-v1" to serviceClaims,
            "login-service-v2" to serviceClaims,
            "tokenx" to serviceClaims
        )
    )

    internal fun getCookieName(): String {
        return config.getRequiredString("nav.authorization.cookie_name", secret = false)
    }

    internal fun getWhitelistedCorsAddreses() : List<URI> {
        return config.getOptionalList(
            key = "nav.cors.addresses",
            builder = { value ->
                URI.create(value)
            },
            secret = false
        )
    }

    internal fun getK9OppslagUrl() = URI(config.getRequiredString("nav.gateways.k9_oppslag_url", secret = false))

    internal fun getK9MellomlagringUrl() = URI(config.getRequiredString("nav.gateways.k9_mellomlagring_url", secret = false))
    internal fun getK9MellomlagringScopes() = getScopesFor("k9-mellomlagring-client-id")
    fun getK9MellomlagringTokenxAudience(): Set<String> = getScopesFor("k9_mellomlagring_tokenx_audience")
    internal fun getK9MellomlagringIngress() = URI(config.getRequiredString("nav.gateways.k9_mellomlagring_ingress", secret = false))

    fun getK9SelvbetjeningOppslagTokenxAudience(): Set<String> = getScopesFor("k9_selvbetjening_oppslag_tokenx_audience")

    private fun getScopesFor(operation: String) = config.getRequiredList("nav.auth.scopes.$operation", secret = false, builder = { it }).toSet()

    internal fun getKafkaConfig() = config.getRequiredString("nav.kafka.bootstrap_servers", secret = false).let { bootstrapServers ->
        val trustStore =
            config.getOptionalString("nav.kafka.truststore_path", secret = false)?.let { trustStorePath ->
                config.getOptionalString("nav.kafka.credstore_password", secret = true)?.let { credstorePassword ->
                    Pair(trustStorePath, credstorePassword)
                }
            }

        val keyStore = config.getOptionalString("nav.kafka.keystore_path", secret = false)?.let { keystorePath ->
            config.getOptionalString("nav.kafka.credstore_password", secret = true)?.let { credstorePassword ->
                Pair(keystorePath, credstorePassword)
            }
        }

        KafkaConfig(
            bootstrapServers = bootstrapServers,
            trustStore = trustStore,
            keyStore = keyStore
        )
    }
}
