package no.nav.k9

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategy
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.jackson.*
import io.ktor.locations.*
import io.ktor.metrics.micrometer.*
import io.ktor.routing.*
import io.ktor.util.*
import io.prometheus.client.hotspot.DefaultExports
import no.nav.helse.dusseldorf.ktor.auth.allIssuers
import no.nav.helse.dusseldorf.ktor.auth.clients
import no.nav.helse.dusseldorf.ktor.auth.multipleJwtIssuers
import no.nav.helse.dusseldorf.ktor.client.HttpRequestHealthCheck
import no.nav.helse.dusseldorf.ktor.client.HttpRequestHealthConfig
import no.nav.helse.dusseldorf.ktor.client.buildURL
import no.nav.helse.dusseldorf.ktor.core.*
import no.nav.helse.dusseldorf.ktor.health.HealthReporter
import no.nav.helse.dusseldorf.ktor.health.HealthRoute
import no.nav.helse.dusseldorf.ktor.health.HealthService
import no.nav.helse.dusseldorf.ktor.jackson.JacksonStatusPages
import no.nav.helse.dusseldorf.ktor.jackson.dusseldorfConfigured
import no.nav.helse.dusseldorf.ktor.metrics.MetricsRoute
import no.nav.helse.dusseldorf.ktor.metrics.init
import no.nav.k9.ettersending.EttersendingService
import no.nav.k9.ettersending.ettersendingApis
import no.nav.k9.general.auth.IdTokenProvider
import no.nav.k9.general.auth.IdTokenStatusPages
import no.nav.k9.general.systemauth.AccessTokenClientResolver
import no.nav.k9.mellomlagring.MellomlagringService
import no.nav.k9.mellomlagring.mellomlagringApis
import no.nav.k9.redis.RedisConfig
import no.nav.k9.redis.RedisStore
import no.nav.k9.soker.SøkerGateway
import no.nav.k9.soker.SøkerService
import no.nav.k9.soker.søkerApis
import no.nav.k9.vedlegg.K9DokumentGateway
import no.nav.k9.vedlegg.VedleggService
import no.nav.k9.vedlegg.vedleggApis
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Duration

fun main(args: Array<String>): Unit = io.ktor.server.netty.EngineMain.main(args)

private val logger: Logger = LoggerFactory.getLogger("nav.k9EttersendingApi")

