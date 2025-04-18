package no.nav.su.se.bakover.web

import io.ktor.server.application.Application
import io.ktor.server.auth.authenticate
import io.ktor.server.routing.Route
import io.ktor.server.routing.routing
import no.nav.su.se.bakover.client.Clients
import no.nav.su.se.bakover.common.infrastructure.audit.CefAuditLogger
import no.nav.su.se.bakover.common.infrastructure.brukerrolle.AzureGroupMapper
import no.nav.su.se.bakover.common.infrastructure.config.ApplicationConfig
import no.nav.su.se.bakover.common.infrastructure.web.withUser
import no.nav.su.se.bakover.domain.DatabaseRepos
import no.nav.su.se.bakover.kontrollsamtale.infrastructure.web.kontrollsamtaleRoutes
import no.nav.su.se.bakover.service.dokument.DistribuerDokumentService
import no.nav.su.se.bakover.utenlandsopphold.application.annuller.AnnullerUtenlandsoppholdService
import no.nav.su.se.bakover.utenlandsopphold.application.korriger.KorrigerUtenlandsoppholdService
import no.nav.su.se.bakover.utenlandsopphold.application.registrer.RegistrerUtenlandsoppholdService
import no.nav.su.se.bakover.utenlandsopphold.infrastruture.web.utenlandsoppholdRoutes
import no.nav.su.se.bakover.web.external.frikortVedtakRoutes
import no.nav.su.se.bakover.web.routes.avstemming.avstemmingRoutes
import no.nav.su.se.bakover.web.routes.dokument.dokumentRoutes
import no.nav.su.se.bakover.web.routes.drift.driftRoutes
import no.nav.su.se.bakover.web.routes.klage.klageRoutes
import no.nav.su.se.bakover.web.routes.me.meRoutes
import no.nav.su.se.bakover.web.routes.nøkkeltall.nøkkeltallRoutes
import no.nav.su.se.bakover.web.routes.person.personRoutes
import no.nav.su.se.bakover.web.routes.regulering.reguleringRoutes
import no.nav.su.se.bakover.web.routes.revurdering.revurderingRoutes
import no.nav.su.se.bakover.web.routes.sak.sakRoutes
import no.nav.su.se.bakover.web.routes.skatt.skattRoutes
import no.nav.su.se.bakover.web.routes.søknad.søknadRoutes
import no.nav.su.se.bakover.web.routes.søknadsbehandling.overordnetSøknadsbehandligRoutes
import no.nav.su.se.bakover.web.routes.vedtak.stønadsmottakereRoute
import no.nav.su.se.bakover.web.routes.vedtak.vedtakRoutes
import no.nav.su.se.bakover.web.routes.vilkår.opplysningsplikt.opplysningspliktRoutes
import no.nav.su.se.bakover.web.services.AccessCheckProxy
import no.nav.su.se.bakover.web.services.Services
import tilbakekreving.presentation.Tilbakekrevingskomponenter
import tilbakekreving.presentation.api.tilbakekrevingRoutes
import vilkår.formue.domain.FormuegrenserFactory
import økonomi.application.utbetaling.ResendUtbetalingService
import økonomi.presentation.api.økonomiRoutes
import java.time.Clock

