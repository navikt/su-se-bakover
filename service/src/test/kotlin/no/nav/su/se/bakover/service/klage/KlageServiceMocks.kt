package no.nav.su.se.bakover.service.klage

import no.nav.su.se.bakover.client.kabal.KlageClient
import no.nav.su.se.bakover.client.person.MicrosoftGraphApiOppslag
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.database.sak.SakRepo
import no.nav.su.se.bakover.database.vedtak.VedtakRepo
import no.nav.su.se.bakover.domain.klage.KlageRepo
import no.nav.su.se.bakover.service.brev.BrevService
import no.nav.su.se.bakover.service.oppgave.OppgaveService
import no.nav.su.se.bakover.service.person.PersonService
import no.nav.su.se.bakover.test.TestSessionFactory
import no.nav.su.se.bakover.test.fixedClock
import org.mockito.kotlin.mock
import java.time.Clock

internal data class KlageServiceMocks(
    val sakRepoMock: SakRepo = mock(),
    val klageRepoMock: KlageRepo = mock(),
    val vedtakRepoMock: VedtakRepo = mock(),
    val brevServiceMock: BrevService = mock(),
    val personServiceMock: PersonService = mock(),
    val microsoftGraphApiMock: MicrosoftGraphApiOppslag = mock(),
    val klageClient: KlageClient = mock(),
    val sessionFactory: SessionFactory = TestSessionFactory(),
    val oppgaveService: OppgaveService = mock(),
    val clock: Clock = fixedClock,
) {
    val service = KlageServiceImpl(
        sakRepo = sakRepoMock,
        klageRepo = klageRepoMock,
        vedtakRepo = vedtakRepoMock,
        brevService = brevServiceMock,
        personService = personServiceMock,
        microsoftGraphApiClient = microsoftGraphApiMock,
        klageClient = klageClient,
        sessionFactory = sessionFactory,
        oppgaveService = oppgaveService,
        clock = clock,
    )

    private fun all() = listOf(
        sakRepoMock,
        klageRepoMock,
        vedtakRepoMock,
        brevServiceMock,
        personServiceMock,
        microsoftGraphApiMock,
        klageClient,
        oppgaveService,
    ).toTypedArray()

    fun verifyNoMoreInteractions() {
        org.mockito.kotlin.verifyNoMoreInteractions(
            *all(),
        )
    }
}