@KtorExperimentalAPI
@KtorExperimentalLocationsAPI
fun Application.k9EttersendingApi() {
    val appId = environment.config.id()
    logProxyProperties()
    DefaultExports.initialize()

    System.setProperty("dusseldorf.ktor.serializeProblemDetailsWithContentNegotiation", "true")

    val configuration = Configuration(environment.config)
    val apiGatewayApiKey = configuration.getApiGatewayApiKey()
    val accessTokenClientResolver = AccessTokenClientResolver(environment.config.clients())

    install(ContentNegotiation) {
        jackson {
            dusseldorfConfigured()
                .setPropertyNamingStrategy(PropertyNamingStrategy.LOWER_CAMEL_CASE)
                .configure(SerializationFeature.WRITE_DURATIONS_AS_TIMESTAMPS, false)
        }
    }

    install(CORS) {
        method(HttpMethod.Options)
        method(HttpMethod.Get)
        method(HttpMethod.Post)
        method(HttpMethod.Put)
        method(HttpMethod.Delete)
        allowNonSimpleContentTypes = true
        allowCredentials = true
        log.info("Configuring CORS")
        configuration.getWhitelistedCorsAddreses().forEach {
            log.info("Adding host {} with scheme {}", it.host, it.scheme)
            host(host = it.authority, schemes = listOf(it.scheme))
        }
    }

    val idTokenProvider = IdTokenProvider(cookieName = configuration.getCookieName())
    val issuers = configuration.issuers()

    install(Authentication) {
        multipleJwtIssuers(
            issuers = issuers,
            extractHttpAuthHeader = { call ->
                idTokenProvider.getIdToken(call)
                    .somHttpAuthHeader()
            }
        )
    }

    install(StatusPages) {
        DefaultStatusPages()
        JacksonStatusPages()
        IdTokenStatusPages()
    }

    install(Locations)

    install(Routing) {

        val vedleggService = VedleggService(
            k9DokumentGateway = K9DokumentGateway(
                baseUrl = configuration.getK9DokumentUrl()
            )
        )

        val k9EttersendingMottakGateway = K9EttersendingMottakGateway(
            baseUrl = configuration.getK9EttersendingMottakBaseUrl(),
            accessTokenClient = accessTokenClientResolver.accessTokenClient(),
            sendeSoknadTilProsesseringScopes = configuration.getSendSoknadTilProsesseringScopes(),
            apiGatewayApiKey = apiGatewayApiKey
        )

        val sokerGateway = SøkerGateway(
            baseUrl = configuration.getK9OppslagUrl(),
            apiGatewayApiKey = apiGatewayApiKey
        )

        val søkerService = SøkerService(
            søkerGateway = sokerGateway
        )

        authenticate(*issuers.allIssuers()) {

            søkerApis(
                søkerService = søkerService,
                idTokenProvider = idTokenProvider
            )

            mellomlagringApis(
                mellomlagringService = MellomlagringService(
                    RedisStore(
                        redisClient = RedisConfig.redisClient(
                            redisHost = configuration.getRedisHost(),
                            redisPort = configuration.getRedisPort()
                        )
                    ),
                    passphrase = configuration.getStoragePassphrase(),
                ),
                idTokenProvider = idTokenProvider
            )

            vedleggApis(
                vedleggService = vedleggService,
                idTokenProvider = idTokenProvider
            )

            ettersendingApis(
                idTokenProvider = idTokenProvider,
                søkerService = søkerService,
                ettersendingService = EttersendingService(
                    k9EttersendingMottakGateway = k9EttersendingMottakGateway,
                    søkerService = søkerService,
                    vedleggService = vedleggService
                )
            )
        }

        val healthService = HealthService(
            healthChecks = setOf(
                k9EttersendingMottakGateway,
                HttpRequestHealthCheck(
                    mapOf(
                        Url.buildURL(
                            baseUrl = configuration.getK9DokumentUrl(),
                            pathParts = listOf("health")
                        ) to HttpRequestHealthConfig(expectedStatus = HttpStatusCode.OK),
                        Url.buildURL(
                            baseUrl = configuration.getK9EttersendingMottakBaseUrl(),
                            pathParts = listOf("health")
                        ) to HttpRequestHealthConfig(
                            expectedStatus = HttpStatusCode.OK,
                            httpHeaders = mapOf(apiGatewayApiKey.headerKey to apiGatewayApiKey.value)
                        )
                    )
                )
            )
        )

        HealthReporter(
            app = appId,
            healthService = healthService,
            frequency = Duration.ofMinutes(1)
        )

        DefaultProbeRoutes()
        MetricsRoute()
        HealthRoute(
            healthService = healthService
        )
    }

    install(MicrometerMetrics) {
        init(appId)
    }

    intercept(ApplicationCallPipeline.Monitoring) {
        call.request.log()
    }

    install(CallId) {
        generated()
    }

    install(CallLogging) {
        correlationIdAndRequestIdInMdc()
        logRequests()
        mdc("id_token_jti") { call ->
            try {
                idTokenProvider.getIdToken(call).getId()
            } catch (cause: Throwable) {
                null
            }
        }
    }
}

internal fun ObjectMapper.k9EttersendingKonfiguert() = dusseldorfConfigured().apply {
    configure(SerializationFeature.WRITE_DURATIONS_AS_TIMESTAMPS, false)
}

internal fun ObjectMapper.k9DokumentKonfigurert() = dusseldorfConfigured().apply {
    configure(SerializationFeature.WRITE_DURATIONS_AS_TIMESTAMPS, false)
    propertyNamingStrategy = PropertyNamingStrategy.SNAKE_CASE
}

internal fun ObjectMapper.k9SelvbetjeningOppslagKonfigurert(): ObjectMapper {
    return dusseldorfConfigured().apply {
        configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        registerModule(JavaTimeModule())
    }
}
