package no.nav.su.se.bakover.web

import SuMetrics
import com.auth0.jwk.JwkProvider
import com.auth0.jwk.JwkProviderBuilder
import com.ibm.mq.jms.MQConnectionFactory
import com.ibm.msg.client.wmq.WMQConstants
import io.ktor.application.Application
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.application.log
import io.ktor.auth.authenticate
import io.ktor.auth.authentication
import io.ktor.auth.jwt.JWTPrincipal
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
import io.ktor.http.HttpHeaders.Authorization
import io.ktor.http.HttpHeaders.WWWAuthenticate
import io.ktor.http.HttpHeaders.XCorrelationId
import io.ktor.http.HttpMethod.Companion.Options
import io.ktor.http.HttpMethod.Companion.Patch
import io.ktor.http.HttpStatusCode
import io.ktor.jackson.JacksonConverter
import io.ktor.locations.Locations
import io.ktor.request.path
import io.ktor.response.respond
import io.ktor.routing.get
import io.ktor.routing.routing
import no.nav.su.se.bakover.client.Clients
import no.nav.su.se.bakover.client.ProdClientsBuilder
import no.nav.su.se.bakover.client.StubClientsBuilder
import no.nav.su.se.bakover.common.Config
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.database.DatabaseBuilder
import no.nav.su.se.bakover.database.ObjectRepo
import no.nav.su.se.bakover.domain.Behandling
import no.nav.su.se.bakover.domain.UgyldigFnrException
import no.nav.su.se.bakover.web.routes.avstemmingRoutes
import no.nav.su.se.bakover.web.routes.behandling.behandlingRoutes
import no.nav.su.se.bakover.web.routes.inntektRoutes
import no.nav.su.se.bakover.web.routes.installMetrics
import no.nav.su.se.bakover.web.routes.naisPaths
import no.nav.su.se.bakover.web.routes.naisRoutes
import no.nav.su.se.bakover.web.routes.personRoutes
import no.nav.su.se.bakover.web.routes.sak.sakRoutes
import no.nav.su.se.bakover.web.routes.søknad.SøknadRouteMediator
import no.nav.su.se.bakover.web.routes.søknad.søknadRoutes
import no.nav.su.se.bakover.web.routes.vilkårsvurdering.vilkårsvurderingRoutes
import no.nav.su.se.bakover.web.services.brev.BrevService
import no.nav.su.se.bakover.web.services.utbetaling.kvittering.AvstemmingKvitteringIbmMqConsumer
import no.nav.su.se.bakover.web.services.utbetaling.kvittering.UtbetalingKvitteringConsumer
import no.nav.su.se.bakover.web.services.utbetaling.kvittering.UtbetalingKvitteringIbmMqConsumer
import org.json.JSONObject
import org.slf4j.event.Level
import java.net.URL
import javax.jms.JMSContext

fun main(args: Array<String>) {
    Config.init()
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

@OptIn(io.ktor.locations.KtorExperimentalLocationsAPI::class)
internal fun Application.susebakover(
    databaseRepo: ObjectRepo = DatabaseBuilder.build(),
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
) {
    val søknadRoutesMediator = SøknadRouteMediator(
        repo = databaseRepo,
        pdfGenerator = clients.pdfGenerator,
        dokArkiv = clients.dokArkiv,
        oppgaveClient = clients.oppgaveClient,
        personOppslag = clients.personOppslag
    )

    install(CORS) {
        method(Options)
        method(Patch)
        header(Authorization)
        header("refresh_token")
        header(XCorrelationId)
        allowNonSimpleContentTypes = true
        exposeHeader(WWWAuthenticate)
        exposeHeader("access_token")
        exposeHeader("refresh_token")
        host(Config.corsAllowOrigin, listOf("http", "https"))
    }

    install(StatusPages) {
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
        frontendRedirectUrl = Config.suSeFramoverRedirectUrl,
        oAuth = clients.oauth
    )

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

    routing {
        authenticate("jwt") {
            get(path = "/authenticated") {
                val principal = (call.authentication.principal as JWTPrincipal).payload
                call.respond(
                    """
                    {
                        "data": "Congrats ${
                    principal.getClaim("name")
                        .asString()
                    }, you are successfully authenticated with a JWT token"
                    }
                    """.trimIndent()
                )
            }
            personRoutes(clients.personOppslag)
            inntektRoutes(clients.inntektOppslag)
            sakRoutes(databaseRepo)
            søknadRoutes(søknadRoutesMediator)
            behandlingRoutes(
                repo = databaseRepo,
                brevService = BrevService(
                    pdfGenerator = clients.pdfGenerator,
                    personOppslag = clients.personOppslag,
                    dokArkiv = clients.dokArkiv,
                    dokDistFordeling = clients.dokDistFordeling
                ),
                simuleringClient = clients.simuleringClient,
                personOppslag = clients.personOppslag,
                oppgaveClient = clients.oppgaveClient,
                utbetalingPublisher = clients.utbetalingPublisher,
            )
            vilkårsvurderingRoutes(databaseRepo)
            avstemmingRoutes(databaseRepo, clients.avstemmingPublisher)
        }
    }
    if (!Config.isLocalOrRunningTests) {
        UtbetalingKvitteringIbmMqConsumer(
            kvitteringQueueName = Config.oppdrag.utbetaling.mqReplyTo,
            jmsContext = jmsContext,
            kvitteringConsumer = UtbetalingKvitteringConsumer(
                repo = databaseRepo
            )
        )
        AvstemmingKvitteringIbmMqConsumer(
            kvitteringQueueName = Config.oppdrag.avstemming.mqReplyTo,
            jmsContext = jmsContext
        )
        jmsContext.start()
    }
}
