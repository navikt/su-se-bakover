package no.nav.su.se.bakover.service.revurdering

import com.nhaarman.mockitokotlin2.mock
import no.nav.su.se.bakover.client.person.MicrosoftGraphApiClient
import no.nav.su.se.bakover.database.revurdering.RevurderingRepo
import no.nav.su.se.bakover.database.vedtak.VedtakRepo
import no.nav.su.se.bakover.service.brev.BrevService
import no.nav.su.se.bakover.service.fixedClock
import no.nav.su.se.bakover.service.oppgave.OppgaveService
import no.nav.su.se.bakover.service.person.PersonService
import no.nav.su.se.bakover.service.sak.SakService
import no.nav.su.se.bakover.service.utbetaling.UtbetalingService
import no.nav.su.se.bakover.service.vedtak.FerdigstillVedtakService

internal data class RevurderingServiceMocks(
    val sakService: SakService = mock(),
    val utbetalingService: UtbetalingService = mock(),
    val revurderingRepo: RevurderingRepo = mock(),
    val oppgaveService: OppgaveService = mock(),
    val personService: PersonService = mock(),
    val microsoftGraphApiClient: MicrosoftGraphApiClient = mock(),
    val brevService: BrevService = mock(),
    val vedtakRepo: VedtakRepo = mock(),
    val ferdigstillVedtakService: FerdigstillVedtakService = mock(),
) {
    val revurderingService = RevurderingServiceImpl(
        sakService = sakService,
        utbetalingService = utbetalingService,
        revurderingRepo = revurderingRepo,
        oppgaveService = oppgaveService,
        personService = personService,
        microsoftGraphApiClient = microsoftGraphApiClient,
        brevService = brevService,
        vedtakRepo = vedtakRepo,
        ferdigstillVedtakService = ferdigstillVedtakService,
        clock = fixedClock,
    )

    fun verifyNoMoreInteractions() {
        com.nhaarman.mockitokotlin2.verifyNoMoreInteractions(
            sakService,
            utbetalingService,
            revurderingRepo,
            oppgaveService,
            personService,
            microsoftGraphApiClient,
            brevService,
            vedtakRepo,
            ferdigstillVedtakService,
        )
    }
}
