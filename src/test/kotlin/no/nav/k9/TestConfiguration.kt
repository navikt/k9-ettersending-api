package no.nav.k9

import com.github.tomakehurst.wiremock.WireMockServer
import no.nav.common.KafkaEnvironment
import no.nav.helse.dusseldorf.testsupport.jws.ClientCredentials
import no.nav.helse.dusseldorf.testsupport.jws.LoginService
import no.nav.helse.dusseldorf.testsupport.jws.Tokendings
import no.nav.helse.dusseldorf.testsupport.wiremock.getAzureV2WellKnownUrl
import no.nav.helse.dusseldorf.testsupport.wiremock.getLoginServiceV1WellKnownUrl
import no.nav.helse.dusseldorf.testsupport.wiremock.getTokendingsWellKnownUrl
import no.nav.k9.wiremock.getK9MellomlagringUrl
import no.nav.k9.wiremock.getK9OppslagUrl

object TestConfiguration {

    fun asMap(
        wireMockServer: WireMockServer? = null,
        kafkaEnvironment: KafkaEnvironment? = null,
        port : Int = 8080,
        k9OppslagUrl: String? = wireMockServer?.getK9OppslagUrl(),
        k9MellomlagringUrl : String? = wireMockServer?.getK9MellomlagringUrl(),
        corsAdresses : String = "http://localhost:8080"
    ) : Map<String, String> {

        val map = mutableMapOf(
            Pair("ktor.deployment.port","$port"),
            Pair("nav.authorization.cookie_name", "localhost-idtoken"),
            Pair("nav.gateways.k9_oppslag_url","$k9OppslagUrl"),
            Pair("nav.gateways.k9_mellomlagring_url", "$k9MellomlagringUrl"),
            Pair("nav.gateways.k9_mellomlagring_ingress","$k9MellomlagringUrl"),
            Pair("nav.cors.addresses", corsAdresses),
        )

        if (wireMockServer != null) {
            // Clients
            map["nav.auth.clients.0.alias"] = "azure-v2"
            map["nav.auth.clients.0.client_id"] = "k9-ettersending-api"
            map["nav.auth.clients.0.private_key_jwk"] = ClientCredentials.ClientC.privateKeyJwk
            map["nav.auth.clients.0.discovery_endpoint"] = wireMockServer.getAzureV2WellKnownUrl()

            map["nav.auth.clients.1.alias"] = "tokenx"
            map["nav.auth.clients.1.client_id"] = "k9-ettersending-api"
            map["nav.auth.clients.1.private_key_jwk"] = ClientCredentials.ClientC.privateKeyJwk
            map["nav.auth.clients.1.discovery_endpoint"] = wireMockServer.getTokendingsWellKnownUrl()



            // Issuers
            map["nav.auth.issuers.0.alias"] = "login-service-v1"
            map["nav.auth.issuers.0.discovery_endpoint"] = wireMockServer.getLoginServiceV1WellKnownUrl()
            map["nav.auth.issuers.1.alias"] = "login-service-v2"
            map["nav.auth.issuers.1.discovery_endpoint"] = wireMockServer.getLoginServiceV1WellKnownUrl()
            map["nav.auth.issuers.1.audience"] = LoginService.V1_0.getAudience()
            map["nav.auth.issuers.2.alias"] = "tokenx"
            map["nav.auth.issuers.2.discovery_endpoint"] = wireMockServer.getTokendingsWellKnownUrl()
            map["nav.auth.issuers.2.audience"] = Tokendings.getAudience()

            // scopes
            map["nav.auth.scopes.k9-mellomlagring-client-id"] = "k9-mellomlagring-client-id/.default"
            map["nav.auth.scopes.k9_mellomlagring_tokenx_audience"] = "dev-gcp:dusseldorf:k9-mellomlagring"
            map["nav.auth.scopes.k9_selvbetjening_oppslag_tokenx_audience"] = "dev-fss:dusseldorf:k9-selvbetjening-oppslag"
        }

        // Kafka
        kafkaEnvironment?.let {
            map["nav.kafka.bootstrap_servers"] = it.brokersURL
        }

        return map.toMap()
    }
}
