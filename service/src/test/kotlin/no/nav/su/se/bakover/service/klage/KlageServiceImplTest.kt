package no.nav.su.se.bakover.service.klage

import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.database.klage.KlageRepo
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.klage.Klage
import no.nav.su.se.bakover.test.fixedClock
import org.junit.jupiter.api.Test
import org.mockito.Mockito.verify
import org.mockito.kotlin.mock
import java.util.UUID

internal class KlageServiceImplTest {
    @Test
    fun `kan opprette klage`() {
        val klageRepoMock: KlageRepo = mock()
        val klageService = KlageServiceImpl(klageRepoMock, fixedClock)

        val nyKlage = Klage(
            id = UUID.randomUUID(),
            opprettet = Tidspunkt.now(),
            sakId = UUID.randomUUID(),
            journalpostId = JournalpostId(value = "1"),
            saksbehandler = NavIdentBruker.Saksbehandler(
                navIdent = "2",
            ),
        )
        klageService.opprettKlage(nyKlage)

        verify(klageRepoMock).opprett(nyKlage)
    }
}
