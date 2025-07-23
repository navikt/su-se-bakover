package no.nav.su.se.bakover.test.application

import beregning.domain.BeregningStrategyFactory
import dokument.domain.brev.BrevService
import dokument.domain.hendelser.DokumentHendelseRepo
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.headers
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.content.PartData
import io.ktor.http.contentType
import io.ktor.server.application.Application
import io.ktor.server.routing.Route
import kotlinx.coroutines.runBlocking
import no.nav.su.se.bakover.client.Clients
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.domain.config.TilbakekrevingConfig
import no.nav.su.se.bakover.common.infrastructure.config.ApplicationConfig
import no.nav.su.se.bakover.common.infrastructure.jms.JmsConfig
import no.nav.su.se.bakover.common.infrastructure.persistence.DbMetrics
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.dokument.infrastructure.Dokumentkomponenter
import no.nav.su.se.bakover.domain.DatabaseRepos
import no.nav.su.se.bakover.domain.oppgave.OppgaveService
import no.nav.su.se.bakover.domain.sak.SakService
import no.nav.su.se.bakover.hendelse.domain.HendelseRepo
import no.nav.su.se.bakover.hendelse.domain.HendelsekonsumenterRepo
import no.nav.su.se.bakover.oppgave.domain.OppgaveHendelseRepo
import no.nav.su.se.bakover.service.dokument.DistribuerDokumentService
import no.nav.su.se.bakover.test.applicationConfig
import no.nav.su.se.bakover.test.auth.FakeSamlTokenProvider
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.jwt.asBearerToken
import no.nav.su.se.bakover.test.jwt.jwtStub
import no.nav.su.se.bakover.web.services.AccessCheckProxy
import no.nav.su.se.bakover.web.services.Services
import no.nav.su.se.bakover.web.susebakover
import org.mockito.kotlin.mock
import org.slf4j.MDC
import satser.domain.SatsFactory
import satser.domain.supplerendestønad.SatsFactoryForSupplerendeStønad
import tilbakekreving.infrastructure.repo.kravgrunnlag.MapRåttKravgrunnlagTilHendelse
import tilbakekreving.presentation.Tilbakekrevingskomponenter
import tilgangstyring.application.TilgangstyringService
import vilkår.formue.domain.FormuegrenserFactory
import økonomi.application.utbetaling.ResendUtbetalingService
import java.time.Clock

/**
 * Contains setup-code for web-server-tests (routes).
 * Defaultly mocks everything except what is required to run the routes.
 */
