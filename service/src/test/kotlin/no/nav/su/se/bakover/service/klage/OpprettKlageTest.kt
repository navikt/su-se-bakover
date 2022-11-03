package no.nav.su.se.bakover.service.klage

import arrow.core.left
import arrow.core.right
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf
import kotlinx.coroutines.runBlocking
import no.nav.su.se.bakover.common.AktørId
import no.nav.su.se.bakover.common.NavIdentBruker
import no.nav.su.se.bakover.common.application.journal.JournalpostId
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.domain.journalpost.ErTilknyttetSak
import no.nav.su.se.bakover.domain.klage.KunneIkkeOppretteKlage
import no.nav.su.se.bakover.domain.klage.OpprettetKlage
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.domain.oppgave.OppgaveFeil
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.person.KunneIkkeHentePerson
import no.nav.su.se.bakover.domain.statistikk.StatistikkEvent
import no.nav.su.se.bakover.domain.statistikk.StatistikkEventObserver
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.test.TestSessionFactory
import no.nav.su.se.bakover.test.avsluttetKlage
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.opprettetKlage
import no.nav.su.se.bakover.test.søknad.nySakMedjournalførtSøknadOgOppgave
import org.junit.jupiter.api.Test
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.time.LocalDate
import java.util.UUID

internal class OpprettKlageTest {

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
            datoKlageMottatt = 1.januar(2021),
            clock = fixedClock,
        )
        mocks.service.opprett(request) shouldBe KunneIkkeOppretteKlage.FantIkkeSak.left()

        verify(mocks.sakRepoMock).hentSak(argThat<UUID> { it shouldBe request.sakId })
        mocks.verifyNoMoreInteractions()
    }

    @Test
    fun `kan opprette en klage med en brukt journalpost-id dersom klagen har blitt avsluttet`() {
        val avsluttetKlage = avsluttetKlage().second
        val sak = nySakMedjournalførtSøknadOgOppgave(
            sakId = avsluttetKlage.sakId,
            klager = listOf(avsluttetKlage),
        ).first
        val observerMock: StatistikkEventObserver = mock {
            on { handle(any()) }.then {}
        }
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
                on { runBlocking { erTilknyttetSak(any(), any()) } } doReturn ErTilknyttetSak.Ja.right()
            },
            oppgaveService = mock {
                on { opprettOppgave(any()) } doReturn OppgaveId("nyOppgaveId").right()
            },
            observer = observerMock,
        )

        val request = NyKlageRequest(
            sakId = UUID.randomUUID(),
            journalpostId = avsluttetKlage.journalpostId,
            saksbehandler = NavIdentBruker.Saksbehandler("s2"),
            datoKlageMottatt = 1.januar(2021),
            clock = fixedClock,
        )

        val nyKlage = mocks.service.opprett(request).getOrFail()

        verify(observerMock).handle(argThat { it shouldBe StatistikkEvent.Behandling.Klage.Opprettet(nyKlage) })
        verify(observerMock).handle(argThat { it shouldBe StatistikkEvent.Behandling.Klage.Opprettet(nyKlage) })
        nyKlage.shouldBeTypeOf<OpprettetKlage>()
        nyKlage.journalpostId shouldBe avsluttetKlage.journalpostId
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
            datoKlageMottatt = 1.januar(2021),
            clock = fixedClock,
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
                on { runBlocking { erTilknyttetSak(any(), any()) } } doReturn ErTilknyttetSak.Ja.right()
            },
            personServiceMock = mock {
                on { hentAktørId(any()) } doReturn KunneIkkeHentePerson.Ukjent.left()
            },
        )
        val request = NyKlageRequest(
            sakId = sakId,
            journalpostId = JournalpostId("j2"),
            saksbehandler = NavIdentBruker.Saksbehandler("s2"),
            datoKlageMottatt = 1.januar(2021),
            clock = fixedClock,
        )
        mocks.service.opprett(request) shouldBe KunneIkkeOppretteKlage.KunneIkkeOppretteOppgave.left()

        verify(mocks.sakRepoMock).hentSak(sakId)
        runBlocking {
            verify(mocks.journalpostClient).erTilknyttetSak(JournalpostId("j2"), sak.saksnummer)
        }
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
                on { runBlocking { erTilknyttetSak(any(), any()) } } doReturn ErTilknyttetSak.Ja.right()
            },
            oppgaveService = mock {
                on { opprettOppgave(any()) } doReturn OppgaveFeil.KunneIkkeOppretteOppgave.left()
            },
        )
        val request = NyKlageRequest(
            sakId = sakId,
            journalpostId = JournalpostId("j2"),
            saksbehandler = NavIdentBruker.Saksbehandler("s2"),
            datoKlageMottatt = 1.januar(2021),
            clock = fixedClock,
        )
        mocks.service.opprett(request) shouldBe KunneIkkeOppretteKlage.KunneIkkeOppretteOppgave.left()

        verify(mocks.sakRepoMock).hentSak(sakId)
        runBlocking {
            verify(mocks.journalpostClient).erTilknyttetSak(JournalpostId("j2"), sak.saksnummer)
        }
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
            clock = fixedClock,
        )
        mocks.service.opprett(request) shouldBe KunneIkkeOppretteKlage.UgyldigMottattDato.left()
        mocks.verifyNoMoreInteractions()
    }

    @Test
    fun `kan opprette klage`() {
        val sak = nySakMedjournalførtSøknadOgOppgave().first

        val observerMock: StatistikkEventObserver = mock {
            on { handle(any()) }.then {}
        }
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
                on { runBlocking { erTilknyttetSak(any(), any()) } } doReturn ErTilknyttetSak.Ja.right()
            },
            oppgaveService = mock {
                on { opprettOppgave(any()) } doReturn OppgaveId("nyOppgaveId").right()
            },
            observer = observerMock,
        )
        val request = NyKlageRequest(
            sakId = sak.id,
            journalpostId = JournalpostId("1"),
            saksbehandler = NavIdentBruker.Saksbehandler("2"),
            datoKlageMottatt = 1.januar(2021),
            clock = fixedClock,
        )
        var expectedKlage: OpprettetKlage?
        mocks.service.opprett(request).getOrFail().also {
            expectedKlage = OpprettetKlage(
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
                datoKlageMottatt = 1.januar(2021),
            )
            it shouldBe expectedKlage
            verify(observerMock).handle(argThat { expected -> StatistikkEvent.Behandling.Klage.Opprettet(it) shouldBe expected })
            verify(observerMock).handle(argThat { expected -> StatistikkEvent.Behandling.Klage.Opprettet(it) shouldBe expected })
        }

        verify(mocks.sakRepoMock).hentSak(sak.id)
        runBlocking {
            verify(mocks.journalpostClient).erTilknyttetSak(JournalpostId("1"), sak.saksnummer)
        }
        verify(mocks.klageRepoMock).defaultTransactionContext()
        verify(mocks.klageRepoMock).lagre(
            argThat {
                it shouldBe expectedKlage
            },
            argThat {
                it shouldBe TestSessionFactory.transactionContext
            },
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
