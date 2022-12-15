package no.nav.su.se.bakover.web

import ch.qos.logback.classic.ClassicConstants
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders.XCorrelationId
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.serialization.jackson.JacksonConverter
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.auth.authenticate
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.callid.CallId
import io.ktor.server.plugins.callid.callIdMdc
import io.ktor.server.plugins.callid.generate
import io.ktor.server.plugins.callloging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.forwardedheaders.XForwardedHeaders
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.routing.Route
import io.ktor.server.routing.routing
import no.finn.unleash.Unleash
import no.nav.su.se.bakover.client.Clients
import no.nav.su.se.bakover.client.ProdClientsBuilder
import no.nav.su.se.bakover.client.StubClientsBuilder
import no.nav.su.se.bakover.client.UnleashBuilder
import no.nav.su.se.bakover.common.ApplicationConfig
import no.nav.su.se.bakover.common.CorrelationIdHeader
import no.nav.su.se.bakover.common.JmsConfig
import no.nav.su.se.bakover.common.UgyldigFnrException
import no.nav.su.se.bakover.common.audit.infrastructure.CefAuditLogger
import no.nav.su.se.bakover.common.infrastructure.web.AzureGroupMapper
import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser
import no.nav.su.se.bakover.common.infrastructure.web.authHeader
import no.nav.su.se.bakover.common.infrastructure.web.errorJson
import no.nav.su.se.bakover.common.infrastructure.web.sikkerlogg
import no.nav.su.se.bakover.common.infrastructure.web.svar
import no.nav.su.se.bakover.common.infrastructure.web.withUser
import no.nav.su.se.bakover.common.log
import no.nav.su.se.bakover.common.metrics.SuMetrics
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.common.persistence.DbMetrics
import no.nav.su.se.bakover.database.DatabaseBuilder
import no.nav.su.se.bakover.database.DomainToQueryParameterMapper
import no.nav.su.se.bakover.domain.DatabaseRepos
import no.nav.su.se.bakover.domain.behandling.BehandlingMetrics
import no.nav.su.se.bakover.domain.metrics.ClientMetrics
import no.nav.su.se.bakover.domain.person.KunneIkkeHentePerson
import no.nav.su.se.bakover.domain.satser.SatsFactoryForSupplerendeStønad
import no.nav.su.se.bakover.domain.søknad.SøknadMetrics
import no.nav.su.se.bakover.domain.søknadsbehandling.StatusovergangVisitor
import no.nav.su.se.bakover.kontrollsamtale.infrastructure.web.kontrollsamtaleRoutes
import no.nav.su.se.bakover.utenlandsopphold.application.annuller.AnnullerUtenlandsoppholdService
import no.nav.su.se.bakover.utenlandsopphold.application.korriger.KorrigerUtenlandsoppholdService
import no.nav.su.se.bakover.utenlandsopphold.application.registrer.RegistrerUtenlandsoppholdService
import no.nav.su.se.bakover.utenlandsopphold.infrastruture.web.utenlandsoppholdRoutes
import no.nav.su.se.bakover.web.external.frikortVedtakRoutes
import no.nav.su.se.bakover.web.metrics.BehandlingMicrometerMetrics
import no.nav.su.se.bakover.web.metrics.DbMicrometerMetrics
import no.nav.su.se.bakover.web.metrics.JournalpostClientMicrometerMetrics
import no.nav.su.se.bakover.web.metrics.SøknadMicrometerMetrics
import no.nav.su.se.bakover.web.routes.avstemming.avstemmingRoutes
import no.nav.su.se.bakover.web.routes.dokument.dokumentRoutes
import no.nav.su.se.bakover.web.routes.drift.driftRoutes
import no.nav.su.se.bakover.web.routes.installMetrics
import no.nav.su.se.bakover.web.routes.klage.klageRoutes
import no.nav.su.se.bakover.web.routes.me.meRoutes
import no.nav.su.se.bakover.web.routes.naisPaths
import no.nav.su.se.bakover.web.routes.naisRoutes
import no.nav.su.se.bakover.web.routes.nøkkeltall.nøkkeltallRoutes
import no.nav.su.se.bakover.web.routes.person.personRoutes
import no.nav.su.se.bakover.web.routes.regulering.reguleringRoutes
import no.nav.su.se.bakover.web.routes.revurdering.leggTilBrevvalgRevurderingRoute
import no.nav.su.se.bakover.web.routes.revurdering.revurderingRoutes
import no.nav.su.se.bakover.web.routes.sak.sakRoutes
import no.nav.su.se.bakover.web.routes.skatt.skattRoutes
import no.nav.su.se.bakover.web.routes.søknad.søknadRoutes
import no.nav.su.se.bakover.web.routes.søknadsbehandling.overordnetSøknadsbehandligRoutes
import no.nav.su.se.bakover.web.routes.togglePaths
import no.nav.su.se.bakover.web.routes.toggleRoutes
import no.nav.su.se.bakover.web.routes.vilkår.opplysningsplikt.opplysningspliktRoutes
import no.nav.su.se.bakover.web.services.AccessCheckProxy
import no.nav.su.se.bakover.web.services.ServiceBuilder
import no.nav.su.se.bakover.web.services.Services
import no.nav.su.se.bakover.web.services.Tilgangssjekkfeil
import no.nav.su.se.bakover.web.services.tilbakekreving.TilbakekrevingConsumer
import no.nav.su.se.bakover.web.services.utbetaling.kvittering.UtbetalingKvitteringConsumer
import org.slf4j.event.Level
import java.time.Clock
import java.time.LocalDate
import java.time.format.DateTimeParseException

