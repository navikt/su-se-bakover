package no.nav.su.se.bakover.web

import ch.qos.logback.classic.util.ContextInitializer
import io.ktor.application.Application
import io.ktor.application.ApplicationCall
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.auth.authenticate
import io.ktor.features.CallId
import io.ktor.features.CallLogging
import io.ktor.features.ContentNegotiation
import io.ktor.features.StatusPages
import io.ktor.features.XForwardedHeaderSupport
import io.ktor.features.callIdMdc
import io.ktor.features.generate
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders.XCorrelationId
import io.ktor.http.HttpStatusCode
import io.ktor.jackson.JacksonConverter
import io.ktor.locations.Locations
import io.ktor.request.httpMethod
import io.ktor.request.path
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.routing
import no.finn.unleash.Unleash
import no.nav.su.se.bakover.client.Clients
import no.nav.su.se.bakover.client.ProdClientsBuilder
import no.nav.su.se.bakover.client.StubClientsBuilder
import no.nav.su.se.bakover.client.UnleashBuilder
import no.nav.su.se.bakover.common.ApplicationConfig
import no.nav.su.se.bakover.common.JmsConfig
import no.nav.su.se.bakover.common.log
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.database.DatabaseBuilder
import no.nav.su.se.bakover.domain.DatabaseRepos
import no.nav.su.se.bakover.domain.UgyldigFnrException
import no.nav.su.se.bakover.domain.behandling.BehandlingMetrics
import no.nav.su.se.bakover.domain.person.KunneIkkeHentePerson
import no.nav.su.se.bakover.domain.søknad.SøknadMetrics
import no.nav.su.se.bakover.domain.søknadsbehandling.StatusovergangVisitor
import no.nav.su.se.bakover.service.AccessCheckProxy
import no.nav.su.se.bakover.service.ServiceBuilder
import no.nav.su.se.bakover.service.Services
import no.nav.su.se.bakover.service.Tilgangssjekkfeil
import no.nav.su.se.bakover.service.personhendelser.PersonhendelseService
import no.nav.su.se.bakover.web.external.frikortVedtakRoutes
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
import no.nav.su.se.bakover.web.metrics.DbMicrometerMetrics
import no.nav.su.se.bakover.web.metrics.SuMetrics
import no.nav.su.se.bakover.web.metrics.SøknadMicrometerMetrics
import no.nav.su.se.bakover.web.routes.Feilresponser
import no.nav.su.se.bakover.web.routes.avstemming.avstemmingRoutes
import no.nav.su.se.bakover.web.routes.dokument.dokumentRoutes
import no.nav.su.se.bakover.web.routes.drift.driftRoutes
import no.nav.su.se.bakover.web.routes.installMetrics
import no.nav.su.se.bakover.web.routes.klage.klageRoutes
import no.nav.su.se.bakover.web.routes.kontrollsamtale.kontrollsamtaleRoutes
import no.nav.su.se.bakover.web.routes.me.meRoutes
import no.nav.su.se.bakover.web.routes.naisPaths
import no.nav.su.se.bakover.web.routes.naisRoutes
import no.nav.su.se.bakover.web.routes.nøkkeltall.nøkkeltallRoutes
import no.nav.su.se.bakover.web.routes.person.personRoutes
import no.nav.su.se.bakover.web.routes.regulering.reguleringRoutes
import no.nav.su.se.bakover.web.routes.revurdering.revurderingRoutes
import no.nav.su.se.bakover.web.routes.sak.sakRoutes
import no.nav.su.se.bakover.web.routes.søknad.søknadRoutes
import no.nav.su.se.bakover.web.routes.søknadsbehandling.overordnetSøknadsbehandligRoutes
import no.nav.su.se.bakover.web.routes.togglePaths
import no.nav.su.se.bakover.web.routes.toggleRoutes
import no.nav.su.se.bakover.web.services.avstemming.GrensesnittsavstemingJob
import no.nav.su.se.bakover.web.services.avstemming.KonsistensavstemmingJob
import no.nav.su.se.bakover.web.services.dokument.DistribuerDokumentJob
import no.nav.su.se.bakover.web.services.klage.FattetKlageinstansvedtakConsumer
import no.nav.su.se.bakover.web.services.klage.KlageinstansvedtakJob
import no.nav.su.se.bakover.web.services.kontrollsamtale.KontrollsamtaleinnkallingJob
import no.nav.su.se.bakover.web.services.personhendelser.PersonhendelseConsumer
import no.nav.su.se.bakover.web.services.personhendelser.PersonhendelseOppgaveJob
import no.nav.su.se.bakover.web.services.tilbakekreving.LokalMottaKravgrunnlagJob
import no.nav.su.se.bakover.web.services.tilbakekreving.TilbakekrevingConsumer
import no.nav.su.se.bakover.web.services.tilbakekreving.TilbakekrevingIbmMqConsumer
import no.nav.su.se.bakover.web.services.tilbakekreving.TilbakekrevingJob
import no.nav.su.se.bakover.web.services.utbetaling.kvittering.LokalKvitteringJob
import no.nav.su.se.bakover.web.services.utbetaling.kvittering.LokalKvitteringService
import no.nav.su.se.bakover.web.services.utbetaling.kvittering.UtbetalingKvitteringConsumer
import no.nav.su.se.bakover.web.services.utbetaling.kvittering.UtbetalingKvitteringIbmMqConsumer
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.slf4j.event.Level
import java.time.Clock
import java.time.Duration
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit

