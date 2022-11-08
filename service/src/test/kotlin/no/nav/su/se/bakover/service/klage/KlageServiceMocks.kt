package no.nav.su.se.bakover.service.klage

import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.domain.brev.BrevService
import no.nav.su.se.bakover.domain.journalpost.JournalpostClient
import no.nav.su.se.bakover.domain.klage.KlageClient
import no.nav.su.se.bakover.domain.klage.KlageRepo
import no.nav.su.se.bakover.domain.oppgave.OppgaveService
import no.nav.su.se.bakover.domain.person.IdentClient
import no.nav.su.se.bakover.domain.person.PersonService
import no.nav.su.se.bakover.domain.sak.SakRepo
import no.nav.su.se.bakover.domain.statistikk.StatistikkEventObserver
import no.nav.su.se.bakover.service.vedtak.VedtakService
import no.nav.su.se.bakover.test.TestSessionFactory
import no.nav.su.se.bakover.test.defaultMock
import no.nav.su.se.bakover.test.fixedClock
import java.time.Clock

internal data class KlageServiceMocks(
    val sakRepoMock: SakRepo = defaultMock(),
    val klageRepoMock: KlageRepo = defaultMock(),
    val vedtakServiceMock: VedtakService = defaultMock(),
    val brevServiceMock: BrevService = defaultMock(),
    val personServiceMock: PersonService = defaultMock(),
    val identClient: IdentClient = defaultMock(),
    val klageClient: KlageClient = defaultMock(),
    val sessionFactory: SessionFactory = TestSessionFactory(),
    val oppgaveService: OppgaveService = defaultMock(),
    val journalpostClient: JournalpostClient = defaultMock(),
    val observer: StatistikkEventObserver = defaultMock(),
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
    ).apply {
        addObserver(observer)
    }

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
