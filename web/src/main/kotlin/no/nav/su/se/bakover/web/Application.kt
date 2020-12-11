package no.nav.su.se.bakover.web

import ch.qos.logback.classic.util.ContextInitializer
import com.auth0.jwk.JwkProvider
import com.auth0.jwk.JwkProviderBuilder
import com.ibm.mq.jms.MQConnectionFactory
import com.ibm.msg.client.wmq.WMQConstants
import io.ktor.application.Application
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.auth.authenticate
import io.ktor.client.HttpClient
import io.ktor.client.engine.apache.Apache
import io.ktor.features.CORS
import io.ktor.features.CallId
import io.ktor.features.CallLogging
import io.ktor.features.ContentNegotiation
import io.ktor.features.StatusPages
import io.ktor.features.XForwardedHeaderSupport
import io.ktor.features.callIdMdc
import io.ktor.features.generate
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpHeaders.WWWAuthenticate
import io.ktor.http.HttpHeaders.XCorrelationId
import io.ktor.http.HttpMethod.Companion.Options
import io.ktor.http.HttpMethod.Companion.Patch
import io.ktor.http.HttpStatusCode
import io.ktor.jackson.JacksonConverter
import io.ktor.locations.Locations
import io.ktor.request.path
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.routing
import io.ktor.util.KtorExperimentalAPI
import no.nav.su.se.bakover.client.Clients
import no.nav.su.se.bakover.client.ProdClientsBuilder
import no.nav.su.se.bakover.client.StubClientsBuilder
import no.nav.su.se.bakover.common.Config
import no.nav.su.se.bakover.common.filterMap
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.database.DatabaseBuilder
import no.nav.su.se.bakover.database.DatabaseRepos
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.domain.UgyldigFnrException
import no.nav.su.se.bakover.domain.behandling.Behandling
import no.nav.su.se.bakover.domain.behandling.BehandlingFactory
import no.nav.su.se.bakover.domain.behandling.BehandlingMetrics
import no.nav.su.se.bakover.domain.person.PersonOppslag.KunneIkkeHentePerson
import no.nav.su.se.bakover.domain.søknad.SøknadMetrics
import no.nav.su.se.bakover.service.AccessCheckProxy
import no.nav.su.se.bakover.service.ServiceBuilder
import no.nav.su.se.bakover.service.Services
import no.nav.su.se.bakover.service.Tilgangssjekkfeil
import no.nav.su.se.bakover.web.features.Authorization
import no.nav.su.se.bakover.web.features.AuthorizationException
import no.nav.su.se.bakover.web.features.FantBrukerMenManglerNAVIdent
import no.nav.su.se.bakover.web.features.IkkeInitialisert
import no.nav.su.se.bakover.web.features.KallMotMicrosoftGraphApiFeilet
import no.nav.su.se.bakover.web.features.ManglerAuthHeader
import no.nav.su.se.bakover.web.features.SuUserFeature
import no.nav.su.se.bakover.web.features.SuUserFeaturefeil
import no.nav.su.se.bakover.web.features.withUser
import no.nav.su.se.bakover.web.metrics.BehandlingMicrometerMetrics
import no.nav.su.se.bakover.web.metrics.SuMetrics
import no.nav.su.se.bakover.web.metrics.SøknadMicrometerMetrics
import no.nav.su.se.bakover.web.routes.avstemming.avstemmingRoutes
import no.nav.su.se.bakover.web.routes.behandling.behandlingRoutes
import no.nav.su.se.bakover.web.routes.installMetrics
import no.nav.su.se.bakover.web.routes.me.meRoutes
import no.nav.su.se.bakover.web.routes.naisPaths
import no.nav.su.se.bakover.web.routes.naisRoutes
import no.nav.su.se.bakover.web.routes.person.BrentFnrIOppdragPreprod
import no.nav.su.se.bakover.web.routes.person.personRoutes
import no.nav.su.se.bakover.web.routes.sak.sakRoutes
import no.nav.su.se.bakover.web.routes.søknad.søknadRoutes
import no.nav.su.se.bakover.web.routes.utbetaling.gjenoppta.gjenopptaUtbetalingRoutes
import no.nav.su.se.bakover.web.routes.utbetaling.stans.stansutbetalingRoutes
import no.nav.su.se.bakover.web.services.avstemming.AvstemmingJob
import no.nav.su.se.bakover.web.services.utbetaling.kvittering.UtbetalingKvitteringConsumer
import no.nav.su.se.bakover.web.services.utbetaling.kvittering.UtbetalingKvitteringIbmMqConsumer
import org.json.JSONObject
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.slf4j.event.Level
import java.net.URL
import javax.jms.JMSContext

