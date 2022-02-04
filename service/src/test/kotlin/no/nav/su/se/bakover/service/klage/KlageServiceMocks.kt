package no.nav.su.se.bakover.service.klage

import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.domain.journalpost.JournalpostClient
import no.nav.su.se.bakover.domain.klage.KlageClient
import no.nav.su.se.bakover.domain.klage.KlageRepo
import no.nav.su.se.bakover.domain.person.IdentClient
import no.nav.su.se.bakover.domain.sak.SakRepo
import no.nav.su.se.bakover.service.brev.BrevService
import no.nav.su.se.bakover.service.oppgave.OppgaveService
import no.nav.su.se.bakover.service.person.PersonService
import no.nav.su.se.bakover.service.vedtak.VedtakService
import no.nav.su.se.bakover.test.TestSessionFactory
import no.nav.su.se.bakover.test.fixedClock
import org.mockito.kotlin.mock
import java.time.Clock

internal data class KlageServiceMocks(
    val sakRepoMock: SakRepo = mock(),
    val klageRepoMock: KlageRepo = mock(),
    val vedtakServiceMock: VedtakService = mock(),
    val brevServiceMock: BrevService = mock(),
    val personServiceMock: PersonService = mock(),
    val identClient: IdentClient = mock(),
    val klageClient: KlageClient = mock(),
    val sessionFactory: SessionFactory = TestSessionFactory(),
    val oppgaveService: OppgaveService = mock(),
    val journalpostClient: JournalpostClient = mock(),
    val clock: Clock = fixedClock,
) {
    val service = KlageServiceImpl(
        sakRepo = sakRepoMock,
        klageRepo = klageRepoMock,
        vedtakService = vedtakServiceMock,
        brevService = brevServiceMock,
        personService = personServiceMock,
        identClient = identClient,
        klageClient = klageClient,
        sessionFactory = sessionFactory,
        oppgaveService = oppgaveService,
        journalpostClient = journalpostClient,
        clock = clock,
    )

    private fun all() = listOf(
        sakRepoMock,
        klageRepoMock,
        vedtakServiceMock,
        brevServiceMock,
        personServiceMock,
        identClient,
        klageClient,
        oppgaveService,
        journalpostClient,
    ).toTypedArray()

    fun verifyNoMoreInteractions() {
        org.mockito.kotlin.verifyNoMoreInteractions(
            *all(),
        )
    }
}
