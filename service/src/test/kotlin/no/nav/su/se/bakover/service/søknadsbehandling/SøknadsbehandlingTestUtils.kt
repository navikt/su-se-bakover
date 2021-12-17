package no.nav.su.se.bakover.service.søknadsbehandling

import no.nav.su.se.bakover.common.idag
import no.nav.su.se.bakover.domain.behandling.BehandlingMetrics
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingRepo
import no.nav.su.se.bakover.domain.vedtak.VedtakRepo
import no.nav.su.se.bakover.service.behandling.BehandlingTestUtils.fnr
import no.nav.su.se.bakover.service.beregning.TestBeregning
import no.nav.su.se.bakover.service.brev.BrevService
import no.nav.su.se.bakover.service.grunnlag.GrunnlagService
import no.nav.su.se.bakover.service.grunnlag.VilkårsvurderingService
import no.nav.su.se.bakover.service.kontrollsamtale.KontrollsamtaleService
import no.nav.su.se.bakover.service.oppgave.OppgaveService
import no.nav.su.se.bakover.service.person.PersonService
import no.nav.su.se.bakover.service.sak.SakService
import no.nav.su.se.bakover.service.statistikk.EventObserver
import no.nav.su.se.bakover.service.søknad.SøknadService
import no.nav.su.se.bakover.service.utbetaling.UtbetalingService
import no.nav.su.se.bakover.service.vedtak.FerdigstillVedtakService
import no.nav.su.se.bakover.test.fixedClock
import org.mockito.kotlin.mock
import java.time.Clock

internal val testBeregning = TestBeregning

internal val simulering = Simulering(
    gjelderId = fnr,
    gjelderNavn = "NAVN",
    datoBeregnet = idag(fixedClock),
    nettoBeløp = 191500,
    periodeList = listOf(),
)

internal fun createSøknadsbehandlingService(
    søknadsbehandlingRepo: SøknadsbehandlingRepo = mock(),
    utbetalingService: UtbetalingService = mock(),
    oppgaveService: OppgaveService = mock(),
    søknadService: SøknadService = mock(),
    personService: PersonService = mock(),
    behandlingMetrics: BehandlingMetrics = mock(),
    observer: EventObserver = mock(),
    brevService: BrevService = mock(),
    clock: Clock = fixedClock,
    vedtakRepo: VedtakRepo = mock(),
    ferdigstillVedtakService: FerdigstillVedtakService = mock(),
    grunnlagService: GrunnlagService = mock(),
    sakService: SakService = mock(),
    kontrollsamtaleService: KontrollsamtaleService = mock(),
) = SøknadsbehandlingServiceImpl(
    søknadService = søknadService,
    søknadsbehandlingRepo = søknadsbehandlingRepo,
    utbetalingService = utbetalingService,
    personService = personService,
    oppgaveService = oppgaveService,
    behandlingMetrics = behandlingMetrics,
    brevService = brevService,
    clock = clock,
    vedtakRepo = vedtakRepo,
    ferdigstillVedtakService = ferdigstillVedtakService,
    grunnlagService = grunnlagService,
    sakService = sakService,
    kontrollsamtaleService,
).apply { addObserver(observer) }

internal data class SøknadsbehandlingServiceAndMocks(
    val søknadsbehandlingRepo: SøknadsbehandlingRepo = mock(),
    val utbetalingService: UtbetalingService = mock(),
    val oppgaveService: OppgaveService = mock(),
    val søknadService: SøknadService = mock(),
    val personService: PersonService = mock(),
    val behandlingMetrics: BehandlingMetrics = mock(),
    val observer: EventObserver = mock(),
    val brevService: BrevService = mock(),
    val clock: Clock = fixedClock,
    val vedtakRepo: VedtakRepo = mock(),
    val ferdigstillVedtakService: FerdigstillVedtakService = mock(),
    val vilkårsvurderingService: VilkårsvurderingService = mock(),
    val grunnlagService: GrunnlagService = mock(),
    val sakService: SakService = mock(),
    val kontrollsamtaleService: KontrollsamtaleService = mock(),
) {
    val søknadsbehandlingService = SøknadsbehandlingServiceImpl(
        søknadService = søknadService,
        søknadsbehandlingRepo = søknadsbehandlingRepo,
        utbetalingService = utbetalingService,
        personService = personService,
        oppgaveService = oppgaveService,
        behandlingMetrics = behandlingMetrics,
        brevService = brevService,
        clock = clock,
        vedtakRepo = vedtakRepo,
        ferdigstillVedtakService = ferdigstillVedtakService,
        grunnlagService = grunnlagService,
        sakService = sakService,
        kontrollsamtaleService = kontrollsamtaleService,
    ).apply { addObserver(observer) }

    fun allMocks(): Array<Any> = listOf(
        søknadsbehandlingRepo,
        utbetalingService,
        oppgaveService,
        søknadService,
        personService,
        behandlingMetrics,
        observer,
        brevService,
        vedtakRepo,
        ferdigstillVedtakService,
        vilkårsvurderingService,
        grunnlagService,
        sakService,
        kontrollsamtaleService,
    ).toTypedArray()

    fun verifyNoMoreInteractions() {
        org.mockito.kotlin.verifyNoMoreInteractions(
            søknadsbehandlingRepo,
            utbetalingService,
            oppgaveService,
            søknadService,
            personService,
            behandlingMetrics,
            observer,
            brevService,
            vedtakRepo,
            ferdigstillVedtakService,
            vilkårsvurderingService,
            grunnlagService,
            sakService,
            kontrollsamtaleService,
        )
    }
}
