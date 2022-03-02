package no.nav.su.se.bakover.service.klage

import arrow.core.left
import arrow.core.right
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.domain.AktørId
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.klage.KlageRepo
import no.nav.su.se.bakover.domain.klage.KlageinstansUtfall
import no.nav.su.se.bakover.domain.klage.KlageinstanshendelseRepo
import no.nav.su.se.bakover.domain.klage.Klageinstanshendelser
import no.nav.su.se.bakover.domain.klage.KunneIkkeTolkeKlageinstanshendelse
import no.nav.su.se.bakover.domain.klage.ProsessertKlageinstanshendelse
import no.nav.su.se.bakover.domain.klage.TolketKlageinstanshendelse
import no.nav.su.se.bakover.domain.klage.UprosessertKlageinstanshendelse
import no.nav.su.se.bakover.domain.klage.VurdertKlage
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.service.argShouldBe
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.service.oppgave.OppgaveService
import no.nav.su.se.bakover.service.person.PersonService
import no.nav.su.se.bakover.test.TestSessionFactory
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.oversendtKlage
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import java.util.UUID

internal class KlageinstanshendelseServiceImplTest {
    val klage = oversendtKlage().second

    @Test
    fun `når deserializering feiler så markerer vi vedtaket med FEIL`() {
        val id = UUID.randomUUID()
        val klageinstanshendelseRepoMock: KlageinstanshendelseRepo = mock {
            on { hentUbehandlaKlageinstanshendelser() } doReturn listOf(uprosessertKlageinstanshendelse(id))
        }

        buildKlageinstanshendelseService(klageinstanshendelseRepoMock).håndterUtfallFraKlageinstans { _, _, _ -> KunneIkkeTolkeKlageinstanshendelse.KunneIkkeDeserialisere.left() }
        verify(klageinstanshendelseRepoMock).markerSomFeil(argThat { it shouldBe id })
    }

    @Test
    fun `stadfestelse setter vedtaket som prosessert`() {
        val id = UUID.randomUUID()
        val klageinstanshendelseRepoMock: KlageinstanshendelseRepo = mock {
            on { hentUbehandlaKlageinstanshendelser() } doReturn listOf(uprosessertKlageinstanshendelse(id))
        }
        val klageRepoMock: KlageRepo = mock {
            on { hentKlage(any()) } doReturn klage
            on { defaultTransactionContext() } doReturn TestSessionFactory.transactionContext
        }
        val personServiceMock: PersonService = mock {
            on { hentAktørIdMedSystembruker(any()) } doReturn AktørId("").right()
        }
        val oppgaveServiceMock: OppgaveService = mock {
            on { opprettOppgaveMedSystembruker(any()) } doReturn OppgaveId("212121").right()
        }
        val mappedKlageinstanshendelse = TolketKlageinstanshendelse(
            id = id,
            opprettet = fixedTidspunkt,
            avsluttetTidspunkt = fixedTidspunkt,
            klageId = klage.id,
            utfall = KlageinstansUtfall.STADFESTELSE,
            journalpostIDer = listOf(JournalpostId("123456")),
        )

        buildKlageinstanshendelseService(
            klageinstanshendelseRepo = klageinstanshendelseRepoMock,
            klageRepo = klageRepoMock,
            personService = personServiceMock,
            oppgaveService = oppgaveServiceMock,
        ).håndterUtfallFraKlageinstans { _, _, _ -> mappedKlageinstanshendelse.right() }
        verify(klageinstanshendelseRepoMock).hentUbehandlaKlageinstanshendelser()
        verify(klageRepoMock).hentKlage(argThat { it shouldBe klage.id })
        verify(personServiceMock).hentAktørIdMedSystembruker(klage.fnr)
        verify(oppgaveServiceMock).opprettOppgaveMedSystembruker(
            argThat {
                it shouldBe OppgaveConfig.Klage.Klageinstanshendelse.Informasjon(
                    saksnummer = klage.saksnummer,
                    aktørId = AktørId(aktørId = ""),
                    tilordnetRessurs = null,
                    clock = fixedClock,
                    utfall = KlageinstansUtfall.STADFESTELSE,
                    journalpostIDer = mappedKlageinstanshendelse.journalpostIDer,
                    avsluttetTidspunkt = fixedTidspunkt,
                )
            },
        )
        verify(klageinstanshendelseRepoMock).lagre(
            mappedKlageinstanshendelse.tilProsessert(OppgaveId("212121")),
            TestSessionFactory.transactionContext,
        )
    }