fun main(args: Array<String>) {
    Config.init()
    if (Config.isLocalOrRunningTests) {
        System.setProperty(ContextInitializer.CONFIG_FILE_PROPERTY, "logback-local.xml")
    }
    io.ktor.server.netty.EngineMain.main(args)
}

private val jmsContext: JMSContext by lazy {
    MQConnectionFactory().apply {
        Config.oppdrag.let {
            hostName = it.mqHostname
            port = it.mqPort
            channel = it.mqChannel
            queueManager = it.mqQueueManager
            transportType = WMQConstants.WMQ_CM_CLIENT
        }
    }.createContext(Config.serviceUser.username, Config.serviceUser.password)
}

@OptIn(io.ktor.locations.KtorExperimentalLocationsAPI::class, KtorExperimentalAPI::class)
internal fun Application.susebakover(
    behandlingMetrics: BehandlingMetrics = BehandlingMicrometerMetrics(),
    søknadMetrics: SøknadMetrics = SøknadMicrometerMetrics(),
    behandlingFactory: BehandlingFactory = BehandlingFactory(behandlingMetrics),
    databaseRepos: DatabaseRepos = DatabaseBuilder.build(behandlingFactory),
    clients: Clients = if (Config.isLocalOrRunningTests) StubClientsBuilder.build() else ProdClientsBuilder(jmsContext).build(),
    jwkConfig: JSONObject = clients.oauth.jwkConfig(),
    jwkProvider: JwkProvider = JwkProviderBuilder(URL(jwkConfig.getString("jwks_uri"))).build(),
    authenticationHttpClient: HttpClient = HttpClient(Apache) {
        engine {
            customizeClient {
                useSystemProperties()
            }
        }
    },
    services: Services = ServiceBuilder(databaseRepos, clients, behandlingMetrics, søknadMetrics).build()
) {
    // Application er allerede reservert av Ktor
    val log: Logger = LoggerFactory.getLogger("su-se-bakover")

    install(CORS) {
        method(Options)
        method(Patch)
        header(HttpHeaders.Authorization)
        header("refresh_token")
        header(XCorrelationId)
        allowNonSimpleContentTypes = true
        exposeHeader(WWWAuthenticate)
        exposeHeader("access_token")
        exposeHeader("refresh_token")
        host(Config.corsAllowOrigin, listOf("http", "https"))
    }

    install(StatusPages) {
        exception<Tilgangssjekkfeil> {
            when (it.feil) {
                is KunneIkkeHentePerson.IkkeTilgangTilPerson -> {
                    call.audit("[Tilgangssjekk] Bruker har ikke tilgang til person ${it.fnr}")
                    log.warn("[Tilgangssjekk] Bruker har ikke tilgang til person.", it)
                    call.respond(HttpStatusCode.Forbidden, ErrorJson("Ikke tilgang til å se person"))
                }
                is KunneIkkeHentePerson.FantIkkePerson -> {
                    log.warn("[Tilgangssjekk] Fant ikke person", it)
                    call.respond(HttpStatusCode.NotFound, ErrorJson("Fant ikke person"))
                }
                is KunneIkkeHentePerson.Ukjent -> {
                    log.warn("[Tilgangssjekk] Feil ved oppslag på person", it)
                    call.respond(HttpStatusCode.InternalServerError, ErrorJson("Feil ved oppslag på person"))
                }
            }
        }

        exception<SuUserFeaturefeil> {
            log.error("Got SuUserFeaturefeil with message=${it.message}", it)
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorJson(
                    when (it) {
                        is KallMotMicrosoftGraphApiFeilet ->
                            "Kunne ikke hente informasjon om innlogget bruker"
                        is ManglerAuthHeader, IkkeInitialisert, FantBrukerMenManglerNAVIdent ->
                            "En feil oppstod"
                    }
                )
            )
        }
        exception<AuthorizationException> {
            call.respond(HttpStatusCode.Forbidden, ErrorJson(it.message))
        }
        exception<UgyldigFnrException> {
            log.warn("Got UgyldigFnrException with message=${it.message}", it)
            call.respond(HttpStatusCode.BadRequest, ErrorJson(it.message ?: "Ugyldig fødselsnummer"))
        }
        exception<Behandling.TilstandException> {
            log.info("Got ${Behandling.TilstandException::class.simpleName} with message=${it.msg}")
            call.respond(HttpStatusCode.BadRequest, ErrorJson(it.msg))
        }
        exception<BrentFnrIOppdragPreprod> {
            // Antar at det er trygt å logge disse siden det bare kan skje i preprod.
            log.warn("Det ble benyttet et fødselsnummer ${it.fnr} som er \"brent\" i Oppdrag/Økonomi. Anbefaler å slette Dolly-brukeren.")
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorJson("Fødselsnummeret ${it.fnr} kan ikke brukes lenger mot Oppdrag/Økonomi. Anbefaler å slette dolly-brukeren.")
            )
        }
        exception<Throwable> {
            log.error("Got Throwable with message=${it.message}", it)
            call.respond(HttpStatusCode.InternalServerError, ErrorJson("Ukjent feil"))
        }
    }

    val (collectorRegistry, prometheusMeterRegistry) = SuMetrics.setup()
    installMetrics(prometheusMeterRegistry)
    naisRoutes(collectorRegistry)

    setupAuthentication(
        jwkConfig = jwkConfig,
        jwkProvider = jwkProvider,
        httpClient = authenticationHttpClient
    )
    oauthRoutes(
        frontendRedirectUrl = Config.suSeFramoverLoginSuccessUrl,
        jwkConfig = jwkConfig,
        oAuth = clients.oauth,
    )

    install(Authorization) {
        getRoller { principal ->
            getGroupsFromJWT(principal)
                .filterMap { Brukerrolle.fromAzureGroup(it) }
                .toSet()
        }
    }

    install(Locations)

    install(ContentNegotiation) {
        register(ContentType.Application.Json, JacksonConverter(objectMapper))
    }

    install(CallId) {
        header(XCorrelationId)
        generate(17)
        verify { it.isNotEmpty() }
    }
    install(CallLogging) {
        level = Level.INFO
        filter { call ->
            val path = call.request.path()
            (naisPaths + AUTH_CALLBACK_PATH).none {
                path.startsWith(it)
            }
        }
        callIdMdc("X-Correlation-ID")

        mdc("Authorization") { it.authHeader() }
    }

    install(XForwardedHeaderSupport)

    install(SuUserFeature) {
        this.clients = clients
    }

    routing {
        authenticate("jwt") {
            withUser {
                meRoutes()
                personRoutes(clients.personOppslag)

                withAccessProtectedServices(
                    services,
                    AccessCheckProxy(databaseRepos.person, clients)
                ) { accessProtectedServices ->
                    sakRoutes(accessProtectedServices.sak)
                    søknadRoutes(accessProtectedServices.søknad, accessProtectedServices.lukkSøknad)
                    behandlingRoutes(accessProtectedServices.behandling)
                    avstemmingRoutes(accessProtectedServices.avstemming)
                    stansutbetalingRoutes(accessProtectedServices.utbetaling)
                    gjenopptaUtbetalingRoutes(accessProtectedServices.utbetaling)
                }
            }
        }
    }
    if (!Config.isLocalOrRunningTests) {
        UtbetalingKvitteringIbmMqConsumer(
            kvitteringQueueName = Config.oppdrag.utbetaling.mqReplyTo,
            globalJmsContext = jmsContext,
            kvitteringConsumer = UtbetalingKvitteringConsumer(
                utbetalingService = services.utbetaling
            )
        )
        if (System.getenv("NAIS_CLUSTER_NAME") == "prod-fss") {
            LoggerFactory.getLogger("su-se-bakover")
                .warn("AvstemmingJob er deaktivert inntil oppdrag har prodsatt endringer for SUUFORE")
        } else {
            AvstemmingJob(
                avstemmingService = services.avstemming,
                leaderPodLookup = clients.leaderPodLookup
            ).schedule()
        }
    }
}

fun Route.withAccessProtectedServices(
    services: Services,
    accessCheckProxy: AccessCheckProxy,
    build: Route.(services: Services) -> Unit
) = build(accessCheckProxy.proxy(services))
