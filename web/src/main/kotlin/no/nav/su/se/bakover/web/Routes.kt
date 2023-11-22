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
import no.nav.su.se.bakover.domain.satser.SatsFactory
import no.nav.su.se.bakover.kontrollsamtale.infrastructure.web.kontrollsamtaleRoutes
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
import no.nav.su.se.bakover.web.routes.revurdering.leggTilBrevvalgRevurderingRoute
import no.nav.su.se.bakover.web.routes.revurdering.revurderingRoutes
import no.nav.su.se.bakover.web.routes.sak.sakRoutes
import no.nav.su.se.bakover.web.routes.skatt.skattRoutes
import no.nav.su.se.bakover.web.routes.søknad.søknadRoutes
import no.nav.su.se.bakover.web.routes.søknadsbehandling.overordnetSøknadsbehandligRoutes
import no.nav.su.se.bakover.web.routes.vedtak.stønadsmottakereRoute
import no.nav.su.se.bakover.web.routes.vilkår.opplysningsplikt.opplysningspliktRoutes
import no.nav.su.se.bakover.web.services.AccessCheckProxy
import no.nav.su.se.bakover.web.services.Services
import tilbakekreving.application.service.Tilbakekrevingskomponenter
import tilbakekreving.presentation.api.tilbakekrevingRoutes
import java.time.Clock

internal fun Application.setupKtorRoutes(
    services: Services,
    clock: Clock,
    applicationConfig: ApplicationConfig,
    accessCheckProxy: AccessCheckProxy,
    extraRoutes: Route.(services: Services) -> Unit,
    azureGroupMapper: AzureGroupMapper,
    satsFactoryIDag: SatsFactory,
    databaseRepos: DatabaseRepos,
    tilbakekrevingskomponenter: Tilbakekrevingskomponenter,
    clients: Clients,
) {
    routing {
        authenticate("frikort") {
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
                    sakRoutes(accessProtectedServices.sak, clock, satsFactoryIDag)
                    søknadRoutes(
                        søknadService = accessProtectedServices.søknad,
                        lukkSøknadService = accessProtectedServices.lukkSøknad,
                        avslåSøknadManglendeDokumentasjonService = accessProtectedServices.avslåSøknadManglendeDokumentasjonService,
                        clock = clock,
                        satsFactoryIDag,
                    )
                    overordnetSøknadsbehandligRoutes(
                        accessProtectedServices.søknadsbehandling,
                        clock,
                        satsFactoryIDag,
                        applicationConfig,
                    )
                    avstemmingRoutes(accessProtectedServices.avstemming, clock)
                    driftRoutes(
                        søknadService = accessProtectedServices.søknad,
                        resendStatistikkhendelserService = accessProtectedServices.resendStatistikkhendelserService,
                        ferdigstillVedtakService = accessProtectedServices.ferdigstillVedtak,
                    )
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
                    stønadsmottakereRoute(accessProtectedServices.vedtakService, clock)
                    kontrollsamtaleRoutes(accessProtectedServices.kontrollsamtaleSetup.kontrollsamtaleService)
                    reguleringRoutes(accessProtectedServices.reguleringService, clock)
                    opplysningspliktRoutes(
                        søknadsbehandlingService = accessProtectedServices.søknadsbehandling.søknadsbehandlingService,
                        revurderingService = accessProtectedServices.revurdering,
                        satsFactory = satsFactoryIDag,
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
                    leggTilBrevvalgRevurderingRoute(
                        revurderingService = accessProtectedServices.revurdering,
                        satsFactory = satsFactoryIDag,
                    )
                    kontrollsamtaleRoutes(
                        kontrollsamtaleService = services.kontrollsamtaleSetup.kontrollsamtaleService,
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
                    )
                }
            }
        }
    }
}

private fun Route.withAccessProtectedServices(
    accessCheckProxy: AccessCheckProxy,
    build: Route.(services: Services) -> Unit,
) = build(accessCheckProxy.proxy())
