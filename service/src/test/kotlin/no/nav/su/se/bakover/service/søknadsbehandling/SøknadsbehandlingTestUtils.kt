package no.nav.su.se.bakover.service.søknadsbehandling

import no.nav.su.se.bakover.client.person.MicrosoftGraphApiOppslag
import no.nav.su.se.bakover.common.idag
import no.nav.su.se.bakover.database.søknad.SøknadRepo
import no.nav.su.se.bakover.database.søknadsbehandling.SøknadsbehandlingRepo
import no.nav.su.se.bakover.database.vedtak.VedtakRepo
import no.nav.su.se.bakover.domain.behandling.BehandlingMetrics
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.service.behandling.BehandlingTestUtils.fnr
import no.nav.su.se.bakover.service.beregning.TestBeregning
import no.nav.su.se.bakover.service.brev.BrevService
import no.nav.su.se.bakover.service.fixedClock
import no.nav.su.se.bakover.service.grunnlag.GrunnlagService
import no.nav.su.se.bakover.service.grunnlag.VilkårsvurderingService
import no.nav.su.se.bakover.service.oppgave.OppgaveService
import no.nav.su.se.bakover.service.person.PersonService
import no.nav.su.se.bakover.service.sak.SakService
import no.nav.su.se.bakover.service.statistikk.EventObserver
import no.nav.su.se.bakover.service.søknad.SøknadService
import no.nav.su.se.bakover.service.utbetaling.UtbetalingService
import no.nav.su.se.bakover.service.vedtak.FerdigstillVedtakService
import no.nav.su.se.bakover.service.vedtak.snapshot.OpprettVedtakssnapshotService
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
    søknadRepo: SøknadRepo = mock(),
    personService: PersonService = mock(),
    behandlingMetrics: BehandlingMetrics = mock(),
    observer: EventObserver = mock(),
    microsoftGraphApiOppslag: MicrosoftGraphApiOppslag = mock(),
    brevService: BrevService = mock(),
    opprettVedtakssnapshotService: OpprettVedtakssnapshotService = mock(),
    clock: Clock = Clock.systemUTC(),
    vedtakRepo: VedtakRepo = mock(),
    ferdigstillVedtakService: FerdigstillVedtakService = mock(),
    vilkårsvurderingService: VilkårsvurderingService = mock(),
    grunnlagService: GrunnlagService = mock(),
    sakService: SakService = mock(),
) = SøknadsbehandlingServiceImpl(
    søknadService,
    søknadRepo,
    søknadsbehandlingRepo,
    utbetalingService,
    personService,
    oppgaveService,
    behandlingMetrics,
    microsoftGraphApiOppslag,
    brevService,
    opprettVedtakssnapshotService,
    clock,
    vedtakRepo,
    ferdigstillVedtakService,
    vilkårsvurderingService,
    grunnlagService,
    sakService,
).apply { addObserver(observer) }

internal data class SøknadsbehandlingServiceAndMocks(
    val søknadsbehandlingRepo: SøknadsbehandlingRepo = mock(),
    val utbetalingService: UtbetalingService = mock(),
    val oppgaveService: OppgaveService = mock(),
    val søknadService: SøknadService = mock(),
    val søknadRepo: SøknadRepo = mock(),
    val personService: PersonService = mock(),
    val behandlingMetrics: BehandlingMetrics = mock(),
    val observer: EventObserver = mock(),
    val microsoftGraphApiOppslag: MicrosoftGraphApiOppslag = mock(),
    val brevService: BrevService = mock(),
    val opprettVedtakssnapshotService: OpprettVedtakssnapshotService = mock(),
    val clock: Clock = no.nav.su.se.bakover.test.fixedClock,
    val vedtakRepo: VedtakRepo = mock(),
    val ferdigstillVedtakService: FerdigstillVedtakService = mock(),
    val vilkårsvurderingService: VilkårsvurderingService = mock(),
    val grunnlagService: GrunnlagService = mock(),
    val sakService: SakService = mock()
) {
    val søknadsbehandlingService = SøknadsbehandlingServiceImpl(
        søknadService = søknadService,
        søknadRepo = søknadRepo,
        søknadsbehandlingRepo = søknadsbehandlingRepo,
        utbetalingService = utbetalingService,
        personService = personService,
        oppgaveService = oppgaveService,
        behandlingMetrics = behandlingMetrics,
        microsoftGraphApiClient = microsoftGraphApiOppslag,
        brevService = brevService,
        opprettVedtakssnapshotService = opprettVedtakssnapshotService,
        clock = clock,
        vedtakRepo = vedtakRepo,
        ferdigstillVedtakService = ferdigstillVedtakService,
        vilkårsvurderingService = vilkårsvurderingService,
        grunnlagService = grunnlagService,
        sakService = sakService,
    ).apply { addObserver(observer) }

    fun all(): List<Any> {
        return listOf(
            søknadsbehandlingRepo,
            utbetalingService,
            oppgaveService,
            søknadService,
            søknadRepo,
            personService,
            behandlingMetrics,
            observer,
            microsoftGraphApiOppslag,
            brevService,
            opprettVedtakssnapshotService,
            vedtakRepo,
            ferdigstillVedtakService,
            vilkårsvurderingService,
            grunnlagService,
            sakService,
        )
    }

    fun verifyNoMoreInteractions() {
        org.mockito.kotlin.verifyNoMoreInteractions(
            søknadsbehandlingRepo,
            utbetalingService,
            oppgaveService,
            søknadService,
            søknadRepo,
            personService,
            behandlingMetrics,
            observer,
            microsoftGraphApiOppslag,
            brevService,
            opprettVedtakssnapshotService,
            vedtakRepo,
            ferdigstillVedtakService,
            vilkårsvurderingService,
            grunnlagService,
            sakService,
        )
    }
}