fun main(args: Array<String>) {
    if (ApplicationConfig.isRunningLocally()) {
        System.setProperty(ContextInitializer.CONFIG_FILE_PROPERTY, "logback-local.xml")
    }
    io.ktor.server.netty.EngineMain.main(args)
}

@OptIn(io.ktor.locations.KtorExperimentalLocationsAPI::class)
fun Application.susebakover(
    clock: Clock = Clock.systemUTC(),
    behandlingMetrics: BehandlingMetrics = BehandlingMicrometerMetrics(),
    søknadMetrics: SøknadMetrics = SøknadMicrometerMetrics(),
    applicationConfig: ApplicationConfig = ApplicationConfig.createConfig(),
    unleash: Unleash = UnleashBuilder.build(applicationConfig),
    databaseRepos: DatabaseRepos = DatabaseBuilder.build(
        databaseConfig = applicationConfig.database,
        dbMetrics = DbMicrometerMetrics(),
        clock = clock,
    ),
    jmsConfig: JmsConfig = JmsConfig(applicationConfig),
    clients: Clients =
        if (applicationConfig.runtimeEnvironment != ApplicationConfig.RuntimeEnvironment.Nais)
            StubClientsBuilder(
                clock = clock,
                databaseRepos = databaseRepos,
            ).build(applicationConfig)
        else
            ProdClientsBuilder(
                jmsConfig,
                clock = clock,
                unleash = unleash,
            ).build(applicationConfig),
    services: Services = ServiceBuilder.build(
        databaseRepos = databaseRepos,
        clients = clients,
        behandlingMetrics = behandlingMetrics,
        søknadMetrics = søknadMetrics,
        clock = clock,
        unleash = unleash,
    ),
    accessCheckProxy: AccessCheckProxy = AccessCheckProxy(databaseRepos.person, services),
) {
    install(StatusPages) {
        exception<Tilgangssjekkfeil> {
            when (it.feil) {
                is KunneIkkeHentePerson.IkkeTilgangTilPerson -> {
                    call.sikkerlogg("slo opp person hen ikke har tilgang til")
                    log.warn("[Tilgangssjekk] Ikke tilgang til person.", it)
                    call.svar(Feilresponser.ikkeTilgangTilPerson)
                }
                is KunneIkkeHentePerson.FantIkkePerson -> {
                    log.warn("[Tilgangssjekk] Fant ikke person", it)
                    call.svar(Feilresponser.fantIkkePerson)
                }
                is KunneIkkeHentePerson.Ukjent -> {
                    log.warn("[Tilgangssjekk] Feil ved oppslag på person", it)
                    call.svar(Feilresponser.feilVedOppslagPåPerson)
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
                    },
                ),
            )
        }
        exception<AuthorizationException> {
            call.respond(HttpStatusCode.Forbidden, ErrorJson(it.message))
        }
        exception<UgyldigFnrException> {
            log.warn("Got UgyldigFnrException with message=${it.message}", it)
            call.respond(HttpStatusCode.BadRequest, ErrorJson(it.message ?: "Ugyldig fødselsnummer"))
        }
        exception<StatusovergangVisitor.UgyldigStatusovergangException> {
            log.info("Got ${StatusovergangVisitor.UgyldigStatusovergangException::class.simpleName} with message=${it.msg}")
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

    configureAuthentication(clients.oauth, applicationConfig, clients.tokenOppslag)
    val azureGroupMapper = AzureGroupMapper(applicationConfig.azure.groups)

    install(Authorization) {
        getRoller { principal ->
            getGroupsFromJWT(applicationConfig, principal)
                .mapNotNull { azureGroupMapper.fromAzureGroup(it) }
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
            if (call.pathShouldBeExcluded(togglePaths)) return@filter false

            return@filter true
        }
        callIdMdc("X-Correlation-ID")

        mdc("Authorization") { it.authHeader() }
        disableDefaultColors()
    }

    install(XForwardedHeaderSupport)

    install(SuUserFeature) {
        this.applicationConfig = applicationConfig
    }

    routing {
        toggleRoutes(services.toggles)

        authenticate("frikort") {
            frikortVedtakRoutes(services.vedtakService, clock)
        }

        authenticate("jwt") {
            withUser {
                meRoutes(applicationConfig, azureGroupMapper)

                withAccessProtectedServices(
                    accessCheckProxy,
                ) { accessProtectedServices ->
                    personRoutes(accessProtectedServices.person, clock)
                    sakRoutes(accessProtectedServices.sak, clock)
                    søknadRoutes(
                        søknadService = accessProtectedServices.søknad,
                        lukkSøknadService = accessProtectedServices.lukkSøknad,
                        avslåSøknadManglendeDokumentasjonService = accessProtectedServices.avslåSøknadManglendeDokumentasjonService,
                        clock = clock,
                    )
                    overordnetSøknadsbehandligRoutes(accessProtectedServices.søknadsbehandling, clock)
                    avstemmingRoutes(accessProtectedServices.avstemming, clock)
                    driftRoutes(accessProtectedServices.søknad)
                    revurderingRoutes(accessProtectedServices.revurdering, accessProtectedServices.vedtakService, clock)
                    klageRoutes(accessProtectedServices.klageService)
                    dokumentRoutes(accessProtectedServices.brev)
                    nøkkeltallRoutes(accessProtectedServices.nøkkeltallService)
                    kontrollsamtaleRoutes(accessProtectedServices.kontrollsamtale)
                    reguleringRoutes(services.reguleringService)
                }
            }
        }
    }
    val utbetalingKvitteringConsumer = UtbetalingKvitteringConsumer(
        utbetalingService = services.utbetaling,
        ferdigstillVedtakService = services.ferdigstillVedtak,
        clock = clock,
    )
    val personhendelseService = PersonhendelseService(
        sakRepo = databaseRepos.sak,
        personhendelseRepo = databaseRepos.personhendelseRepo,
        oppgaveServiceImpl = services.oppgave,
        personService = services.person,
        clock = clock,
    )
    val tilbakekrevingConsumer = TilbakekrevingConsumer(
        tilbakekrevingService = services.tilbakekrevingService,
        sakService = services.sak,
        clock = clock,
        revurderingService = services.revurdering,
    )
    if (applicationConfig.runtimeEnvironment == ApplicationConfig.RuntimeEnvironment.Nais) {
        UtbetalingKvitteringIbmMqConsumer(
            kvitteringQueueName = applicationConfig.oppdrag.utbetaling.mqReplyTo,
            globalJmsContext = jmsConfig.jmsContext,
            kvitteringConsumer = utbetalingKvitteringConsumer,
        )
        GrensesnittsavstemingJob(
            avstemmingService = services.avstemming,
            leaderPodLookup = clients.leaderPodLookup,
        ).schedule()

        PersonhendelseConsumer(
            consumer = KafkaConsumer(applicationConfig.kafkaConfig.consumerCfg.kafkaConfig),
            personhendelseService = personhendelseService,
            maxBatchSize = applicationConfig.kafkaConfig.consumerCfg.kafkaConfig[ConsumerConfig.MAX_POLL_RECORDS_CONFIG] as? Int,
        )
        FattetKlageinstansvedtakConsumer(
            consumer = KafkaConsumer(applicationConfig.kabalKafkaConfig.kafkaConfig),
            klagevedtakService = services.klagevedtakService,
            maxBatchSize = applicationConfig.kabalKafkaConfig.kafkaConfig[ConsumerConfig.MAX_POLL_RECORDS_CONFIG] as? Int,
            clock = clock,
            toggleService = services.toggles,
        )
        DistribuerDokumentJob(
            brevService = services.brev,
            leaderPodLookup = clients.leaderPodLookup,
            initialDelay = applicationConfig.jobConfig.initialDelay
        ).schedule()

        KonsistensavstemmingJob(
            avstemmingService = services.avstemming,
            leaderPodLookup = clients.leaderPodLookup,
            jobConfig = applicationConfig.jobConfig.konsistensavstemming,
            initialDelay = applicationConfig.jobConfig.initialDelay,
            clock = clock,
        ).schedule()

        KlageinstansvedtakJob(
            klagevedtakService = services.klagevedtakService,
            leaderPodLookup = clients.leaderPodLookup,
            initialDelay = applicationConfig.jobConfig.initialDelay,
        ).schedule()

        TilbakekrevingIbmMqConsumer(
            queueName = applicationConfig.oppdrag.tilbakekreving.mq.mqReplyTo,
            globalJmsContext = jmsConfig.jmsContext,
            tilbakekrevingConsumer = tilbakekrevingConsumer,
        )

        TilbakekrevingJob(
            tilbakekrevingService = services.tilbakekrevingService,
            leaderPodLookup = clients.leaderPodLookup,
            intervall = Duration.of(10, ChronoUnit.MINUTES).toMillis(),
        ).schedule()
    } else if (applicationConfig.runtimeEnvironment == ApplicationConfig.RuntimeEnvironment.Local) {
        LokalKvitteringJob(
            lokalKvitteringService = LokalKvitteringService(
                utbetalingRepo = databaseRepos.utbetaling,
                utbetalingKvitteringConsumer = utbetalingKvitteringConsumer,
            ),
        ).schedule()

        DistribuerDokumentJob(
            brevService = services.brev,
            leaderPodLookup = clients.leaderPodLookup,
            initialDelay = applicationConfig.jobConfig.initialDelay,
        ).schedule()

        GrensesnittsavstemingJob(
            avstemmingService = services.avstemming,
            leaderPodLookup = clients.leaderPodLookup,
        ).schedule()

        KonsistensavstemmingJob(
            avstemmingService = services.avstemming,
            leaderPodLookup = clients.leaderPodLookup,
            jobConfig = applicationConfig.jobConfig.konsistensavstemming,
            initialDelay = applicationConfig.jobConfig.initialDelay,
            clock = clock,
        ).schedule()
        KlageinstansvedtakJob(
            klagevedtakService = services.klagevedtakService,
            leaderPodLookup = clients.leaderPodLookup,
            initialDelay = applicationConfig.jobConfig.initialDelay,
        ).schedule()

        LokalMottaKravgrunnlagJob(
            tilbakekrevingConsumer = tilbakekrevingConsumer,
            tilbakekrevingService = services.tilbakekrevingService,
            revurderingService = services.revurdering,
        ).schedule()

        TilbakekrevingJob(
            tilbakekrevingService = services.tilbakekrevingService,
            leaderPodLookup = clients.leaderPodLookup,
            intervall = Duration.of(1, ChronoUnit.MINUTES).toMillis(),
        ).schedule()
    }

    PersonhendelseOppgaveJob(
        personhendelseService = personhendelseService,
        leaderPodLookup = clients.leaderPodLookup,
        intervall = applicationConfig.jobConfig.personhendelse.intervall,
        initialDelay = applicationConfig.jobConfig.initialDelay,
    ).schedule()

    KontrollsamtaleinnkallingJob(
        isProd = applicationConfig.naisCluster == ApplicationConfig.NaisCluster.Prod,
        leaderPodLookup = clients.leaderPodLookup,
        kontrollsamtaleService = services.kontrollsamtale,
        clock = clock,
    ).schedule()
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
