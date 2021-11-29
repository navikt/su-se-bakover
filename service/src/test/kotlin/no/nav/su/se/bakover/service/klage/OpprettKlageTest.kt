package no.nav.su.se.bakover.service.klage

import arrow.core.left
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.klage.OpprettetKlage
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.nySakMedjournalførtSøknadOgOppgave
import org.junit.jupiter.api.Test
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.util.UUID

internal class OpprettKlageTest {

    @Test
    fun `fant ikke sak`() {
        val mocks = KlageServiceMocks(
            sakRepoMock = mock {
                on { hentSak(any<UUID>()) } doReturn null
            }
        )

        val request = NyKlageRequest(
            sakId = UUID.randomUUID(),
            journalpostId = JournalpostId("j2"),
            saksbehandler = NavIdentBruker.Saksbehandler("s2"),
            datoKlageMottatt = 1.desember(2021),
        )
        mocks.service.opprett(request) shouldBe KunneIkkeOppretteKlage.FantIkkeSak.left()

        verify(mocks.sakRepoMock).hentSak(argThat<UUID> { it shouldBe request.sakId })
        mocks.verifyNoMoreInteractions()
    }

    @Test
    fun `har allerede en åpen klage`() {
        val sakId = UUID.randomUUID()
        val sak = nySakMedjournalførtSøknadOgOppgave(
            sakId = sakId,
            klager = listOf(
                OpprettetKlage.create(
                    id = UUID.randomUUID(),
                    opprettet = fixedTidspunkt,
                    sakId = sakId,
                    journalpostId = JournalpostId(value = "j1"),
                    saksbehandler = NavIdentBruker.Saksbehandler(navIdent = "s1"),
                    datoKlageMottatt = 1.desember(2021),
                ),
            ),
        ).first

        val mocks = KlageServiceMocks(
            sakRepoMock = mock {
                on { hentSak(any<UUID>()) } doReturn sak
            }
        )
        val request = NyKlageRequest(
            sakId = sakId,
            journalpostId = JournalpostId("j2"),
            saksbehandler = NavIdentBruker.Saksbehandler("s2"),
            datoKlageMottatt = 1.desember(2021),
        )
        mocks.service.opprett(request) shouldBe KunneIkkeOppretteKlage.FinnesAlleredeEnÅpenKlage.left()

        verify(mocks.sakRepoMock).hentSak(sakId)
        mocks.verifyNoMoreInteractions()
    }

    @Test
    fun `kan opprette klage`() {
        val sak = nySakMedjournalførtSøknadOgOppgave().first

        val mocks = KlageServiceMocks(
            sakRepoMock = mock {
                on { hentSak(any<UUID>()) } doReturn sak
            }
        )
        val request = NyKlageRequest(
            sakId = sak.id,
            journalpostId = JournalpostId("1"),
            saksbehandler = NavIdentBruker.Saksbehandler("2"),
            datoKlageMottatt = 1.desember(2021),
        )
        var expectedKlage: OpprettetKlage?
        mocks.service.opprett(request).orNull()!!.also {
            expectedKlage = OpprettetKlage.create(
                id = it.id,
                opprettet = fixedTidspunkt,
                sakId = sak.id,
                journalpostId = JournalpostId(value = "1"),
                saksbehandler = NavIdentBruker.Saksbehandler(
                    navIdent = "2",
                ),
                datoKlageMottatt = 1.desember(2021),
            )
            it shouldBe expectedKlage
        }

        verify(mocks.sakRepoMock).hentSak(sak.id)
        verify(mocks.klageRepoMock).lagre(
            argThat {
                it shouldBe expectedKlage
            },
        )
        mocks.verifyNoMoreInteractions()
    }
}
