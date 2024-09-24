package no.nav.su.se.bakover.service.klage

import arrow.core.left
import arrow.core.right
import behandling.klage.domain.UprosessertKlageinstanshendelse
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.common.journal.JournalpostId
import no.nav.su.se.bakover.domain.klage.AvsluttetKlageinstansUtfall
import no.nav.su.se.bakover.domain.klage.KlageRepo
import no.nav.su.se.bakover.domain.klage.KlageinstanshendelseRepo
import no.nav.su.se.bakover.domain.klage.Klageinstanshendelser
import no.nav.su.se.bakover.domain.klage.KunneIkkeTolkeKlageinstanshendelse
import no.nav.su.se.bakover.domain.klage.ProsessertKlageinstanshendelse
import no.nav.su.se.bakover.domain.klage.TolketKlageinstanshendelse
import no.nav.su.se.bakover.domain.klage.VurdertKlage
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.domain.oppgave.OppgaveService
import no.nav.su.se.bakover.test.TestSessionFactory
import no.nav.su.se.bakover.test.argShouldBe
import no.nav.su.se.bakover.test.argThat
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.oppgave.nyOppgaveHttpKallResponse
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
        val oppgaveServiceMock: OppgaveService = mock {
            on { opprettOppgaveMedSystembruker(any()) } doReturn nyOppgaveHttpKallResponse().right()
        }
        val mappedKlageinstanshendelse = TolketKlageinstanshendelse.KlagebehandlingAvsluttet(
            id = id,
            opprettet = fixedTidspunkt,
            avsluttetTidspunkt = fixedTidspunkt,
            klageId = klage.id,
            utfall = AvsluttetKlageinstansUtfall.TilInformasjon.Stadfestelse,
            journalpostIDer = listOf(JournalpostId("123456")),
        )

        buildKlageinstanshendelseService(
            klageinstanshendelseRepo = klageinstanshendelseRepoMock,
            klageRepo = klageRepoMock,
            oppgaveService = oppgaveServiceMock,
        ).håndterUtfallFraKlageinstans { _, _, _ -> mappedKlageinstanshendelse.right() }
        verify(klageinstanshendelseRepoMock).hentUbehandlaKlageinstanshendelser()
        verify(klageRepoMock).hentKlage(argThat { it shouldBe klage.id })
        verify(oppgaveServiceMock).opprettOppgaveMedSystembruker(
            argThat {
                it shouldBe OppgaveConfig.Klage.Klageinstanshendelse.AvsluttetKlageinstansUtfall.Informasjon(
                    saksnummer = klage.saksnummer,
                    fnr = klage.fnr,
                    tilordnetRessurs = null,
                    clock = fixedClock,
                    utfall = AvsluttetKlageinstansUtfall.TilInformasjon.Stadfestelse,
                    journalpostIDer = mappedKlageinstanshendelse.journalpostIDer,
                    avsluttetTidspunkt = fixedTidspunkt,
                    hendelsestype = "KlagebehandlingAvsluttet",
                )
            },
        )
        verify(klageinstanshendelseRepoMock).lagre(
            mappedKlageinstanshendelse.tilProsessert(OppgaveId("123")),
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
        val oppgaveServiceMock: OppgaveService = mock {
            on { opprettOppgaveMedSystembruker(any()) } doReturn nyOppgaveHttpKallResponse().right()
        }
        val mappedKlageinstanshendelse = TolketKlageinstanshendelse.KlagebehandlingAvsluttet(
            id = klageinstansId,
            opprettet = fixedTidspunkt,
            avsluttetTidspunkt = fixedTidspunkt,
            klageId = klage.id,
            utfall = AvsluttetKlageinstansUtfall.Retur,
            journalpostIDer = listOf(JournalpostId("123456")),
        )

        buildKlageinstanshendelseService(
            klageinstanshendelseRepo = klageinstanshendelseRepoMock,
            klageRepo = klageRepoMock,
            oppgaveService = oppgaveServiceMock,
        ).håndterUtfallFraKlageinstans { _, _, _ ->
            mappedKlageinstanshendelse.right()
        }
        verify(klageinstanshendelseRepoMock).hentUbehandlaKlageinstanshendelser()
        verify(klageRepoMock).hentKlage(argThat { it shouldBe klage.id })
        verify(oppgaveServiceMock).opprettOppgaveMedSystembruker(
            argThat {
                it shouldBe OppgaveConfig.Klage.Klageinstanshendelse.AvsluttetKlageinstansUtfall.Handling(
                    saksnummer = klage.saksnummer,
                    fnr = klage.fnr,
                    tilordnetRessurs = null,
                    clock = fixedClock,
                    utfall = AvsluttetKlageinstansUtfall.Retur,
                    journalpostIDer = listOf(JournalpostId("123456")),
                    avsluttetTidspunkt = fixedTidspunkt,
                    hendelsestype = "KlagebehandlingAvsluttet",
                )
            },
        )
        verify(klageRepoMock).lagre(
            argThat {
                it as VurdertKlage.Bekreftet
                it.oppgaveId shouldBe OppgaveId("123")
                it.saksbehandler shouldBe klage.saksbehandler
                it.klageinstanshendelser shouldBe Klageinstanshendelser.create(
                    listOf(
                        ProsessertKlageinstanshendelse.KlagebehandlingAvsluttet(
                            id = klageinstansId,
                            opprettet = fixedTidspunkt,
                            klageId = klage.id,
                            utfall = AvsluttetKlageinstansUtfall.Retur,
                            journalpostIDer = listOf(JournalpostId("123456")),
                            oppgaveId = OppgaveId("123"),
                        ),
                    ),
                )
            },
            argShouldBe(TestSessionFactory.transactionContext),
        )
        verify(klageinstanshendelseRepoMock).lagre(
            mappedKlageinstanshendelse.tilProsessert(OppgaveId("123")),
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
    ): KlageinstanshendelseService {
        return KlageinstanshendelseServiceImpl(
            klageinstanshendelseRepo = klageinstanshendelseRepo,
            klageRepo = klageRepo,
            oppgaveService = oppgaveService,
            clock = fixedClock,
            sessionFactory = TestSessionFactory(),
        )
    }
}
