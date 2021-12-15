package no.nav.su.se.bakover.service

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

data class Services(
    val avstemming: AvstemmingService,
    val utbetaling: UtbetalingService,
    val sak: SakService,
    val søknad: SøknadService,
    val brev: BrevService,
    val lukkSøknad: LukkSøknadService,
    val oppgave: OppgaveService,
    val person: PersonService,
    val statistikk: StatistikkService,
    val toggles: ToggleService,
    val søknadsbehandling: SøknadsbehandlingService,
    val grunnlagService: GrunnlagService,
    val ferdigstillVedtak: FerdigstillVedtakService,
    val revurdering: RevurderingService,
    val vedtakService: VedtakService,
    val nøkkeltallService: NøkkeltallService,
    val avslåSøknadManglendeDokumentasjonService: AvslåSøknadManglendeDokumentasjonService,
    val kontrollsamtale: KontrollsamtaleService,
    val klageService: KlageService,
    val klagevedtakService: KlagevedtakService,
)
