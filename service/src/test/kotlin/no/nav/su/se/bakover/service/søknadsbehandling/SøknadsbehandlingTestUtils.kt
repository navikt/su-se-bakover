package no.nav.su.se.bakover.service.søknadsbehandling

import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.domain.avkorting.AvkortingsvarselRepo
import no.nav.su.se.bakover.domain.behandling.BehandlingMetrics
import no.nav.su.se.bakover.domain.person.PersonService
import no.nav.su.se.bakover.domain.satser.SatsFactory
import no.nav.su.se.bakover.domain.statistikk.StatistikkEventObserver
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingRepo
import no.nav.su.se.bakover.domain.vedtak.VedtakRepo
import no.nav.su.se.bakover.domain.vilkår.FormuegrenserFactory
import no.nav.su.se.bakover.service.brev.BrevService
import no.nav.su.se.bakover.service.kontrollsamtale.KontrollsamtaleService
import no.nav.su.se.bakover.service.oppgave.OppgaveService
import no.nav.su.se.bakover.service.sak.SakService
import no.nav.su.se.bakover.service.søknad.SøknadService
import no.nav.su.se.bakover.service.tilbakekreving.TilbakekrevingService
import no.nav.su.se.bakover.service.utbetaling.UtbetalingService
import no.nav.su.se.bakover.service.vedtak.FerdigstillVedtakService
import no.nav.su.se.bakover.test.TestSessionFactory
import no.nav.su.se.bakover.test.defaultMock
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.formuegrenserFactoryTestPåDato
import no.nav.su.se.bakover.test.satsFactoryTestPåDato
import org.mockito.kotlin.mock
import java.time.Clock

internal fun createSøknadsbehandlingService(
    søknadsbehandlingRepo: SøknadsbehandlingRepo = mock(),
    utbetalingService: UtbetalingService = mock(),
    oppgaveService: OppgaveService = mock(),
    søknadService: SøknadService = mock(),
    personService: PersonService = mock(),
    behandlingMetrics: BehandlingMetrics = mock(),
    observer: StatistikkEventObserver = mock(),
    brevService: BrevService = mock(),
    clock: Clock = fixedClock,
    vedtakRepo: VedtakRepo = mock(),
    ferdigstillVedtakService: FerdigstillVedtakService = mock(),
    sakService: SakService = mock(),
    kontrollsamtaleService: KontrollsamtaleService = mock(),
    sessionFactory: SessionFactory = TestSessionFactory(),
    avkortingsvarselRepo: AvkortingsvarselRepo = mock(),
    tilbakekrevingService: TilbakekrevingService = mock(),
    formuegrenserFactory: FormuegrenserFactory = formuegrenserFactoryTestPåDato(),
    satsFactory: SatsFactory = satsFactoryTestPåDato(),
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
    sakService = sakService,
    kontrollsamtaleService = kontrollsamtaleService,
    sessionFactory = sessionFactory,
    avkortingsvarselRepo = avkortingsvarselRepo,
    tilbakekrevingService = tilbakekrevingService,
    formuegrenserFactory = formuegrenserFactory,
    satsFactory = satsFactory,
).apply { addObserver(observer) }

internal data class SøknadsbehandlingServiceAndMocks(
    val søknadsbehandlingRepo: SøknadsbehandlingRepo = defaultMock(),
    val utbetalingService: UtbetalingService = defaultMock(),
    val oppgaveService: OppgaveService = defaultMock(),
    val søknadService: SøknadService = defaultMock(),
    val personService: PersonService = defaultMock(),
    val behandlingMetrics: BehandlingMetrics = mock(),
    val observer: StatistikkEventObserver = mock(),
    val brevService: BrevService = defaultMock(),
    val clock: Clock = fixedClock,
    val vedtakRepo: VedtakRepo = defaultMock(),
    val ferdigstillVedtakService: FerdigstillVedtakService = defaultMock(),
    val sakService: SakService = defaultMock(),
    val kontrollsamtaleService: KontrollsamtaleService = defaultMock(),
    val sessionFactory: SessionFactory = TestSessionFactory(),
    val avkortingsvarselRepo: AvkortingsvarselRepo = mock(),
    val tilbakekrevingService: TilbakekrevingService = defaultMock(),
    val formuegrenserFactory: FormuegrenserFactory = formuegrenserFactoryTestPåDato(),
    val satsFactory: SatsFactory = satsFactoryTestPåDato(),
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
        sakService = sakService,
        kontrollsamtaleService = kontrollsamtaleService,
        sessionFactory = sessionFactory,
        avkortingsvarselRepo = avkortingsvarselRepo,
        tilbakekrevingService = tilbakekrevingService,
        formuegrenserFactory = formuegrenserFactory,
        satsFactory = satsFactory,
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
            sakService,
            kontrollsamtaleService,
            avkortingsvarselRepo,
            tilbakekrevingService,
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
            sakService,
            kontrollsamtaleService,
            avkortingsvarselRepo,
            tilbakekrevingService,
        )
    }
}