fun main() {
    if (ApplicationConfig.isRunningLocally()) {
        System.setProperty(ClassicConstants.CONFIG_FILE_PROPERTY, "logback-local.xml")
    }
    embeddedServer(factory = Netty, port = 8080) {
        susebakover()
    }.start(true)
}

fun Application.susebakover(
    clock: Clock = Clock.systemUTC(),
    behandlingMetrics: BehandlingMetrics = BehandlingMicrometerMetrics(),
    søknadMetrics: SøknadMetrics = SøknadMicrometerMetrics(),
    clientMetrics: ClientMetrics = ClientMetrics(
        journalpostClientMetrics = JournalpostClientMicrometerMetrics(),
    ),
    dbMetrics: DbMetrics = DbMicrometerMetrics(),
    applicationConfig: ApplicationConfig = ApplicationConfig.createConfig(),
    unleash: Unleash = UnleashBuilder.build(applicationConfig),
    satsFactory: SatsFactoryForSupplerendeStønad = SatsFactoryForSupplerendeStønad(),
    databaseRepos: DatabaseRepos = DatabaseBuilder.build(
        databaseConfig = applicationConfig.database,
        dbMetrics = dbMetrics,
        clock = clock,
        satsFactory = satsFactory,
        queryParameterMappers = listOf(DomainToQueryParameterMapper),
    ),
    jmsConfig: JmsConfig = JmsConfig(applicationConfig),
    clients: Clients = if (applicationConfig.runtimeEnvironment != ApplicationConfig.RuntimeEnvironment.Nais) {
        StubClientsBuilder(
            clock = clock,
            databaseRepos = databaseRepos,
        ).build(applicationConfig)
    } else {
        ProdClientsBuilder(
            jmsConfig,
            clock = clock,
            unleash = unleash,
            metrics = clientMetrics,
        ).build(applicationConfig)
    },
    services: Services = ServiceBuilder.build(
        databaseRepos = databaseRepos,
        clients = clients,
        behandlingMetrics = behandlingMetrics,
        søknadMetrics = søknadMetrics,
        clock = clock,
        unleash = unleash,
        satsFactory = satsFactory.gjeldende(LocalDate.now(clock)),
        applicationConfig = applicationConfig,
        dbMetrics = dbMetrics,
    ),
    accessCheckProxy: AccessCheckProxy = AccessCheckProxy(databaseRepos.person, services),
    consumers: Consumers = Consumers(
        tilbakekrevingConsumer = TilbakekrevingConsumer(
            tilbakekrevingService = services.tilbakekrevingService,
            revurderingService = services.revurdering,
            clock = clock,
        ),
        utbetalingKvitteringConsumer = UtbetalingKvitteringConsumer(
            utbetalingService = services.utbetaling,
            ferdigstillVedtakService = services.ferdigstillVedtak,
            clock = clock,
        ),
    ),
) {
    val satsFactoryIDag = satsFactory.gjeldende(LocalDate.now(clock))
    install(StatusPages) {
        exception<Tilgangssjekkfeil> { call, cause ->
            when (cause.feil) {
                is KunneIkkeHentePerson.IkkeTilgangTilPerson -> {
                    call.sikkerlogg("slo opp person hen ikke har tilgang til")
                    log.warn("[Tilgangssjekk] Ikke tilgang til person.", cause)
                    call.svar(Feilresponser.ikkeTilgangTilPerson)
                }

                is KunneIkkeHentePerson.FantIkkePerson -> {
                    log.warn("[Tilgangssjekk] Fant ikke person", cause)
                    call.svar(Feilresponser.fantIkkePerson)
                }

                is KunneIkkeHentePerson.Ukjent -> {
                    log.warn("[Tilgangssjekk] Feil ved oppslag på person", cause)
                    call.svar(Feilresponser.feilVedOppslagPåPerson)
                }
            }
        }
        exception<UgyldigFnrException> { call, cause ->
            log.warn("Got UgyldigFnrException with message=${cause.message}", cause)
            call.svar(
                BadRequest.errorJson(
                    message = cause.message ?: "Ugyldig fødselsnummer",
                    code = "ugyldig_fødselsnummer",
                ),
            )
        }
        exception<StatusovergangVisitor.UgyldigStatusovergangException> { call, cause ->
            log.info("Got ${StatusovergangVisitor.UgyldigStatusovergangException::class.simpleName} with message=${cause.msg}")
            call.svar(
                BadRequest.errorJson(
                    message = cause.msg,
                    code = "ugyldig_statusovergang",
                ),
            )
        }
        exception<DateTimeParseException> { call, cause ->
            log.info("Got ${DateTimeParseException::class.simpleName} with message ${cause.message}")
            call.svar(
                BadRequest.errorJson(
                    message = "Ugyldig dato - datoer må være på isoformat",
                    code = "ugyldig_dato",
                ),
            )
        }
        exception<Throwable> { call, cause ->
            log.error("Got Throwable with message=${cause.message}", cause)
            call.svar(Feilresponser.ukjentFeil)
        }
    }

    val (collectorRegistry, prometheusMeterRegistry) = SuMetrics.setup()
    installMetrics(prometheusMeterRegistry)
    naisRoutes(collectorRegistry)

    configureAuthentication(clients.oauth, applicationConfig, clients.tokenOppslag)
    val azureGroupMapper = AzureGroupMapper(applicationConfig.azure.groups)

    install(ContentNegotiation) {
        register(ContentType.Application.Json, JacksonConverter(objectMapper))
    }

    install(CallId) {
        header(XCorrelationId)
        this.generate(length = 17)
        verify { it.isNotEmpty() }
    }
    install(CallLogging) {
        level = Level.INFO
        filter { call ->
            if (call.request.httpMethod.value == "OPTIONS") return@filter false
            if (call.pathShouldBeExcluded(naisPaths)) return@filter false
            if (call.pathShouldBeExcluded(togglePaths)) return@filter false

            return@filter true
        }
        callIdMdc(CorrelationIdHeader)

        mdc("Authorization") { it.authHeader() }
        disableDefaultColors()
    }

    install(XForwardedHeaders)

    routing {
        toggleRoutes(services.toggles)

        authenticate("frikort") {
            frikortVedtakRoutes(services.vedtakService, clock)
        }

        authenticate("jwt") {
            withUser(applicationConfig) {
                meRoutes(applicationConfig, azureGroupMapper)

                withAccessProtectedServices(
                    accessCheckProxy,
                ) { accessProtectedServices ->
                    personRoutes(accessProtectedServices.person, clock)
                    sakRoutes(accessProtectedServices.sak, clock, satsFactoryIDag)
                    søknadRoutes(
                        søknadService = accessProtectedServices.søknad,
                        lukkSøknadService = accessProtectedServices.lukkSøknad,
                        avslåSøknadManglendeDokumentasjonService = accessProtectedServices.avslåSøknadManglendeDokumentasjonService,
                        clock = clock,
                        satsFactoryIDag,
                    )
                    overordnetSøknadsbehandligRoutes(accessProtectedServices.søknadsbehandling, clock, satsFactoryIDag)
                    avstemmingRoutes(accessProtectedServices.avstemming, clock)
                    driftRoutes(accessProtectedServices.søknad)
                    revurderingRoutes(
                        revurderingService = accessProtectedServices.revurdering,
                        sakService = accessProtectedServices.sak,
                        clock = clock,
                        satsFactory = satsFactoryIDag,
                        stansAvYtelseService = accessProtectedServices.stansYtelse,
                        gjenopptakAvYtelseService = accessProtectedServices.gjenopptaYtelse,
                    )
                    klageRoutes(accessProtectedServices.klageService, clock)
                    dokumentRoutes(accessProtectedServices.brev)
                    nøkkeltallRoutes(accessProtectedServices.nøkkeltallService)
                    kontrollsamtaleRoutes(accessProtectedServices.kontrollsamtaleSetup.kontrollsamtaleService)
                    reguleringRoutes(accessProtectedServices.reguleringService, satsFactoryIDag, clock)
                    opplysningspliktRoutes(
                        søknadsbehandlingService = accessProtectedServices.søknadsbehandling.søknadsbehandlingService,
                        revurderingService = accessProtectedServices.revurdering,
                        satsFactory = satsFactoryIDag,
                        clock = clock,
                    )
                    skattRoutes(accessProtectedServices.skatteService, accessProtectedServices.toggles)
                    utenlandsoppholdRoutes(
                        registerService = RegistrerUtenlandsoppholdService(
                            sakRepo = databaseRepos.sak,
                            utenlandsoppholdRepo = databaseRepos.utenlandsoppholdRepo,
                            journalpostClient = clients.journalpostClient,
                            auditLogger = CefAuditLogger,
                            personService = services.person,
                        ),
                        korrigerService = KorrigerUtenlandsoppholdService(
                            sakRepo = databaseRepos.sak,
                            utenlandsoppholdRepo = databaseRepos.utenlandsoppholdRepo,
                            journalpostClient = clients.journalpostClient,
                            auditLogger = CefAuditLogger,
                            personService = services.person,
                        ),
                        annullerService = AnnullerUtenlandsoppholdService(
                            sakRepo = databaseRepos.sak,
                            utenlandsoppholdRepo = databaseRepos.utenlandsoppholdRepo,
                            auditLogger = CefAuditLogger,
                            personService = services.person,
                        ),
                    )
                    leggTilBrevvalgRevurderingRoute(
                        revurderingService = accessProtectedServices.revurdering,
                        satsFactory = satsFactoryIDag,
                    )
                    kontrollsamtaleRoutes(
                        kontrollsamtaleService = services.kontrollsamtaleSetup.kontrollsamtaleService,
                    )
                }
            }
        }
    }
    startJobberOgConsumers(
        services = services,
        clients = clients,
        databaseRepos = databaseRepos,
        applicationConfig = applicationConfig,
        jmsConfig = jmsConfig,
        clock = clock,
        consumers = consumers,
    )
}

fun Route.withAccessProtectedServices(
    accessCheckProxy: AccessCheckProxy,
    build: Route.(services: Services) -> Unit,
) = build(accessCheckProxy.proxy())

fun ApplicationCall.pathShouldBeExcluded(paths: List<String>): Boolean {
    return paths.any {
        this.request.path().startsWith(it)
    }
}
