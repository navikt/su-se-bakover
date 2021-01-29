package no.nav.su.se.bakover.web

import ch.qos.logback.classic.util.ContextInitializer
import io.ktor.application.Application
import io.ktor.application.ApplicationCall
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.auth.authenticate
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
import io.ktor.request.httpMethod
import io.ktor.request.path
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.routing
import io.ktor.util.KtorExperimentalAPI
import no.nav.su.se.bakover.client.Clients
import no.nav.su.se.bakover.client.ProdClientsBuilder
import no.nav.su.se.bakover.client.StubClientsBuilder
import no.nav.su.se.bakover.common.ApplicationConfig
import no.nav.su.se.bakover.common.JmsConfig
import no.nav.su.se.bakover.common.filterMap
import no.nav.su.se.bakover.common.log
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.database.DatabaseBuilder
import no.nav.su.se.bakover.database.DatabaseRepos
import no.nav.su.se.bakover.domain.UgyldigFnrException
import no.nav.su.se.bakover.domain.behandling.Behandling
import no.nav.su.se.bakover.domain.behandling.BehandlingFactory
import no.nav.su.se.bakover.domain.behandling.BehandlingMetrics
import no.nav.su.se.bakover.domain.person.KunneIkkeHentePerson
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
import no.nav.su.se.bakover.web.routes.drift.driftRoutes
import no.nav.su.se.bakover.web.routes.installMetrics
import no.nav.su.se.bakover.web.routes.me.meRoutes
import no.nav.su.se.bakover.web.routes.naisPaths
import no.nav.su.se.bakover.web.routes.naisRoutes
import no.nav.su.se.bakover.web.routes.person.personPath
import no.nav.su.se.bakover.web.routes.person.personRoutes
import no.nav.su.se.bakover.web.routes.sak.sakRoutes
import no.nav.su.se.bakover.web.routes.søknad.søknadRoutes
import no.nav.su.se.bakover.web.routes.utbetaling.gjenoppta.gjenopptaUtbetalingRoutes
import no.nav.su.se.bakover.web.routes.utbetaling.stans.stansutbetalingRoutes
import no.nav.su.se.bakover.web.services.avstemming.AvstemmingJob
import no.nav.su.se.bakover.web.services.utbetaling.kvittering.LokalKvitteringJob
import no.nav.su.se.bakover.web.services.utbetaling.kvittering.UtbetalingKvitteringConsumer
import no.nav.su.se.bakover.web.services.utbetaling.kvittering.UtbetalingKvitteringIbmMqConsumer
import org.slf4j.event.Level
import java.time.Clock
import java.time.format.DateTimeParseException

fun main(args: Array<String>) {
    if (ApplicationConfig.isRunningLocally()) {
        System.setProperty(ContextInitializer.CONFIG_FILE_PROPERTY, "logback-local.xml")
    }
    io.ktor.server.netty.EngineMain.main(args)
}

