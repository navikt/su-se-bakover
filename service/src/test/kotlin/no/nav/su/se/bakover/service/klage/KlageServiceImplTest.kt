package no.nav.su.se.bakover.service.klage

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.database.klage.KlageRepo
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.klage.OpprettetKlage
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedTidspunkt
import org.junit.jupiter.api.Test
import org.mockito.Mockito.verify
import org.mockito.kotlin.mock
import java.util.UUID

internal class KlageServiceImplTest {

    @Test
    fun `kan opprette klage`() {
        val klageRepoMock: KlageRepo = mock()
        val klageService = KlageServiceImpl(klageRepoMock, fixedClock)

        val nyKlage = NyKlageRequest(
            sakId = UUID.randomUUID(),
            journalpostId = "1",
            navIdent = "2",
        )

        var expectedKlage: OpprettetKlage?
        klageService.opprettKlage(nyKlage).orNull()!!.also {
            expectedKlage = OpprettetKlage.create(
                id = it.id,
                opprettet = fixedTidspunkt,
                sakId = nyKlage.sakId,
                journalpostId = JournalpostId(value = nyKlage.journalpostId),
                saksbehandler = NavIdentBruker.Saksbehandler(
                    navIdent = nyKlage.navIdent,
                ),
            )
            it shouldBe expectedKlage
        }

        verify(klageRepoMock).opprett(
            argThat {
                it shouldBe expectedKlage
            },
        )
    }
}
