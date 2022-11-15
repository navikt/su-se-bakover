package no.nav.su.se.bakover.service.søknadsbehandling

import no.nav.su.se.bakover.domain.behandling.BehandlingMetrics
import no.nav.su.se.bakover.domain.brev.BrevService
import no.nav.su.se.bakover.domain.oppgave.OppgaveService
import no.nav.su.se.bakover.domain.person.PersonService
import no.nav.su.se.bakover.domain.sak.SakService
import no.nav.su.se.bakover.domain.satser.SatsFactory
import no.nav.su.se.bakover.domain.statistikk.StatistikkEventObserver
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingRepo
import no.nav.su.se.bakover.domain.vilkår.FormuegrenserFactory
import no.nav.su.se.bakover.service.tilbakekreving.TilbakekrevingService
import no.nav.su.se.bakover.service.utbetaling.UtbetalingService
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
    personService: PersonService = mock(),
    behandlingMetrics: BehandlingMetrics = mock(),
    observer: StatistikkEventObserver = mock(),
    brevService: BrevService = mock(),
    clock: Clock = fixedClock,
    sakService: SakService = mock(),
    tilbakekrevingService: TilbakekrevingService = mock(),
    formuegrenserFactory: FormuegrenserFactory = formuegrenserFactoryTestPåDato(),
    satsFactory: SatsFactory = satsFactoryTestPåDato(),
) = SøknadsbehandlingServiceImpl(
    søknadsbehandlingRepo = søknadsbehandlingRepo,
    utbetalingService = utbetalingService,
    personService = personService,
    oppgaveService = oppgaveService,
    behandlingMetrics = behandlingMetrics,
    brevService = brevService,
    clock = clock,
    sakService = sakService,
    tilbakekrevingService = tilbakekrevingService,
    formuegrenserFactory = formuegrenserFactory,
    satsFactory = satsFactory,
).apply { addObserver(observer) }

internal data class SøknadsbehandlingServiceAndMocks(
    val søknadsbehandlingRepo: SøknadsbehandlingRepo = defaultMock(),
    val utbetalingService: UtbetalingService = defaultMock(),
    val oppgaveService: OppgaveService = defaultMock(),
    val personService: PersonService = defaultMock(),
    val behandlingMetrics: BehandlingMetrics = mock(),
    val observer: StatistikkEventObserver = mock(),
    val brevService: BrevService = defaultMock(),
    val clock: Clock = fixedClock,
    val sakService: SakService = defaultMock(),
    val tilbakekrevingService: TilbakekrevingService = defaultMock(),
    val formuegrenserFactory: FormuegrenserFactory = formuegrenserFactoryTestPåDato(),
    val satsFactory: SatsFactory = satsFactoryTestPåDato(),
) {
    val søknadsbehandlingService = SøknadsbehandlingServiceImpl(
        søknadsbehandlingRepo = søknadsbehandlingRepo,
        utbetalingService = utbetalingService,
        personService = personService,
        oppgaveService = oppgaveService,
        behandlingMetrics = behandlingMetrics,
        brevService = brevService,
        clock = clock,
        sakService = sakService,
        tilbakekrevingService = tilbakekrevingService,
        formuegrenserFactory = formuegrenserFactory,
        satsFactory = satsFactory,
    ).apply { addObserver(observer) }

    fun allMocks(): Array<Any> {
        return listOf(
            søknadsbehandlingRepo,
            utbetalingService,
            oppgaveService,
            personService,
            behandlingMetrics,
            observer,
            brevService,
            sakService,
            tilbakekrevingService,
        ).toTypedArray()
    }

    fun verifyNoMoreInteractions() {
        org.mockito.kotlin.verifyNoMoreInteractions(
            søknadsbehandlingRepo,
            utbetalingService,
            oppgaveService,
            personService,
            behandlingMetrics,
            observer,
            brevService,
            sakService,
            tilbakekrevingService,
        )
    }
}