internal fun Application.setupKtorRoutes(
    services: Services,
    clock: Clock,
    applicationConfig: ApplicationConfig,
    accessCheckProxy: AccessCheckProxy,
    extraRoutes: Route.(services: Services) -> Unit,
    azureGroupMapper: AzureGroupMapper,
    formuegrenserFactoryIDag: FormuegrenserFactory,
    databaseRepos: DatabaseRepos,
    tilbakekrevingskomponenter: Tilbakekrevingskomponenter,
    clients: Clients,
    resendUtbetalingService: ResendUtbetalingService,
    distribuerDokumentService: DistribuerDokumentService,
) {
    routing {
        authenticate("frikort", "frikort2") {
            frikortVedtakRoutes(services.vedtakService, clock)
        }

        authenticate("jwt") {
            withUser(applicationConfig) {
                meRoutes(applicationConfig, azureGroupMapper)

                withAccessProtectedServices(
                    accessCheckProxy,
                ) { accessProtectedServices ->
                    extraRoutes(this, services)
                    personRoutes(accessProtectedServices.person, clock)
                    sakRoutes(accessProtectedServices.sak, clock, formuegrenserFactoryIDag)
                    søknadRoutes(
                        søknadService = accessProtectedServices.søknad,
                        lukkSøknadService = accessProtectedServices.lukkSøknad,
                        avslåSøknadManglendeDokumentasjonService = accessProtectedServices.avslåSøknadManglendeDokumentasjonService,
                        clock = clock,
                        formuegrenserFactory = formuegrenserFactoryIDag,
                    )
                    overordnetSøknadsbehandligRoutes(
                        søknadsbehandlingServices = accessProtectedServices.søknadsbehandling,
                        clock = clock,
                        formuegrenserFactory = formuegrenserFactoryIDag,
                        applicationConfig = applicationConfig,
                    )
                    avstemmingRoutes(accessProtectedServices.avstemming, clock)
                    driftRoutes(
                        søknadService = accessProtectedServices.søknad,
                        resendStatistikkhendelserService = accessProtectedServices.resendStatistikkhendelserService,
                        ferdigstillVedtakService = accessProtectedServices.ferdigstillVedtak,
                        personhendelseService = accessProtectedServices.personhendelseService,
                    )
                    revurderingRoutes(
                        revurderingService = accessProtectedServices.revurdering,
                        sakService = accessProtectedServices.sak,
                        clock = clock,
                        formuegrenserFactory = formuegrenserFactoryIDag,
                        stansAvYtelseService = accessProtectedServices.stansYtelse,
                        gjenopptakAvYtelseService = accessProtectedServices.gjenopptaYtelse,
                    )
                    klageRoutes(accessProtectedServices.klageService, clock)
                    dokumentRoutes(accessProtectedServices.brev, distribuerDokumentService)
                    nøkkeltallRoutes(accessProtectedServices.nøkkeltallService)
                    stønadsmottakereRoute(accessProtectedServices.vedtakService, clock)
                    kontrollsamtaleRoutes(
                        kontrollsamtaleService = accessProtectedServices.kontrollsamtaleSetup.kontrollsamtaleService,
                    )
                    reguleringRoutes(
                        accessProtectedServices.reguleringService,
                        clock,
                        applicationConfig.runtimeEnvironment,
                    )
                    opplysningspliktRoutes(
                        søknadsbehandlingService = accessProtectedServices.søknadsbehandling.søknadsbehandlingService,
                        revurderingService = accessProtectedServices.revurdering,
                        formuegrenserFactory = formuegrenserFactoryIDag,
                        clock = clock,
                    )
                    skattRoutes(accessProtectedServices.skatteService)
                    utenlandsoppholdRoutes(
                        registerService = RegistrerUtenlandsoppholdService(
                            sakRepo = databaseRepos.sak,
                            utenlandsoppholdRepo = databaseRepos.utenlandsoppholdRepo,
                            queryJournalpostClient = clients.queryJournalpostClient,
                            auditLogger = CefAuditLogger,
                            personService = services.person,
                        ),
                        korrigerService = KorrigerUtenlandsoppholdService(
                            sakRepo = databaseRepos.sak,
                            utenlandsoppholdRepo = databaseRepos.utenlandsoppholdRepo,
                            queryJournalpostClient = clients.queryJournalpostClient,
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
                    tilbakekrevingRoutes(
                        opprettTilbakekrevingsbehandlingService = tilbakekrevingskomponenter.services.opprettTilbakekrevingsbehandlingService,
                        månedsvurderingerTilbakekrevingsbehandlingService = tilbakekrevingskomponenter.services.månedsvurderingerTilbakekrevingsbehandlingService,
                        brevTilbakekrevingsbehandlingService = tilbakekrevingskomponenter.services.brevTilbakekrevingsbehandlingService,
                        forhåndsvisVedtaksbrevTilbakekrevingsbehandlingService = tilbakekrevingskomponenter.services.forhåndsvisVedtaksbrevTilbakekrevingsbehandlingService,
                        forhåndsvarsleTilbakekrevingsbehandlingService = tilbakekrevingskomponenter.services.forhåndsvarsleTilbakekrevingsbehandlingService,
                        forhåndsvisForhåndsvarselTilbakekrevingsbehandlingService = tilbakekrevingskomponenter.services.forhåndsvisForhåndsvarselTilbakekrevingsbehandlingService,
                        tilbakekrevingsbehandlingTilAttesteringService = tilbakekrevingskomponenter.services.tilbakekrevingsbehandlingTilAttesteringService,
                        visUtsendtForhåndsvarselbrevForTilbakekrevingService = tilbakekrevingskomponenter.services.visUtsendtForhåndsvarselbrevForTilbakekrevingService,
                        underkjennTilbakekrevingsbehandlingService = tilbakekrevingskomponenter.services.underkjennTilbakekrevingsbehandlingService,
                        iverksettTilbakekrevingService = tilbakekrevingskomponenter.services.iverksettTilbakekrevingService,
                        avbrytTilbakekrevingsbehandlingService = tilbakekrevingskomponenter.services.avbrytTilbakekrevingsbehandlingService,
                        oppdaterKravgrunnlagService = tilbakekrevingskomponenter.services.oppdaterKravgrunnlagService,
                        notatTilbakekrevingsbehandlingService = tilbakekrevingskomponenter.services.notatTilbakekrevingsbehandlingService,
                        annullerKravgrunnlagService = tilbakekrevingskomponenter.services.annullerKravgrunnlagService,
                    )
                    økonomiRoutes(resendUtbetalingService)
                    vedtakRoutes(services.vedtakService, formuegrenserFactoryIDag)
                }
            }
        }
    }
}

private fun Route.withAccessProtectedServices(
    accessCheckProxy: AccessCheckProxy,
    build: Route.(services: Services) -> Unit,
) = build(accessCheckProxy.proxy())
