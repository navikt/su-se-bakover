package no.nav.su.se.bakover.service.revurdering

import no.nav.su.se.bakover.client.person.MicrosoftGraphApiOppslag
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.database.revurdering.RevurderingRepo
import no.nav.su.se.bakover.database.vedtak.VedtakRepo
import no.nav.su.se.bakover.service.brev.BrevService
import no.nav.su.se.bakover.service.grunnlag.GrunnlagService
import no.nav.su.se.bakover.service.grunnlag.VilkårsvurderingService
import no.nav.su.se.bakover.service.oppgave.OppgaveService
import no.nav.su.se.bakover.service.person.PersonService
import no.nav.su.se.bakover.service.sak.SakService
import no.nav.su.se.bakover.service.utbetaling.UtbetalingService
import no.nav.su.se.bakover.service.vedtak.FerdigstillVedtakService
import no.nav.su.se.bakover.service.vedtak.VedtakService
import no.nav.su.se.bakover.test.fixedClock
import org.mockito.kotlin.mock

internal data class RevurderingServiceMocks(
    val vedtakService: VedtakService = mock(),
    val utbetalingService: UtbetalingService = mock(),
    val revurderingRepo: RevurderingRepo = mock(),
    val oppgaveService: OppgaveService = mock(),
    val personService: PersonService = mock(),
    val microsoftGraphApiClient: MicrosoftGraphApiOppslag = mock(),
    val brevService: BrevService = mock(),
    val vedtakRepo: VedtakRepo = mock(),
    val ferdigstillVedtakService: FerdigstillVedtakService = mock(),
    val grunnlagService: GrunnlagService = mock(),
    val vilkårsvurderingService: VilkårsvurderingService = mock(),
    val sakService: SakService = mock(),
    val sessionFactory: SessionFactory = mock(),
) {
    val revurderingService = RevurderingServiceImpl(
        utbetalingService = utbetalingService,
        revurderingRepo = revurderingRepo,
        oppgaveService = oppgaveService,
        personService = personService,
        microsoftGraphApiClient = microsoftGraphApiClient,
        brevService = brevService,
        clock = fixedClock,
        vedtakRepo = vedtakRepo,
        vilkårsvurderingService = vilkårsvurderingService,
        grunnlagService = grunnlagService,
        vedtakService = vedtakService,
        sakService = sakService,
        sessionFactory = sessionFactory
    )

    fun all() = listOf(
        vedtakService,
        utbetalingService,
        revurderingRepo,
        oppgaveService,
        personService,
        microsoftGraphApiClient,
        brevService,
        vedtakRepo,
        ferdigstillVedtakService,
        grunnlagService,
        vilkårsvurderingService,
        sakService,
    ).toTypedArray()

    fun verifyNoMoreInteractions() {
        org.mockito.kotlin.verifyNoMoreInteractions(
            *all(),
        )
    }
}
