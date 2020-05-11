package no.nav.k9

import com.auth0.jwk.JwkProviderBuilder
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategy
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ktor.application.*
import io.ktor.auth.Authentication
import io.ktor.auth.authenticate
import io.ktor.auth.jwt.JWTPrincipal
import io.ktor.auth.jwt.jwt
import io.ktor.features.*
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.jackson.jackson
import io.ktor.locations.KtorExperimentalLocationsAPI
import io.ktor.locations.Locations
import io.ktor.metrics.micrometer.MicrometerMetrics
import io.ktor.request.receive
import io.ktor.response.header
import io.ktor.routing.Routing
import io.ktor.util.KtorExperimentalAPI
import io.micrometer.core.instrument.Clock
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import io.prometheus.client.CollectorRegistry
import io.prometheus.client.hotspot.DefaultExports
import no.nav.helse.dusseldorf.ktor.auth.clients
import no.nav.helse.dusseldorf.ktor.client.HttpRequestHealthCheck
import no.nav.helse.dusseldorf.ktor.client.HttpRequestHealthConfig
import no.nav.helse.dusseldorf.ktor.client.buildURL
import no.nav.helse.dusseldorf.ktor.core.*
import no.nav.helse.dusseldorf.ktor.health.HealthReporter
import no.nav.helse.dusseldorf.ktor.health.HealthRoute
import no.nav.helse.dusseldorf.ktor.health.HealthService
import no.nav.helse.dusseldorf.ktor.jackson.dusseldorfConfigured
import no.nav.helse.dusseldorf.ktor.metrics.MetricsRoute
import no.nav.k9.ettersending.EttersendingService
import no.nav.k9.general.auth.IdTokenProvider
import no.nav.k9.general.auth.authorizationStatusPages
import no.nav.k9.general.systemauth.AccessTokenClientResolver
import no.nav.k9.mellomlagring.MellomlagringService
import no.nav.k9.mellomlagring.mellomlagringApis
import no.nav.k9.redis.RedisConfig
import no.nav.k9.redis.RedisConfigurationProperties
import no.nav.k9.redis.RedisStore
import no.nav.k9.soker.SøkerGateway
import no.nav.k9.soker.SøkerService
import no.nav.k9.soker.søkerApis
import no.nav.k9.ettersending.ettersendingApis
import no.nav.k9.vedlegg.K9DokumentGateway
import no.nav.k9.vedlegg.VedleggService
import no.nav.k9.vedlegg.vedleggApis
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.concurrent.TimeUnit

fun main(args: Array<String>): Unit  = io.ktor.server.netty.EngineMain.main(args)

private val logger: Logger = LoggerFactory.getLogger("nav.k9EttersendingApi")

