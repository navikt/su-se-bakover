package no.nav.su.se.bakover.service.klage

import dokument.domain.brev.BrevService
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.domain.journalpost.JournalpostClient
import no.nav.su.se.bakover.domain.klage.KlageClient
import no.nav.su.se.bakover.domain.klage.KlageRepo
import no.nav.su.se.bakover.domain.oppgave.OppgaveService
import no.nav.su.se.bakover.domain.sak.SakService
import no.nav.su.se.bakover.domain.statistikk.StatistikkEventObserver
import no.nav.su.se.bakover.service.vedtak.VedtakService
import no.nav.su.se.bakover.test.TestSessionFactory
import no.nav.su.se.bakover.test.defaultMock
import no.nav.su.se.bakover.test.fixedClock
import person.domain.PersonService
import java.time.Clock

internal data class KlageServiceMocks(
    val sakServiceMock: SakService = defaultMock(),
    val klageRepoMock: KlageRepo = defaultMock(),
    val vedtakServiceMock: VedtakService = defaultMock(),
    val brevServiceMock: BrevService = defaultMock(),
    val personServiceMock: PersonService = defaultMock(),
    val klageClient: KlageClient = defaultMock(),
    val sessionFactory: SessionFactory = TestSessionFactory(),
    val oppgaveService: OppgaveService = defaultMock(),
    val journalpostClient: JournalpostClient = defaultMock(),
    val observer: StatistikkEventObserver = defaultMock(),
    val clock: Clock = fixedClock,
) {
    val service = KlageServiceImpl(
        sakService = sakServiceMock,
        klageRepo = klageRepoMock,
        vedtakService = vedtakServiceMock,
        brevService = brevServiceMock,
        personService = personServiceMock,
        klageClient = klageClient,
        sessionFactory = sessionFactory,
        oppgaveService = oppgaveService,
        journalpostClient = journalpostClient,
        clock = clock,
    ).apply {
        addObserver(observer)
    }

    private fun all() = listOf(
        sakServiceMock,
        klageRepoMock,
        vedtakServiceMock,
        brevServiceMock,
        personServiceMock,
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
