package no.nav.su.se.bakover.service.klage

import arrow.core.left
import arrow.core.right
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.domain.AktørId
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.journalpost.HentetJournalpost
import no.nav.su.se.bakover.domain.journalpost.Sak
import no.nav.su.se.bakover.domain.klage.KunneIkkeOppretteKlage
import no.nav.su.se.bakover.domain.klage.OpprettetKlage
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.domain.oppgave.OppgaveFeil
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.person.KunneIkkeHentePerson
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.test.TestSessionFactory
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.nySakMedjournalførtSøknadOgOppgave
import no.nav.su.se.bakover.test.opprettetKlage
import no.nav.su.se.bakover.test.oversendtKlage
import org.junit.jupiter.api.Test
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.time.LocalDate
import java.util.UUID

internal class OpprettKlageTest {

    private fun getHentetJournalpost(): HentetJournalpost {
        return HentetJournalpost.create(
            tema = "SUP",
            journalstatus = "FERDIGSTILT",
            sak = Sak(
                fagsakId = "yep yep",
            ),
        )
    }

    @Test
    fun `fant ikke sak`() {
        val mocks = KlageServiceMocks(
            sakRepoMock = mock {
                on { hentSak(any<UUID>()) } doReturn null
            },
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
    fun `har allerede en klagebehandling`() {
        val sakId = UUID.randomUUID()
        val alleredeEksisterendeKlagebehandling = oversendtKlage(sakId = sakId).second
        val sak = nySakMedjournalførtSøknadOgOppgave(
            sakId = sakId,
            klager = listOf(alleredeEksisterendeKlagebehandling),
        ).first
        val mocks = KlageServiceMocks(
            sakRepoMock = mock {
                on { hentSak(any<UUID>()) } doReturn sak
            }
        )

        val request = NyKlageRequest(
            sakId = UUID.randomUUID(),
            journalpostId = alleredeEksisterendeKlagebehandling.journalpostId,
            saksbehandler = NavIdentBruker.Saksbehandler("s2"),
            datoKlageMottatt = 1.desember(2021),
        )
        mocks.service.opprett(request) shouldBe KunneIkkeOppretteKlage.HarAlleredeEnKlageBehandling.left()

        verify(mocks.sakRepoMock).hentSak(argThat<UUID> { it shouldBe request.sakId })
        mocks.verifyNoMoreInteractions()
    }

    @Test
    fun `finnes allerede en åpen klage`() {
        val sakId = UUID.randomUUID()
        val sak = nySakMedjournalførtSøknadOgOppgave(
            sakId = sakId,
            klager = listOf(opprettetKlage(sakId = sakId).second),
        ).first

        val mocks = KlageServiceMocks(
            sakRepoMock = mock {
                on { hentSak(any<UUID>()) } doReturn sak
            },
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
    fun `kunne ikke hente aktør id`() {
        val sakId = UUID.randomUUID()
        val sak = nySakMedjournalførtSøknadOgOppgave(
            sakId = sakId,
        ).first

        val mocks = KlageServiceMocks(
            sakRepoMock = mock {
                on { hentSak(any<UUID>()) } doReturn sak
            },
            klageRepoMock = mock {
                on { defaultTransactionContext() } doReturn TestSessionFactory.transactionContext
            },
            journalpostClient = mock {
                on { hentJournalpost(any()) } doReturn getHentetJournalpost().right()
            },
            personServiceMock = mock {
                on { hentAktørId(any()) } doReturn KunneIkkeHentePerson.Ukjent.left()
            },
        )
        val request = NyKlageRequest(
            sakId = sakId,
            journalpostId = JournalpostId("j2"),
            saksbehandler = NavIdentBruker.Saksbehandler("s2"),
            datoKlageMottatt = 1.desember(2021),
        )
        mocks.service.opprett(request) shouldBe KunneIkkeOppretteKlage.KunneIkkeOppretteOppgave.left()

        verify(mocks.sakRepoMock).hentSak(sakId)
        verify(mocks.personServiceMock).hentAktørId(argThat { it shouldBe sak.fnr })
        mocks.verifyNoMoreInteractions()
    }

    @Test
    fun `en opprettetKlage er en åpen klage`() {
        val klage = opprettetKlage().second
        klage.erÅpen() shouldBe true
    }

    @Test
    fun `kunne ikke opprette oppgave`() {
        val sakId = UUID.randomUUID()
        val sak = nySakMedjournalførtSøknadOgOppgave(
            sakId = sakId,
        ).first

        val mocks = KlageServiceMocks(
            sakRepoMock = mock {
                on { hentSak(any<UUID>()) } doReturn sak
            },
            klageRepoMock = mock {
                on { defaultTransactionContext() } doReturn TestSessionFactory.transactionContext
            },
            personServiceMock = mock {
                on { hentAktørId(any()) } doReturn AktørId("aktørId").right()
            },
            journalpostClient = mock {
                on { hentJournalpost(any()) } doReturn getHentetJournalpost().right()
            },
            oppgaveService = mock {
                on { opprettOppgave(any()) } doReturn OppgaveFeil.KunneIkkeOppretteOppgave.left()
            },
        )
        val request = NyKlageRequest(
            sakId = sakId,
            journalpostId = JournalpostId("j2"),
            saksbehandler = NavIdentBruker.Saksbehandler("s2"),
            datoKlageMottatt = 1.desember(2021),
        )
        mocks.service.opprett(request) shouldBe KunneIkkeOppretteKlage.KunneIkkeOppretteOppgave.left()

        verify(mocks.sakRepoMock).hentSak(sakId)
        verify(mocks.personServiceMock).hentAktørId(argThat { it shouldBe sak.fnr })
        verify(mocks.oppgaveService).opprettOppgave(
            argThat {
                it shouldBe OppgaveConfig.Klage.Saksbehandler(
                    saksnummer = sak.saksnummer,
                    aktørId = AktørId("aktørId"),
                    journalpostId = JournalpostId(value = "j2"),
                    tilordnetRessurs = null,
                    clock = fixedClock,
                )
            },
        )
        mocks.verifyNoMoreInteractions()
    }

    @Test
    fun `mottattdato etter nå-tid gir feil`() {
        val sakId = UUID.randomUUID()
        val mocks = KlageServiceMocks()

        val request = NyKlageRequest(
            sakId = sakId,
            journalpostId = JournalpostId("j2"),
            saksbehandler = NavIdentBruker.Saksbehandler("s2"),
            datoKlageMottatt = LocalDate.now(fixedClock).plusDays(1),
            clock = fixedClock
        )
        mocks.service.opprett(request) shouldBe KunneIkkeOppretteKlage.UgyldigMottattDato.left()
        mocks.verifyNoMoreInteractions()
    }

    @Test
    fun `kan opprette klage`() {
        val sak = nySakMedjournalførtSøknadOgOppgave().first

        val mocks = KlageServiceMocks(
            sakRepoMock = mock {
                on { hentSak(any<UUID>()) } doReturn sak
            },
            klageRepoMock = mock {
                on { defaultTransactionContext() } doReturn TestSessionFactory.transactionContext
            },
            personServiceMock = mock {
                on { hentAktørId(any()) } doReturn AktørId("aktørId").right()
            },
            journalpostClient = mock {
                on { hentJournalpost(any()) } doReturn getHentetJournalpost().right()
            },
            oppgaveService = mock {
                on { opprettOppgave(any()) } doReturn OppgaveId("nyOppgaveId").right()
            },
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
                saksnummer = sak.saksnummer,
                fnr = sak.fnr,
                journalpostId = JournalpostId(value = "1"),
                oppgaveId = OppgaveId("nyOppgaveId"),
                saksbehandler = NavIdentBruker.Saksbehandler(
                    navIdent = "2",
                ),
                datoKlageMottatt = 1.desember(2021),
            )
            it shouldBe expectedKlage
        }

        verify(mocks.sakRepoMock).hentSak(sak.id)
        verify(mocks.klageRepoMock).defaultTransactionContext()
        verify(mocks.klageRepoMock).lagre(
            argThat {
                it shouldBe expectedKlage
            },
            argThat {
                it shouldBe TestSessionFactory.transactionContext
            }
        )
        verify(mocks.oppgaveService).opprettOppgave(
            argThat {
                it shouldBe OppgaveConfig.Klage.Saksbehandler(
                    saksnummer = sak.saksnummer,
                    aktørId = AktørId("aktørId"),
                    journalpostId = JournalpostId(value = "1"),
                    tilordnetRessurs = null,
                    clock = fixedClock,
                )
            },
        )
        verify(mocks.personServiceMock).hentAktørId(argThat { it shouldBe sak.fnr })
        mocks.verifyNoMoreInteractions()
    }
}