    @Test
    fun `RETUR setter vedtaket som prosessert og lager ny oppgave for klagen`() {
        val klage = oversendtKlage().second
        val klageinstansId = UUID.randomUUID()
        val klageinstanshendelseRepoMock: KlageinstanshendelseRepo = mock {
            on { hentUbehandlaKlageinstanshendelser() } doReturn listOf(uprosessertKlageinstanshendelse(klageinstansId))
        }
        val klageRepoMock: KlageRepo = mock {
            on { hentKlage(any()) } doReturn klage
            on { defaultTransactionContext() } doReturn TestSessionFactory.transactionContext
        }
        val personServiceMock: PersonService = mock {
            on { hentAktørIdMedSystembruker(any()) } doReturn AktørId("").right()
        }
        val oppgaveServiceMock: OppgaveService = mock {
            on { opprettOppgaveMedSystembruker(any()) } doReturn OppgaveId("212121").right()
        }
        val mappedKlageinstanshendelse = TolketKlageinstanshendelse(
            id = klageinstansId,
            opprettet = fixedTidspunkt,
            avsluttetTidspunkt = fixedTidspunkt,
            klageId = klage.id,
            utfall = KlageinstansUtfall.RETUR,
            journalpostIDer = listOf(JournalpostId("123456")),
        )

        buildKlageinstanshendelseService(
            klageinstanshendelseRepo = klageinstanshendelseRepoMock,
            klageRepo = klageRepoMock,
            personService = personServiceMock,
            oppgaveService = oppgaveServiceMock,
        ).håndterUtfallFraKlageinstans { _, _, _ ->
            mappedKlageinstanshendelse.right()
        }
        verify(klageinstanshendelseRepoMock).hentUbehandlaKlageinstanshendelser()
        verify(klageRepoMock).hentKlage(argThat { it shouldBe klage.id })
        verify(personServiceMock).hentAktørIdMedSystembruker(klage.fnr)
        verify(oppgaveServiceMock).opprettOppgaveMedSystembruker(
            argThat {
                it shouldBe OppgaveConfig.Klage.Klageinstanshendelse.Handling(
                    saksnummer = klage.saksnummer,
                    aktørId = AktørId(aktørId = ""),
                    tilordnetRessurs = null,
                    clock = fixedClock,
                    utfall = KlageinstansUtfall.RETUR,
                    journalpostIDer = listOf(JournalpostId("123456")),
                    avsluttetTidspunkt = fixedTidspunkt,
                )
            },
        )
        verify(klageRepoMock).lagre(
            argThat {
                it as VurdertKlage.Bekreftet
                it.oppgaveId shouldBe OppgaveId("212121")
                it.saksbehandler shouldBe klage.saksbehandler
                it.klageinstanshendelser shouldBe Klageinstanshendelser.create(
                    listOf(
                        ProsessertKlageinstanshendelse(
                            id = klageinstansId,
                            opprettet = fixedTidspunkt,
                            klageId = klage.id,
                            utfall = KlageinstansUtfall.RETUR,
                            journalpostIDer = listOf(JournalpostId("123456")),
                            oppgaveId = OppgaveId("212121"),
                        ),
                    ),
                )
            },
            argShouldBe(TestSessionFactory.transactionContext),
        )
        verify(klageinstanshendelseRepoMock).lagre(
            mappedKlageinstanshendelse.tilProsessert(OppgaveId("212121")),
            TestSessionFactory.transactionContext,
        )
    }

    private fun uprosessertKlageinstanshendelse(id: UUID) = UprosessertKlageinstanshendelse(
        id = id,
        opprettet = fixedTidspunkt,
        metadata = UprosessertKlageinstanshendelse.Metadata(
            topic = "klage.behandling-events.v1",
            hendelseId = "55",
            offset = 0,
            partisjon = 0,
            key = "",
            value = "",
        ),
    )

    private fun buildKlageinstanshendelseService(
        klageinstanshendelseRepo: KlageinstanshendelseRepo = mock(),
        klageRepo: KlageRepo = mock(),
        oppgaveService: OppgaveService = mock(),
        personService: PersonService = mock(),
    ): KlageinstanshendelseService {
        return KlageinstanshendelseServiceImpl(
            klageinstanshendelseRepo = klageinstanshendelseRepo,
            klageRepo = klageRepo,
            oppgaveService = oppgaveService,
            personService = personService,
            clock = fixedClock,
            sessionFactory = TestSessionFactory(),
        )
    }
}