@KtorExperimentalAPI
@KtorExperimentalLocationsAPI
fun Application.k9EttersendingApi() {
    val appId = environment.config.id()
    logProxyProperties()
    DefaultExports.initialize()

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
    val jwkProvider = JwkProviderBuilder(configuration.getJwksUrl().toURL())
        .cached(10, 24, TimeUnit.HOURS)
        .rateLimited(10, 1, TimeUnit.MINUTES)
        .build()

    install(Authentication) {
        jwt {
            realm = appId
            verifier(jwkProvider, configuration.getIssuer()) {
                acceptNotBefore(10)
                acceptIssuedAt(10)
            }
            authHeader { call ->
                idTokenProvider
                    .getIdToken(call)
                    .medValidertLevel("Level4")
                    .somHttpAuthHeader()
            }
            validate { credentials ->
                return@validate JWTPrincipal(credentials.payload)
            }
        }
    }

    install(StatusPages) {
        DefaultStatusPages()
        JacksonStatusPages()
        authorizationStatusPages()
        CustomStatusPages()
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

        authenticate {

            søkerApis(
                søkerService = søkerService,
                idTokenProvider = idTokenProvider
            )

            mellomlagringApis(
                mellomlagringService = MellomlagringService(
                    RedisStore(
                        RedisConfig(
                        RedisConfigurationProperties(
                            configuration.getRedisHost().equals("localhost"))
                        ).redisClient(configuration)), configuration.getStoragePassphrase()),
                idTokenProvider = idTokenProvider
            )

            vedleggApis(
                vedleggService = vedleggService,
                idTokenProvider = idTokenProvider
            )

            ettersendingApis(
                idTokenProvider = idTokenProvider,
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
                HttpRequestHealthCheck(mapOf(
                    configuration.getJwksUrl() to HttpRequestHealthConfig(expectedStatus = HttpStatusCode.OK, includeExpectedStatusEntity = false),
                    Url.buildURL(baseUrl = configuration.getK9DokumentUrl(), pathParts = listOf("health")) to HttpRequestHealthConfig(expectedStatus = HttpStatusCode.OK),
                    Url.buildURL(baseUrl = configuration.getK9EttersendingMottakBaseUrl(), pathParts = listOf("health")) to HttpRequestHealthConfig(expectedStatus = HttpStatusCode.OK, httpHeaders = mapOf(apiGatewayApiKey.headerKey to apiGatewayApiKey.value))
                ))
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
            try { idTokenProvider.getIdToken(call).getId() }
            catch (cause: Throwable) { null }
        }
    }
}

fun StatusPages.Configuration.CustomStatusPages() {

    exception<Throwblem> { cause ->
        call.response.header("invalid-parameters", invalidParametersSomString(cause))
        call.respondProblemDetails(cause.getProblemDetails() , logger)
    }

    exception<Throwable> { cause ->
        if (cause is Problem) {
            call.response.header("invalid-parameters", invalidParametersSomString(cause as Throwblem))
            call.respondProblemDetails(cause.getProblemDetails(), logger)
        }
    }
}

private fun invalidParametersSomString(cause: Throwblem): String = cause.getProblemDetails().asMap()["invalid_parameters"].toString()

fun MicrometerMetrics.Configuration.init(
    app: String,
    collectorRegistry: CollectorRegistry = CollectorRegistry.defaultRegistry
) {
    registry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT, collectorRegistry, Clock.SYSTEM)
    timers { call, throwable ->
        tag("app", app)
        tag("result",
            when {
                throwable != null -> "failure"
                call.response.status() == null -> "failure"
                call.response.status()!!.isSuccessOrRedirect() -> "success"
                else -> "failure"
            }
        )
        val problemDetails = call.response.headers["invalid-parameters"]
        tag("invalid_parameters",
            if (problemDetails != null) {
                problemDetails
            } else "n/a"
        )
    }
}

fun StatusPages.Configuration.JacksonStatusPages() {

    exception<JsonMappingException> { cause ->
        val violations= mutableSetOf<Violation>()
        cause.path.filter { it.fieldName != null }.forEach {
            violations.add(
                Violation(
                    parameterType = ParameterType.ENTITY,
                    parameterName = it.fieldName,
                    reason = "Må være satt.",
                    invalidValue = null

                )
            )
        }

        val problemDetails = ValidationProblemDetails(violations)

        logger.debug("Feil ved mapping av JSON", cause)

        call.response.header("invalid-parameters", problemDetails.asMap()["invalid_parameters"].toString())
        call.respondProblemDetails(problemDetails, logger)
    }

    exception<JsonProcessingException> { cause ->

        val problemDetails = DefaultProblemDetails(
            title = "invalid-json-entity",
            status = 400,
            detail = "Request entityen inneholder ugyldig JSON."
        )
        logger.debug("Feil ved prosessering av JSON", cause)

        call.response.header("invalid-parameters", problemDetails.asMap()["invalid_parameters"].toString())
        call.respondProblemDetails(problemDetails, logger)
    }
}

private fun HttpStatusCode.isSuccessOrRedirect() = value in (200 until 400)

internal fun ObjectMapper.k9EttersendingKonfiguert() = dusseldorfConfigured()
    .setPropertyNamingStrategy(PropertyNamingStrategy.LOWER_CAMEL_CASE)
    .configure(SerializationFeature.WRITE_DURATIONS_AS_TIMESTAMPS, false)
