package no.nav.su.se.bakover.service.klage

import arrow.core.left
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.database.sak.SakRepo
import no.nav.su.se.bakover.database.vedtak.VedtakRepo
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.klage.KlageRepo
import no.nav.su.se.bakover.domain.klage.OpprettetKlage
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.nySakMedjournalførtSøknadOgOppgave
import org.junit.jupiter.api.Test
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyNoMoreInteractions
import java.util.UUID

internal class OpprettKlageTest {

    @Test
    fun `fant ikke sak`() {
        val sakRepoMock: SakRepo = mock {
            on { hentSak(any<UUID>()) } doReturn null
        }
        val klageRepoMock = mock<KlageRepo>()
        val vedtakRepoMock: VedtakRepo = mock()
        val klageService = KlageServiceImpl(
            sakRepo = sakRepoMock,
            klageRepo = klageRepoMock,
            vedtakRepo = vedtakRepoMock,
            clock = fixedClock,
        )

        val request = NyKlageRequest(
            sakId = UUID.randomUUID(),
            journalpostId = "j2",
            navIdent = "s2",
        )
        klageService.opprett(request) shouldBe KunneIkkeOppretteKlage.FantIkkeSak.left()

        verify(sakRepoMock).hentSak(argThat<UUID> { it shouldBe request.sakId })
        verifyNoMoreInteractions(sakRepoMock, klageRepoMock, vedtakRepoMock)
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
                ),
            ),
        ).first
        val sakRepoMock: SakRepo = mock {
            on { hentSak(any<UUID>()) } doReturn sak
        }
        val klageRepoMock: KlageRepo = mock()
        val vedtakRepoMock: VedtakRepo = mock()
        val klageService = KlageServiceImpl(
            sakRepo = sakRepoMock,
            klageRepo = klageRepoMock,
            vedtakRepo = vedtakRepoMock,
            clock = fixedClock,
        )

        val request = NyKlageRequest(
            sakId = sakId,
            journalpostId = "j2",
            navIdent = "s2",
        )
        klageService.opprett(request) shouldBe KunneIkkeOppretteKlage.FinnesAlleredeEnÅpenKlage.left()

        verify(sakRepoMock).hentSak(sakId)
        verifyNoMoreInteractions(sakRepoMock, klageRepoMock, vedtakRepoMock)
    }

    @Test
    fun `kan opprette klage`() {
        val sak = nySakMedjournalførtSøknadOgOppgave().first
        val sakRepoMock: SakRepo = mock {
            on { hentSak(any<UUID>()) } doReturn sak
        }
        val klageRepoMock: KlageRepo = mock()
        val vedtakRepoMock: VedtakRepo = mock()
        val klageService = KlageServiceImpl(
            sakRepo = sakRepoMock,
            klageRepo = klageRepoMock,
            vedtakRepo = vedtakRepoMock,
            clock = fixedClock,
        )
        val request = NyKlageRequest(
            sakId = sak.id,
            journalpostId = "1",
            navIdent = "2",
        )
        var expectedKlage: OpprettetKlage?
        klageService.opprett(request).orNull()!!.also {
            expectedKlage = OpprettetKlage.create(
                id = it.id,
                opprettet = fixedTidspunkt,
                sakId = sak.id,
                journalpostId = JournalpostId(value = "1"),
                saksbehandler = NavIdentBruker.Saksbehandler(
                    navIdent = "2",
                ),
            )
            it shouldBe expectedKlage
        }

        verify(sakRepoMock).hentSak(sak.id)
        verify(klageRepoMock).lagre(
            argThat {
                it shouldBe expectedKlage
            },
        )
        verifyNoMoreInteractions(sakRepoMock, klageRepoMock, vedtakRepoMock)
    }
}
