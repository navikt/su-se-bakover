package no.nav.su.se.bakover.service.revurdering

import com.nhaarman.mockitokotlin2.mock
import no.nav.su.se.bakover.client.person.MicrosoftGraphApiOppslag
import no.nav.su.se.bakover.database.revurdering.RevurderingRepo
import no.nav.su.se.bakover.database.vedtak.VedtakRepo
import no.nav.su.se.bakover.service.brev.BrevService
import no.nav.su.se.bakover.service.grunnlag.GrunnlagService
import no.nav.su.se.bakover.service.grunnlag.VilkårsvurderingService
import no.nav.su.se.bakover.service.oppgave.OppgaveService
import no.nav.su.se.bakover.service.person.PersonService
import no.nav.su.se.bakover.service.utbetaling.UtbetalingService
import no.nav.su.se.bakover.service.vedtak.FerdigstillVedtakService
import no.nav.su.se.bakover.service.vedtak.VedtakService
import no.nav.su.se.bakover.test.fixedClock

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
) {
    val revurderingService = RevurderingServiceImpl(
        vedtakService = vedtakService,
        utbetalingService = utbetalingService,
        revurderingRepo = revurderingRepo,
        oppgaveService = oppgaveService,
        personService = personService,
        microsoftGraphApiClient = microsoftGraphApiClient,
        brevService = brevService,
        vedtakRepo = vedtakRepo,
        ferdigstillVedtakService = ferdigstillVedtakService,
        vilkårsvurderingService = vilkårsvurderingService,
        grunnlagService = grunnlagService,
        clock = fixedClock,
    )

    fun verifyNoMoreInteractions() {
        com.nhaarman.mockitokotlin2.verifyNoMoreInteractions(
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
        )
    }
}
