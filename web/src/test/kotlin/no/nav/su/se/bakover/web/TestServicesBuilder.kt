package no.nav.su.se.bakover.web

import no.nav.su.se.bakover.service.Services
import no.nav.su.se.bakover.service.avstemming.AvstemmingService
import no.nav.su.se.bakover.service.brev.BrevService
import no.nav.su.se.bakover.service.grunnlag.GrunnlagService
import no.nav.su.se.bakover.service.klage.KlageService
import no.nav.su.se.bakover.service.klage.KlagevedtakService
import no.nav.su.se.bakover.service.kontrollsamtale.KontrollsamtaleService
import no.nav.su.se.bakover.service.nøkkeltall.NøkkeltallService
import no.nav.su.se.bakover.service.oppgave.OppgaveService
import no.nav.su.se.bakover.service.person.PersonService
import no.nav.su.se.bakover.service.revurdering.RevurderingService
import no.nav.su.se.bakover.service.sak.SakService
import no.nav.su.se.bakover.service.statistikk.StatistikkService
import no.nav.su.se.bakover.service.søknad.AvslåSøknadManglendeDokumentasjonService
import no.nav.su.se.bakover.service.søknad.SøknadService
import no.nav.su.se.bakover.service.søknad.lukk.LukkSøknadService
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingService
import no.nav.su.se.bakover.service.toggles.ToggleService
import no.nav.su.se.bakover.service.utbetaling.UtbetalingService
import no.nav.su.se.bakover.service.vedtak.FerdigstillVedtakService
import no.nav.su.se.bakover.service.vedtak.VedtakService
import org.mockito.kotlin.mock

object TestServicesBuilder {
    fun services(
        avstemming: AvstemmingService = mock(),
        utbetaling: UtbetalingService = mock(),
        sak: SakService = mock(),
        søknad: SøknadService = mock(),
        brev: BrevService = mock(),
        lukkSøknad: LukkSøknadService = mock(),
        oppgave: OppgaveService = mock(),
        person: PersonService = mock(),
        statistikk: StatistikkService = mock(),
        toggles: ToggleService = mock(),
        søknadsbehandling: SøknadsbehandlingService = mock(),
        ferdigstillVedtak: FerdigstillVedtakService = mock(),
        revurdering: RevurderingService = mock(),
        vedtakService: VedtakService = mock(),
        grunnlagService: GrunnlagService = mock(),
        nøkkeltallService: NøkkeltallService = mock(),
        avslåSøknadManglendeDokumentasjonService: AvslåSøknadManglendeDokumentasjonService = mock(),
        kontrollsamtaleService: KontrollsamtaleService = mock(),
        klageService: KlageService = mock(),
        klagevedtakService: KlagevedtakService = mock(),
    ): Services = Services(
        avstemming = avstemming,
        utbetaling = utbetaling,
        sak = sak,
        søknad = søknad,
        brev = brev,
        lukkSøknad = lukkSøknad,
        oppgave = oppgave,
        person = person,
        statistikk = statistikk,
        toggles = toggles,
        søknadsbehandling = søknadsbehandling,
        ferdigstillVedtak = ferdigstillVedtak,
        revurdering = revurdering,
        vedtakService = vedtakService,
        grunnlagService = grunnlagService,
        nøkkeltallService = nøkkeltallService,
        avslåSøknadManglendeDokumentasjonService = avslåSøknadManglendeDokumentasjonService,
        kontrollsamtale = kontrollsamtaleService,
        klageService = klageService,
        klagevedtakService = klagevedtakService,
    )
}
