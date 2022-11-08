package no.nav.su.se.bakover.service

import no.nav.su.se.bakover.domain.brev.BrevService
import no.nav.su.se.bakover.domain.person.PersonService
import no.nav.su.se.bakover.domain.sak.SakService
import no.nav.su.se.bakover.service.avstemming.AvstemmingService
import no.nav.su.se.bakover.service.klage.KlageService
import no.nav.su.se.bakover.service.klage.KlageinstanshendelseService
import no.nav.su.se.bakover.service.kontrollsamtale.KontrollsamtaleService
import no.nav.su.se.bakover.service.kontrollsamtale.UtløptFristForKontrollsamtaleService
import no.nav.su.se.bakover.service.nøkkeltall.NøkkeltallService
import no.nav.su.se.bakover.service.oppgave.OppgaveService
import no.nav.su.se.bakover.service.regulering.ReguleringService
import no.nav.su.se.bakover.service.revurdering.RevurderingService
import no.nav.su.se.bakover.service.skatt.SkatteService
import no.nav.su.se.bakover.service.søknad.AvslåSøknadManglendeDokumentasjonService
import no.nav.su.se.bakover.service.søknad.SøknadService
import no.nav.su.se.bakover.service.søknad.lukk.LukkSøknadService
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingService
import no.nav.su.se.bakover.service.tilbakekreving.TilbakekrevingService
import no.nav.su.se.bakover.service.toggles.ToggleService
import no.nav.su.se.bakover.service.utbetaling.UtbetalingService
import no.nav.su.se.bakover.service.vedtak.FerdigstillVedtakService
import no.nav.su.se.bakover.service.vedtak.VedtakService

data class Services(
    val avstemming: AvstemmingService,
    val utbetaling: UtbetalingService,
    val sak: SakService,
    val søknad: SøknadService,
    val brev: BrevService,
    val lukkSøknad: LukkSøknadService,
    val oppgave: OppgaveService,
    val person: PersonService,
    val toggles: ToggleService,
    val søknadsbehandling: SøknadsbehandlingService,
    val ferdigstillVedtak: FerdigstillVedtakService,
    val revurdering: RevurderingService,
    val vedtakService: VedtakService,
    val nøkkeltallService: NøkkeltallService,
    val avslåSøknadManglendeDokumentasjonService: AvslåSøknadManglendeDokumentasjonService,
    val kontrollsamtale: KontrollsamtaleService,
    val klageService: KlageService,
    val klageinstanshendelseService: KlageinstanshendelseService,
    val reguleringService: ReguleringService,
    val tilbakekrevingService: TilbakekrevingService,
    val sendPåminnelserOmNyStønadsperiodeService: SendPåminnelserOmNyStønadsperiodeService,
    val skatteService: SkatteService,
    val utløptFristForKontrollsamtaleService: UtløptFristForKontrollsamtaleService,
)