fun Application.runApplicationWithMocks(
    clock: Clock = fixedClock,
    dbMetrics: DbMetrics = mock(),
    applicationConfig: ApplicationConfig = applicationConfig(),
    satsFactory: SatsFactoryForSupplerendeStønad = mock(),
    satsFactoryIDag: SatsFactory = mock(),
    formuegrenserFactoryIDag: FormuegrenserFactory = mock(),
    databaseRepos: DatabaseRepos = mockedDatabaseRepos(),
    jmsConfig: JmsConfig = mock(),
    clients: Clients = mockedClients(),
    services: Services = mockedServices(),
    tilbakekrevingskomponenter: (
        clock: Clock,
        sessionFactory: SessionFactory,
        hendelsekonsumenterRepo: HendelsekonsumenterRepo,
        sakService: SakService,
        oppgaveService: OppgaveService,
        oppgaveHendelseRepo: OppgaveHendelseRepo,
        mapRåttKravgrunnlag: MapRåttKravgrunnlagTilHendelse,
        hendelseRepo: HendelseRepo,
        dokumentHendelseRepo: DokumentHendelseRepo,
        brevService: BrevService,
        tilbakekrevingConfig: TilbakekrevingConfig,
        tilgangstyringService: TilgangstyringService,
    ) -> Tilbakekrevingskomponenter = { clockFunParam, sessionFactory, hendelsekonsumenterRepo, sak, oppgave, oppgaveHendelseRepo, mapRåttKravgrunnlagPåSakHendelse, hendelseRepo, dokumentHendelseRepo, brevService, tilbakekrevingConfig, tilgangstyringService ->
        Tilbakekrevingskomponenter.create(
            clock = clockFunParam,
            sessionFactory = sessionFactory,
            hendelsekonsumenterRepo = hendelsekonsumenterRepo,
            sakService = sak,
            oppgaveService = oppgave,
            oppgaveHendelseRepo = oppgaveHendelseRepo,
            mapRåttKravgrunnlagPåSakHendelse = mapRåttKravgrunnlagPåSakHendelse,
            hendelseRepo = hendelseRepo,
            dokumentHendelseRepo = dokumentHendelseRepo,
            brevService = brevService,
            tilbakekrevingConfig = tilbakekrevingConfig,
            dbMetrics = dbMetrics,
            samlTokenProvider = FakeSamlTokenProvider(),
            tilgangstyringService = tilgangstyringService,
        )
    },
    dokumentkomponenter: Dokumentkomponenter = mock(),
    accessCheckProxy: AccessCheckProxy = AccessCheckProxy(databaseRepos.person, services),
    beregningStrategyFactory: BeregningStrategyFactory = mock(),
    resendUtbetalingService: ResendUtbetalingService = mock(),
    distribuerDokumentService: DistribuerDokumentService = mock(),
    extraRoutes: Route.(services: Services) -> Unit = {},
) {
    return susebakover(
        clock = clock,
        dbMetrics = dbMetrics,
        applicationConfig = applicationConfig,
        satsFactory = satsFactory,
        satsFactoryIDag = satsFactoryIDag,
        formuegrenserFactoryIDag = formuegrenserFactoryIDag,
        databaseRepos = databaseRepos,
        jmsConfig = jmsConfig,
        clients = clients,
        services = services,
        tilbakekrevingskomponenter = tilbakekrevingskomponenter,
        dokumentkomponenter = dokumentkomponenter,
        accessCheckProxy = accessCheckProxy,
        beregningStrategyFactory = beregningStrategyFactory,
        resendUtbetalingService = resendUtbetalingService,
        distribuerDokumentService = distribuerDokumentService,
        extraRoutes = extraRoutes,
        disableConsumersAndJobs = true,

    )
}

private const val DEFAULT_CALL_ID = "her skulle vi sikkert hatt en korrelasjonsid"

fun defaultRequest(
    method: HttpMethod,
    uri: String,
    roller: List<Brukerrolle> = emptyList(),
    navIdent: String = "Z990Lokal",
    correlationId: String = DEFAULT_CALL_ID,
    client: HttpClient,
    body: String? = null,
    setup: HttpRequestBuilder.() -> Unit = {},
): HttpResponse {
    return runBlocking {
        client.request(uri) {
            val auth: String? = MDC.get("Authorization")
            val bearerToken = auth ?: jwtStub.createJwtToken(roller = roller, navIdent = navIdent).asBearerToken()
            this.method = method
            if (body != null) {
                contentType(ContentType.Application.Json)
                setBody(body)
            }
            this.headers {
                append(HttpHeaders.XCorrelationId, correlationId)
                append(HttpHeaders.Authorization, bearerToken)
            }
            setup()
        }
    }
}

fun formdataRequest(
    method: HttpMethod,
    uri: String,
    roller: List<Brukerrolle> = emptyList(),
    formData: List<PartData> = formData {},
    navIdent: String = "Z990Lokal",
    correlationId: String = DEFAULT_CALL_ID,
    client: HttpClient,
): HttpResponse {
    return runBlocking {
        client.submitFormWithBinaryData(
            url = uri,
            formData = formData,
        ) {
            val auth: String? = MDC.get("Authorization")
            val bearerToken = auth ?: jwtStub.createJwtToken(roller = roller, navIdent = navIdent).asBearerToken()
            this.method = method
            this.headers {
                append(HttpHeaders.XCorrelationId, correlationId)
                append(HttpHeaders.Authorization, bearerToken)
            }
        }
    }
}
