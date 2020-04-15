package no.nav.k9.general.systemauth

import com.nimbusds.jose.jwk.JWK
import no.nav.helse.dusseldorf.ktor.auth.Client
import no.nav.helse.dusseldorf.ktor.auth.PrivateKeyClient
import no.nav.helse.dusseldorf.oauth2.client.DirectKeyId
import no.nav.helse.dusseldorf.oauth2.client.FromJwk
import no.nav.helse.dusseldorf.oauth2.client.SignedJwtAccessTokenClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory

internal class AccessTokenClientResolver(
    clients: Map<String, Client>
) {
    companion object {
        private val logger: Logger = LoggerFactory.getLogger(AccessTokenClientResolver::class.java)
        private const val AZURE_V2_ALIAS = "azure-v2"
    }

    private val azureV2Client = clients.getOrElse(AZURE_V2_ALIAS) {
        throw IllegalStateException("Client[$AZURE_V2_ALIAS] må være satt opp.")
    } as PrivateKeyClient

    private val keyId = try {
        val jwk = JWK.parse(azureV2Client.privateKeyJwk)
        requireNotNull(jwk.keyID) { "Azure JWK inneholder ikke keyID." }
        jwk.keyID
    } catch (_: Throwable) {
        throw IllegalArgumentException("Azure JWK på feil format.")
    }

    private val azureV2AccessTokenClient = SignedJwtAccessTokenClient(
        clientId = azureV2Client.clientId(),
        tokenEndpoint = azureV2Client.tokenEndpoint(),
        privateKeyProvider = FromJwk(azureV2Client.privateKeyJwk),
        keyIdProvider = DirectKeyId(keyId)
    )

    internal fun accessTokenClient() = azureV2AccessTokenClient
}
