package no.nav.su.se.bakover.web

import no.nav.su.se.bakover.common.toggle.domain.ToggleClient
import no.nav.su.se.bakover.domain.brev.BrevService
import no.nav.su.se.bakover.domain.oppgave.OppgaveService
import no.nav.su.se.bakover.domain.person.PersonService
import no.nav.su.se.bakover.domain.regulering.ReguleringService
import no.nav.su.se.bakover.domain.revurdering.RevurderingService
import no.nav.su.se.bakover.domain.revurdering.gjenopptak.GjenopptaYtelseService
import no.nav.su.se.bakover.domain.revurdering.opphør.AnnullerKontrollsamtaleVedOpphørService
import no.nav.su.se.bakover.domain.revurdering.stans.StansYtelseService
import no.nav.su.se.bakover.domain.sak.SakService
import no.nav.su.se.bakover.domain.søknadsbehandling.iverksett.OpprettKontrollsamtaleVedNyStønadsperiodeService
import no.nav.su.se.bakover.kontrollsamtale.domain.KontrollsamtaleService
import no.nav.su.se.bakover.kontrollsamtale.domain.UtløptFristForKontrollsamtaleService
import no.nav.su.se.bakover.kontrollsamtale.infrastructure.setup.KontrollsamtaleSetup
import no.nav.su.se.bakover.service.SendPåminnelserOmNyStønadsperiodeService
import no.nav.su.se.bakover.service.avstemming.AvstemmingService
import no.nav.su.se.bakover.service.klage.KlageService
import no.nav.su.se.bakover.service.klage.KlageinstanshendelseService
import no.nav.su.se.bakover.service.nøkkeltall.NøkkeltallService
import no.nav.su.se.bakover.service.skatt.SkatteService
import no.nav.su.se.bakover.service.søknad.AvslåSøknadManglendeDokumentasjonService
import no.nav.su.se.bakover.service.søknad.SøknadService
import no.nav.su.se.bakover.service.søknad.lukk.LukkSøknadService
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingServices
import no.nav.su.se.bakover.service.tilbakekreving.TilbakekrevingService
import no.nav.su.se.bakover.service.utbetaling.UtbetalingService
import no.nav.su.se.bakover.service.vedtak.FerdigstillVedtakService
import no.nav.su.se.bakover.service.vedtak.VedtakService
import no.nav.su.se.bakover.web.services.Services
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
        toggles: ToggleClient = mock(),
        søknadsbehandling: SøknadsbehandlingServices = SøknadsbehandlingServices(
            iverksettSøknadsbehandlingService = mock(),
            søknadsbehandlingService = mock(),
        ),
        ferdigstillVedtak: FerdigstillVedtakService = mock(),
        revurdering: RevurderingService = mock(),
        vedtakService: VedtakService = mock(),
        nøkkeltallService: NøkkeltallService = mock(),
        avslåSøknadManglendeDokumentasjonService: AvslåSøknadManglendeDokumentasjonService = mock(),
        klageService: KlageService = mock(),
        klageinstanshendelseService: KlageinstanshendelseService = mock(),
        regulerServices: ReguleringService = mock(),
        tilbakekrevingService: TilbakekrevingService = mock(),
        sendPåminnelserOmNyStønadsperiodeService: SendPåminnelserOmNyStønadsperiodeService = mock(),
        skatteService: SkatteService = mock(),
        stansAvYtelseService: StansYtelseService = mock(),
        gjenopptakAvYtelseService: GjenopptaYtelseService = mock(),
        kontrollsamtaleSetup: KontrollsamtaleSetup = object : KontrollsamtaleSetup {
            override val kontrollsamtaleService: KontrollsamtaleService = mock()
            override val annullerKontrollsamtaleService: AnnullerKontrollsamtaleVedOpphørService = mock()
            override val opprettPlanlagtKontrollsamtaleService: OpprettKontrollsamtaleVedNyStønadsperiodeService = mock()
            override val utløptFristForKontrollsamtaleService: UtløptFristForKontrollsamtaleService = mock()
        },
    ): Services = Services(
        avstemming = avstemming,
        utbetaling = utbetaling,
        sak = sak,
        søknad = søknad,
        brev = brev,
        lukkSøknad = lukkSøknad,
        oppgave = oppgave,
        person = person,
        toggles = toggles,
        søknadsbehandling = søknadsbehandling,
        ferdigstillVedtak = ferdigstillVedtak,
        revurdering = revurdering,
        vedtakService = vedtakService,
        nøkkeltallService = nøkkeltallService,
        avslåSøknadManglendeDokumentasjonService = avslåSøknadManglendeDokumentasjonService,
        klageService = klageService,
        klageinstanshendelseService = klageinstanshendelseService,
        reguleringService = regulerServices,
        tilbakekrevingService = tilbakekrevingService,
        sendPåminnelserOmNyStønadsperiodeService = sendPåminnelserOmNyStønadsperiodeService,
        skatteService = skatteService,
        stansYtelse = stansAvYtelseService,
        gjenopptaYtelse = gjenopptakAvYtelseService,
        kontrollsamtaleSetup = kontrollsamtaleSetup,
    )
}
