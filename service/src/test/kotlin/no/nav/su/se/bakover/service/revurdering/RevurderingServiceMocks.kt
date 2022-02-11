package no.nav.su.se.bakover.service.revurdering

import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.domain.avkorting.AvkortingsvarselRepo
import no.nav.su.se.bakover.domain.person.IdentClient
import no.nav.su.se.bakover.domain.revurdering.RevurderingRepo
import no.nav.su.se.bakover.domain.vedtak.VedtakRepo
import no.nav.su.se.bakover.service.brev.BrevService
import no.nav.su.se.bakover.service.grunnlag.GrunnlagService
import no.nav.su.se.bakover.service.grunnlag.VilkårsvurderingService
import no.nav.su.se.bakover.service.kontrollsamtale.KontrollsamtaleService
import no.nav.su.se.bakover.service.oppgave.OppgaveService
import no.nav.su.se.bakover.service.person.PersonService
import no.nav.su.se.bakover.service.sak.SakService
import no.nav.su.se.bakover.service.statistikk.EventObserver
import no.nav.su.se.bakover.service.tilbakekreving.TilbakekrevingService
import no.nav.su.se.bakover.service.toggles.ToggleService
import no.nav.su.se.bakover.service.utbetaling.UtbetalingService
import no.nav.su.se.bakover.service.vedtak.FerdigstillVedtakService
import no.nav.su.se.bakover.service.vedtak.VedtakService
import no.nav.su.se.bakover.test.TestSessionFactory
import no.nav.su.se.bakover.test.defaultMock
import no.nav.su.se.bakover.test.fixedClock
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
    val grunnlagService: GrunnlagService = defaultMock(),
    val vilkårsvurderingService: VilkårsvurderingService = defaultMock(),
    val sakService: SakService = defaultMock(),
    val kontrollsamtaleService: KontrollsamtaleService = defaultMock(),
    val avkortingsvarselRepo: AvkortingsvarselRepo = defaultMock(),
    val sessionFactory: SessionFactory = TestSessionFactory(),
    val observer: EventObserver = mock(),
    val toggleService: ToggleService = mock(),
    val clock: Clock = fixedClock,
    val tilbakekrevingService: TilbakekrevingService = defaultMock()
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
        vilkårsvurderingService = vilkårsvurderingService,
        grunnlagService = grunnlagService,
        vedtakService = vedtakService,
        sakService = sakService,
        kontrollsamtaleService = kontrollsamtaleService,
        sessionFactory = sessionFactory,
        avkortingsvarselRepo = avkortingsvarselRepo,
        toggleService = toggleService,
        tilbakekrevingService = tilbakekrevingService,
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
        grunnlagService,
        vilkårsvurderingService,
        sakService,
        avkortingsvarselRepo,
        kontrollsamtaleService,
        tilbakekrevingService,
    ).toTypedArray()

    fun verifyNoMoreInteractions() {
        org.mockito.kotlin.verifyNoMoreInteractions(
            *all(),
        )
    }
}
