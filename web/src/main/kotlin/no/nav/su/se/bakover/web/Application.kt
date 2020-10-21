package no.nav.su.se.bakover.web

import SuMetrics
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
import no.nav.su.se.bakover.domain.Behandling
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.domain.UgyldigFnrException
import no.nav.su.se.bakover.service.ServiceBuilder
import no.nav.su.se.bakover.service.Services
import no.nav.su.se.bakover.service.brev.BrevService
import no.nav.su.se.bakover.web.features.Authorization
import no.nav.su.se.bakover.web.features.AuthorizationException
import no.nav.su.se.bakover.web.features.FantBrukerMenManglerNAVIdent
import no.nav.su.se.bakover.web.features.IkkeInitialisert
import no.nav.su.se.bakover.web.features.KallMotMicrosoftGraphApiFeilet
import no.nav.su.se.bakover.web.features.ManglerAuthHeader
import no.nav.su.se.bakover.web.features.SuUserFeature
import no.nav.su.se.bakover.web.features.SuUserFeaturefeil
import no.nav.su.se.bakover.web.features.withUser
import no.nav.su.se.bakover.web.routes.avstemming.avstemmingRoutes
import no.nav.su.se.bakover.web.routes.behandling.behandlingRoutes
import no.nav.su.se.bakover.web.routes.inntektRoutes
import no.nav.su.se.bakover.web.routes.installMetrics
import no.nav.su.se.bakover.web.routes.me.meRoutes
import no.nav.su.se.bakover.web.routes.naisPaths
import no.nav.su.se.bakover.web.routes.naisRoutes
import no.nav.su.se.bakover.web.routes.personRoutes
import no.nav.su.se.bakover.web.routes.sak.sakRoutes
import no.nav.su.se.bakover.web.routes.søknad.SøknadRouteMediator
import no.nav.su.se.bakover.web.routes.søknad.søknadRoutes
import no.nav.su.se.bakover.web.routes.utbetaling.gjenoppta.gjenopptaUtbetalingRoutes
import no.nav.su.se.bakover.web.routes.utbetaling.stans.stansutbetalingRoutes
import no.nav.su.se.bakover.web.services.utbetaling.kvittering.AvstemmingKvitteringIbmMqConsumer
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
    databaseRepos: DatabaseRepos = DatabaseBuilder.build(),
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
    services: Services = ServiceBuilder(databaseRepos, clients).build()
) {
    // Application er allerede reservert av Ktor
    val log: Logger = LoggerFactory.getLogger("su-se-bakover")

    val søknadRoutesMediator = SøknadRouteMediator(
        pdfGenerator = clients.pdfGenerator,
        dokArkiv = clients.dokArkiv,
        oppgaveClient = clients.oppgaveClient,
        personOppslag = clients.personOppslag,
        søknadService = services.søknad,
        sakService = services.sak
    )

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
            log.error("Got authorizationException with message=${it.message}", it)
            call.respond(HttpStatusCode.Forbidden, ErrorJson(it.message))
        }
        exception<UgyldigFnrException> {
            log.error("Got UgyldigFnrException with message=${it.message}", it)
            call.respond(HttpStatusCode.BadRequest, ErrorJson(it.message ?: "Ugyldig fødselsnummer"))
        }
        exception<Behandling.TilstandException> {
            log.info("Got ${Behandling.TilstandException::class.simpleName} with message=${it.msg}")
            call.respond(HttpStatusCode.BadRequest, ErrorJson(it.msg))
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
                personRoutes(clients.personOppslag)
                inntektRoutes(clients.inntektOppslag)
                sakRoutes(
                    behandlingService = services.behandling,
                    sakService = services.sak
                )
                søknadRoutes(søknadRoutesMediator, services.søknad)
                behandlingRoutes(
                    brevService = BrevService(
                        pdfGenerator = clients.pdfGenerator,
                        personOppslag = clients.personOppslag,
                        dokArkiv = clients.dokArkiv,
                        dokDistFordeling = clients.dokDistFordeling,
                        sakService = services.sak
                    ),
                    behandlingService = services.behandling,
                    sakService = services.sak
                )
                avstemmingRoutes(services.avstemming)
                stansutbetalingRoutes(services.utbetaling)
                gjenopptaUtbetalingRoutes(services.utbetaling)
                meRoutes()
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
        AvstemmingKvitteringIbmMqConsumer(
            kvitteringQueueName = Config.oppdrag.avstemming.mqReplyTo,
            globalJmxContext = jmsContext
        )
    }
}