@OptIn(io.ktor.locations.KtorExperimentalLocationsAPI::class, KtorExperimentalAPI::class)
internal fun Application.susebakover(
    clock: Clock = Clock.systemUTC(),
    behandlingMetrics: BehandlingMetrics = BehandlingMicrometerMetrics(),
    søknadMetrics: SøknadMetrics = SøknadMicrometerMetrics(),
    behandlingFactory: BehandlingFactory = BehandlingFactory(behandlingMetrics, clock),
    applicationConfig: ApplicationConfig = ApplicationConfig.createConfig(),
    databaseRepos: DatabaseRepos = DatabaseBuilder.build(behandlingFactory, applicationConfig.database),
    jmsConfig: JmsConfig = JmsConfig(applicationConfig),
    clients: Clients = if (applicationConfig.runtimeEnvironment != ApplicationConfig.RuntimeEnvironment.Nais) StubClientsBuilder.build(applicationConfig) else ProdClientsBuilder(
        jmsConfig,
        clock = clock,
    ).build(applicationConfig),
    services: Services = ServiceBuilder(
        databaseRepos = databaseRepos,
        clients = clients,
        behandlingMetrics = behandlingMetrics,
        søknadMetrics = søknadMetrics,
        clock = clock
    ).build(),
    accessCheckProxy: AccessCheckProxy = AccessCheckProxy(databaseRepos.person, services),
) {
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
        host(applicationConfig.corsAllowOrigin, listOf("http", "https"))
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
        exception<DateTimeParseException> {
            log.info("Got ${DateTimeParseException::class.simpleName} with message ${it.message}")
            call.respond(HttpStatusCode.BadRequest, ErrorJson("Ugyldig dato - datoer må være på isoformat"))
        }
        exception<Throwable> {
            log.error("Got Throwable with message=${it.message}", it)
            call.respond(HttpStatusCode.InternalServerError, ErrorJson("Ukjent feil"))
        }
    }

    val (collectorRegistry, prometheusMeterRegistry) = SuMetrics.setup()
    installMetrics(prometheusMeterRegistry)
    naisRoutes(collectorRegistry)

    configureAuthentication(clients.oauth, applicationConfig)
    val azureGroupMapper = AzureGroupMapper(applicationConfig.azure.groups)

    install(Authorization) {
        getRoller { principal ->
            getGroupsFromJWT(applicationConfig, principal)
                .filterMap { azureGroupMapper.fromAzureGroup(it) }
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
            if (call.request.httpMethod.value == "OPTIONS") return@filter false
            if (call.pathShouldBeExcluded(naisPaths)) return@filter false
            if (call.pathShouldBeExcluded(personPath)) return@filter false

            return@filter true
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
                meRoutes(applicationConfig, azureGroupMapper)

                withAccessProtectedServices(
                    accessCheckProxy
                ) { accessProtectedServices ->
                    personRoutes(accessProtectedServices.person, clock)
                    sakRoutes(accessProtectedServices.sak)
                    søknadRoutes(accessProtectedServices.søknad, accessProtectedServices.lukkSøknad)
                    behandlingRoutes(accessProtectedServices.behandling, accessProtectedServices.saksbehandling)
                    avstemmingRoutes(accessProtectedServices.avstemming)
                    stansutbetalingRoutes(accessProtectedServices.utbetaling)
                    gjenopptaUtbetalingRoutes(accessProtectedServices.utbetaling)
                    driftRoutes(accessProtectedServices.søknad, accessProtectedServices.behandling)
                }
            }
        }
    }
    val utbetalingKvitteringConsumer = UtbetalingKvitteringConsumer(
        utbetalingService = services.utbetaling,
        behandlingService = services.behandling,
        clock = clock,
    )
    if (applicationConfig.runtimeEnvironment == ApplicationConfig.RuntimeEnvironment.Nais) {
        UtbetalingKvitteringIbmMqConsumer(
            kvitteringQueueName = applicationConfig.oppdrag.utbetaling.mqReplyTo,
            globalJmsContext = jmsConfig.jmsContext,
            kvitteringConsumer = utbetalingKvitteringConsumer
        )
        AvstemmingJob(
            avstemmingService = services.avstemming,
            leaderPodLookup = clients.leaderPodLookup
        ).schedule()
    } else {
        LokalKvitteringJob(databaseRepos.utbetaling, utbetalingKvitteringConsumer).schedule()
    }
}

fun Route.withAccessProtectedServices(
    accessCheckProxy: AccessCheckProxy,
    build: Route.(services: Services) -> Unit
) = build(accessCheckProxy.proxy())

fun ApplicationCall.pathShouldBeExcluded(paths: List<String>): Boolean {
    return paths.any {
        this.request.path().startsWith(it)
    }
}

fun ApplicationCall.pathShouldBeExcluded(path: String) = pathShouldBeExcluded(listOf(path))
