package no.nav.su.se.bakover.service.revurdering

import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.common.toggle.domain.ToggleClient
import no.nav.su.se.bakover.domain.avkorting.AvkortingsvarselRepo
import no.nav.su.se.bakover.domain.brev.BrevService
import no.nav.su.se.bakover.domain.oppgave.OppgaveService
import no.nav.su.se.bakover.domain.person.IdentClient
import no.nav.su.se.bakover.domain.person.PersonService
import no.nav.su.se.bakover.domain.revurdering.RevurderingRepo
import no.nav.su.se.bakover.domain.revurdering.opphør.AnnullerKontrollsamtaleVedOpphørService
import no.nav.su.se.bakover.domain.sak.SakService
import no.nav.su.se.bakover.domain.satser.SatsFactory
import no.nav.su.se.bakover.domain.statistikk.StatistikkEventObserver
import no.nav.su.se.bakover.domain.vedtak.VedtakRepo
import no.nav.su.se.bakover.service.tilbakekreving.TilbakekrevingService
import no.nav.su.se.bakover.service.utbetaling.UtbetalingService
import no.nav.su.se.bakover.service.vedtak.FerdigstillVedtakService
import no.nav.su.se.bakover.service.vedtak.VedtakService
import no.nav.su.se.bakover.test.TestSessionFactory
import no.nav.su.se.bakover.test.defaultMock
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.formuegrenserFactoryTestPåDato
import no.nav.su.se.bakover.test.satsFactoryTestPåDato
import org.mockito.kotlin.mock
import java.time.Clock

internal data class RevurderingServiceMocks(
    val vedtakService: VedtakService = defaultMock(),
    val utbetalingService: UtbetalingService = defaultMock(),
    val revurderingRepo: RevurderingRepo = defaultMock(),
    val oppgaveService: OppgaveService = defaultMock(),
    val personService: PersonService = defaultMock(),
    val identClient: IdentClient = defaultMock(),
    val brevService: BrevService = defaultMock(),
    val vedtakRepo: VedtakRepo = defaultMock(),
    val ferdigstillVedtakService: FerdigstillVedtakService = defaultMock(),
    val sakService: SakService = defaultMock(),
    val annullerKontrollsamtaleService: AnnullerKontrollsamtaleVedOpphørService = defaultMock(),
    val avkortingsvarselRepo: AvkortingsvarselRepo = defaultMock(),
    val sessionFactory: SessionFactory = TestSessionFactory(),
    val observer: StatistikkEventObserver = mock(),
    val toggleService: ToggleClient = mock(),
    val clock: Clock = fixedClock,
    val tilbakekrevingService: TilbakekrevingService = defaultMock(),
    val satsFactory: SatsFactory = satsFactoryTestPåDato(),
) {
    val revurderingService = RevurderingServiceImpl(
        utbetalingService = utbetalingService,
        revurderingRepo = revurderingRepo,
        oppgaveService = oppgaveService,
        personService = personService,
        identClient = identClient,
        brevService = brevService,
        clock = clock,
        vedtakRepo = vedtakRepo,
        annullerKontrollsamtaleService = annullerKontrollsamtaleService,
        sessionFactory = sessionFactory,
        formuegrenserFactory = formuegrenserFactoryTestPåDato(),
        sakService = sakService,
        tilbakekrevingService = tilbakekrevingService,
        satsFactory = satsFactory,
    ).apply { addObserver(observer) }

    fun all() = listOf(
        vedtakService,
        utbetalingService,
        revurderingRepo,
        oppgaveService,
        personService,
        identClient,
        brevService,
        vedtakRepo,
        ferdigstillVedtakService,
        sakService,
        avkortingsvarselRepo,
        annullerKontrollsamtaleService,
        tilbakekrevingService,
    ).toTypedArray()

    fun verifyNoMoreInteractions() {
        org.mockito.kotlin.verifyNoMoreInteractions(
            *all(),
        )
    }
}
