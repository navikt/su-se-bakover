package no.nav.su.se.bakover.service.klage

import arrow.core.right
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.domain.AktørId
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.klage.KlageRepo
import no.nav.su.se.bakover.domain.klage.Klagevedtak
import no.nav.su.se.bakover.domain.klage.KlagevedtakRepo
import no.nav.su.se.bakover.domain.klage.UprosessertFattetKlagevedtak
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.service.oppgave.OppgaveService
import no.nav.su.se.bakover.service.person.PersonService
import no.nav.su.se.bakover.test.TestSessionFactory
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.oversendtKlage
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import java.time.Clock
import java.util.UUID

internal class KlagevedtakServiceImplTest {
    val klage = oversendtKlage().second

    @Test
    fun `når deserializering feiler så markerer vi vedtaket med FEIL`() {
        val id = UUID.randomUUID()
        val klagevedtakRepoMock: KlagevedtakRepo = mock {
            on { hentUbehandlaKlagevedtak() } doReturn listOf(UprosessertFattetKlagevedtak(id))
        }

        buildKlagevedtakService(klagevedtakRepoMock).håndterUtfallFraKlageinstans { _, _ -> throw RuntimeException("feil") }
        verify(klagevedtakRepoMock).markerSomFeil(argThat { it shouldBe id })
    }

    @Test
    fun `stadfestelse setter vedtaket som prosessert`() {
        val id = UUID.randomUUID()
        val klagevedtakRepoMock: KlagevedtakRepo = mock {
            on { hentUbehandlaKlagevedtak() } doReturn listOf(UprosessertFattetKlagevedtak(id))
        }
        val klageRepoMock: KlageRepo = mock {
            on { hentKlage(any()) } doReturn klage
            on { defaultTransactionContext() } doReturn TestSessionFactory.transactionContext
        }
        val personServiceMock: PersonService = mock {
            on { hentAktørIdMedSystembruker(any()) } doReturn AktørId("").right()
        }
        val oppgaveServiceMock: OppgaveService = mock {
            on { opprettOppgave(any()) } doReturn OppgaveId("212121").right()
        }
        val mappedKlagevedtak = Klagevedtak.Uprosessert(
            id = id,
            eventId = UUID.randomUUID().toString(),
            klageId = klage.id,
            utfall = Klagevedtak.Utfall.STADFESTELSE,
            vedtaksbrevReferanse = "123456",
        )

        buildKlagevedtakService(
            klagevedtakRepo = klagevedtakRepoMock,
            klageRepo = klageRepoMock,
            personService = personServiceMock,
            oppgaveService = oppgaveServiceMock,
        ).håndterUtfallFraKlageinstans { _, _ -> mappedKlagevedtak }
        verify(klagevedtakRepoMock).hentUbehandlaKlagevedtak()
        verify(klageRepoMock).hentKlage(argThat { it shouldBe klage.id })
        verify(personServiceMock).hentAktørIdMedSystembruker(klage.fnr)
        verify(oppgaveServiceMock).opprettOppgave(
            argThat {
                it shouldBe OppgaveConfig.Klage.Saksbehandler(
                    saksnummer = klage.saksnummer,
                    aktørId = AktørId(aktørId = ""),
                    journalpostId = JournalpostId(value = mappedKlagevedtak.vedtaksbrevReferanse),
                    tilordnetRessurs = null,
                    clock = Clock.systemUTC(),
                )
            },
        )
        verify(klagevedtakRepoMock).lagreProsessertKlagevedtak(mappedKlagevedtak.tilProsessert(OppgaveId("212121")))
    }

    @Test
    fun `RETUR setter vedtaket som prosessert og lager ny oppgave for klagen`() {
        val id = UUID.randomUUID()
        val klagevedtakRepoMock: KlagevedtakRepo = mock {
            on { hentUbehandlaKlagevedtak() } doReturn listOf(UprosessertFattetKlagevedtak(id))
        }
        val klageRepoMock: KlageRepo = mock {
            on { hentKlage(any()) } doReturn klage
            on { defaultTransactionContext() } doReturn TestSessionFactory.transactionContext
        }
        val personServiceMock: PersonService = mock {
            on { hentAktørIdMedSystembruker(any()) } doReturn AktørId("").right()
        }
        val oppgaveServiceMock: OppgaveService = mock {
            on { opprettOppgave(any()) } doReturn OppgaveId("212121").right()
        }
        val mappedKlagevedtak = Klagevedtak.Uprosessert(
            id = id,
            eventId = UUID.randomUUID().toString(),
            klageId = klage.id,
            utfall = Klagevedtak.Utfall.RETUR,
            vedtaksbrevReferanse = "123456",
        )

        buildKlagevedtakService(
            klagevedtakRepo = klagevedtakRepoMock,
            klageRepo = klageRepoMock,
            personService = personServiceMock,
            oppgaveService = oppgaveServiceMock,
        ).håndterUtfallFraKlageinstans { _, _ -> mappedKlagevedtak }
        verify(klagevedtakRepoMock).hentUbehandlaKlagevedtak()
        verify(klageRepoMock).hentKlage(argThat { it shouldBe klage.id })
        verify(personServiceMock).hentAktørIdMedSystembruker(klage.fnr)
        verify(oppgaveServiceMock).opprettOppgave(
            argThat {
                it shouldBe OppgaveConfig.Klage.Saksbehandler(
                    saksnummer = klage.saksnummer,
                    aktørId = AktørId(aktørId = ""),
                    journalpostId = JournalpostId(value = mappedKlagevedtak.vedtaksbrevReferanse),
                    tilordnetRessurs = null,
                    clock = Clock.systemUTC(),
                )
            },
        )
        verify(klageRepoMock).lagre(klage.copy(oppgaveId = OppgaveId("212121")), TestSessionFactory.transactionContext)
        verify(klagevedtakRepoMock).lagreProsessertKlagevedtak(mappedKlagevedtak.tilProsessert(OppgaveId("212121")))
    }

    private fun UprosessertFattetKlagevedtak(id: UUID) = UprosessertFattetKlagevedtak(
        id = id,
        opprettet = fixedTidspunkt,
        metadata = UprosessertFattetKlagevedtak.Metadata(
            hendelseId = "55",
            offset = 0,
            partisjon = 0,
            key = "",
            value = "",
        ),
    )

    private fun buildKlagevedtakService(
        klagevedtakRepo: KlagevedtakRepo = mock(),
        klageRepo: KlageRepo = mock(),
        oppgaveService: OppgaveService = mock(),
        personService: PersonService = mock(),
    ): KlagevedtakService {
        return KlagevedtakServiceImpl(
            klagevedtakRepo = klagevedtakRepo,
            klageRepo = klageRepo,
            oppgaveService = oppgaveService,
            personService = personService,
        )
    }
}
