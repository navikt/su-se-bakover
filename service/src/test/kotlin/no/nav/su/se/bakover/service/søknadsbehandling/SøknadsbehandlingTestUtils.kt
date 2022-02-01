package no.nav.su.se.bakover.service.søknadsbehandling

import no.nav.su.se.bakover.common.idag
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.domain.avkorting.AvkortingsvarselRepo
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
import no.nav.su.se.bakover.test.TestSessionFactory
import no.nav.su.se.bakover.test.defaultMock
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
    sessionFactory: SessionFactory = TestSessionFactory(),
    avkortingsvarselRepo: AvkortingsvarselRepo = mock(),
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
    kontrollsamtaleService = kontrollsamtaleService,
    sessionFactory = sessionFactory,
    avkortingsvarselRepo
).apply { addObserver(observer) }

internal data class SøknadsbehandlingServiceAndMocks(
    val søknadsbehandlingRepo: SøknadsbehandlingRepo = defaultMock(),
    val utbetalingService: UtbetalingService = defaultMock(),
    val oppgaveService: OppgaveService = defaultMock(),
    val søknadService: SøknadService = defaultMock(),
    val personService: PersonService = defaultMock(),
    val behandlingMetrics: BehandlingMetrics = mock(),
    val observer: EventObserver = mock(),
    val brevService: BrevService = defaultMock(),
    val clock: Clock = fixedClock,
    val vedtakRepo: VedtakRepo = defaultMock(),
    val ferdigstillVedtakService: FerdigstillVedtakService = defaultMock(),
    val vilkårsvurderingService: VilkårsvurderingService = defaultMock(),
    val grunnlagService: GrunnlagService = defaultMock(),
    val sakService: SakService = defaultMock(),
    val kontrollsamtaleService: KontrollsamtaleService = defaultMock(),
    val sessionFactory: SessionFactory = TestSessionFactory(),
    val avkortingsvarselRepo: AvkortingsvarselRepo = mock(),
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
        sessionFactory = sessionFactory,
        avkortingsvarselRepo = avkortingsvarselRepo,
    ).apply { addObserver(observer) }

    fun allMocks(): Array<Any> {
        return listOf(
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
            avkortingsvarselRepo
        ).toTypedArray()
    }

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
            avkortingsvarselRepo
        )
    }
}
