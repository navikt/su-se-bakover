package no.nav.su.se.bakover.web.services

import no.nav.su.se.bakover.common.toggle.domain.ToggleClient
import no.nav.su.se.bakover.domain.brev.BrevService
import no.nav.su.se.bakover.domain.oppgave.OppgaveService
import no.nav.su.se.bakover.domain.person.PersonService
import no.nav.su.se.bakover.domain.regulering.ReguleringService
import no.nav.su.se.bakover.domain.revurdering.RevurderingService
import no.nav.su.se.bakover.domain.revurdering.gjenopptak.GjenopptaYtelseService
import no.nav.su.se.bakover.domain.revurdering.stans.StansYtelseService
import no.nav.su.se.bakover.domain.sak.SakService
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

data class Services(
    val avstemming: AvstemmingService,
    val utbetaling: UtbetalingService,
    val sak: SakService,
    val søknad: SøknadService,
    val brev: BrevService,
    val lukkSøknad: LukkSøknadService,
    val oppgave: OppgaveService,
    val person: PersonService,
    val toggles: ToggleClient,
    val søknadsbehandling: SøknadsbehandlingServices,
    val ferdigstillVedtak: FerdigstillVedtakService,
    val revurdering: RevurderingService,
    val stansYtelse: StansYtelseService,
    val gjenopptaYtelse: GjenopptaYtelseService,
    val vedtakService: VedtakService,
    val nøkkeltallService: NøkkeltallService,
    val avslåSøknadManglendeDokumentasjonService: AvslåSøknadManglendeDokumentasjonService,
    val klageService: KlageService,
    val klageinstanshendelseService: KlageinstanshendelseService,
    val reguleringService: ReguleringService,
    val tilbakekrevingService: TilbakekrevingService,
    val sendPåminnelserOmNyStønadsperiodeService: SendPåminnelserOmNyStønadsperiodeService,
    val skatteService: SkatteService,
    val kontrollsamtaleSetup: KontrollsamtaleSetup,
)
