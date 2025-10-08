package no.nav.su.se.bakover.service.klage

import dokument.domain.brev.BrevService
import dokument.domain.hendelser.DokumentHendelseRepo
import dokument.domain.journalføring.QueryJournalpostClient
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.domain.klage.KlageClient
import no.nav.su.se.bakover.domain.klage.KlageRepo
import no.nav.su.se.bakover.domain.oppgave.OppgaveService
import no.nav.su.se.bakover.domain.sak.SakService
import no.nav.su.se.bakover.domain.statistikk.StatistikkEventObserver
import no.nav.su.se.bakover.test.TestSessionFactory
import no.nav.su.se.bakover.test.defaultMock
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.vedtak.application.VedtakService
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
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
    val queryJournalpostClient: QueryJournalpostClient = defaultMock(),
    val observer: StatistikkEventObserver = mock { on { handle(any(), any()) }.then {} },
    val dokumentHendelseRepo: DokumentHendelseRepo = defaultMock(),
    val clock: Clock = fixedClock,
) {
    val service = KlageServiceImpl(
        sakService = sakServiceMock,
        klageRepo = klageRepoMock,
        vedtakService = vedtakServiceMock,
        brevService = brevServiceMock,
        klageClient = klageClient,
        sessionFactory = sessionFactory,
        oppgaveService = oppgaveService,
        queryJournalpostClient = queryJournalpostClient,
        dokumentHendelseRepo = dokumentHendelseRepo,
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
        queryJournalpostClient,
    ).toTypedArray()

    fun verifyNoMoreInteractions() {
        org.mockito.kotlin.verifyNoMoreInteractions(
            *all(),
        )
    }
}
