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
import no.nav.su.se.bakover.service.toggles.ToggleService
import no.nav.su.se.bakover.service.utbetaling.UtbetalingService
import no.nav.su.se.bakover.service.vedtak.FerdigstillVedtakService
import no.nav.su.se.bakover.service.vedtak.VedtakService
import no.nav.su.se.bakover.test.TestSessionFactory
import no.nav.su.se.bakover.test.fixedClock
import org.mockito.kotlin.mock

internal data class RevurderingServiceMocks(
    val vedtakService: VedtakService = mock(),
    val utbetalingService: UtbetalingService = mock(),
    val revurderingRepo: RevurderingRepo = mock(),
    val oppgaveService: OppgaveService = mock(),
    val personService: PersonService = mock(),
    val identClient: IdentClient = mock(),
    val brevService: BrevService = mock(),
    val vedtakRepo: VedtakRepo = mock(),
    val ferdigstillVedtakService: FerdigstillVedtakService = mock(),
    val grunnlagService: GrunnlagService = mock(),
    val vilkårsvurderingService: VilkårsvurderingService = mock(),
    val sakService: SakService = mock(),
    val kontrollsamtaleService: KontrollsamtaleService = mock(),
    val sessionFactory: SessionFactory = TestSessionFactory(),
    val avkortingsvarselRepo: AvkortingsvarselRepo = mock(),
    val toggleService: ToggleService = mock(),
) {
    val revurderingService = RevurderingServiceImpl(
        utbetalingService = utbetalingService,
        revurderingRepo = revurderingRepo,
        oppgaveService = oppgaveService,
        personService = personService,
        identClient = identClient,
        brevService = brevService,
        clock = fixedClock,
        vedtakRepo = vedtakRepo,
        vilkårsvurderingService = vilkårsvurderingService,
        grunnlagService = grunnlagService,
        vedtakService = vedtakService,
        sakService = sakService,
        kontrollsamtaleService = kontrollsamtaleService,
        sessionFactory = sessionFactory,
        avkortingsvarselRepo = avkortingsvarselRepo,
        toggleService = toggleService,
    )

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
    ).toTypedArray()

    fun verifyNoMoreInteractions() {
        org.mockito.kotlin.verifyNoMoreInteractions(
            *all(),
        )
    }
}
