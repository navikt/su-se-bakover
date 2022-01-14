package no.nav.su.se.bakover.service.klage

import arrow.core.left
import arrow.core.right
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.domain.AktørId
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.klage.KanIkkeTolkeKlagevedtak
import no.nav.su.se.bakover.domain.klage.KlageRepo
import no.nav.su.se.bakover.domain.klage.KlagevedtakRepo
import no.nav.su.se.bakover.domain.klage.KlagevedtakUtfall
import no.nav.su.se.bakover.domain.klage.Klagevedtakshistorikk
import no.nav.su.se.bakover.domain.klage.UprosessertFattetKlageinstansvedtak
import no.nav.su.se.bakover.domain.klage.UprosessertKlageinstansvedtak
import no.nav.su.se.bakover.domain.klage.VurdertKlage
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
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

internal class KlagevedtakServiceImplTest {
    val klage = oversendtKlage().second

    @Test
    fun `når deserializering feiler så markerer vi vedtaket med FEIL`() {
        val id = UUID.randomUUID()
        val klagevedtakRepoMock: KlagevedtakRepo = mock {
            on { hentUbehandlaKlagevedtak() } doReturn listOf(uprosessertFattetKlagevedtak(id))
        }

        buildKlagevedtakService(klagevedtakRepoMock).håndterUtfallFraKlageinstans { _, _, _ -> KanIkkeTolkeKlagevedtak.KunneIkkeDeserialisere.left() }
        verify(klagevedtakRepoMock).markerSomFeil(argThat { it shouldBe id })
    }

    @Test
    fun `stadfestelse setter vedtaket som prosessert`() {
        val id = UUID.randomUUID()
        val klagevedtakRepoMock: KlagevedtakRepo = mock {
            on { hentUbehandlaKlagevedtak() } doReturn listOf(uprosessertFattetKlagevedtak(id))
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
        val mappedKlagevedtak = UprosessertKlageinstansvedtak(
            id = id,
            opprettet = fixedTidspunkt,
            klageId = klage.id,
            utfall = KlagevedtakUtfall.STADFESTELSE,
            vedtaksbrevReferanse = "123456",
        )

        buildKlagevedtakService(
            klagevedtakRepo = klagevedtakRepoMock,
            klageRepo = klageRepoMock,
            personService = personServiceMock,
            oppgaveService = oppgaveServiceMock,
        ).håndterUtfallFraKlageinstans { _, _, _ -> mappedKlagevedtak.right() }
        verify(klagevedtakRepoMock).hentUbehandlaKlagevedtak()
        verify(klageRepoMock).hentKlage(argThat { it shouldBe klage.id })
        verify(personServiceMock).hentAktørIdMedSystembruker(klage.fnr)
        verify(oppgaveServiceMock).opprettOppgaveMedSystembruker(
            argThat {
                it shouldBe OppgaveConfig.Klage.Vedtak.Informasjon(
                    saksnummer = klage.saksnummer,
                    aktørId = AktørId(aktørId = ""),
                    journalpostId = JournalpostId(value = mappedKlagevedtak.vedtaksbrevReferanse),
                    tilordnetRessurs = null,
                    clock = fixedClock,
                    utfall = KlagevedtakUtfall.STADFESTELSE
                )
            },
        )
        TestSessionFactory().withTransactionContext { tx ->
            verify(klagevedtakRepoMock).lagre(mappedKlagevedtak.tilProsessert(OppgaveId("212121")), tx)
        }
    }

    @Test
    fun `RETUR setter vedtaket som prosessert og lager ny oppgave for klagen`() {
        val id = UUID.randomUUID()
        val klagevedtakRepoMock: KlagevedtakRepo = mock {
            on { hentUbehandlaKlagevedtak() } doReturn listOf(uprosessertFattetKlagevedtak(id))
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
        val mappedKlagevedtak = UprosessertKlageinstansvedtak(
            id = id,
            opprettet = fixedTidspunkt,
            klageId = klage.id,
            utfall = KlagevedtakUtfall.RETUR,
            vedtaksbrevReferanse = "123456",
        )

        buildKlagevedtakService(
            klagevedtakRepo = klagevedtakRepoMock,
            klageRepo = klageRepoMock,
            personService = personServiceMock,
            oppgaveService = oppgaveServiceMock,
        ).håndterUtfallFraKlageinstans { _, _, _ -> mappedKlagevedtak.right() }
        verify(klagevedtakRepoMock).hentUbehandlaKlagevedtak()
        verify(klageRepoMock).hentKlage(argThat { it shouldBe klage.id })
        verify(personServiceMock).hentAktørIdMedSystembruker(klage.fnr)
        verify(oppgaveServiceMock).opprettOppgaveMedSystembruker(
            argThat {
                it shouldBe OppgaveConfig.Klage.Vedtak.Handling(
                    saksnummer = klage.saksnummer,
                    aktørId = AktørId(aktørId = ""),
                    journalpostId = JournalpostId(value = "123456"),
                    tilordnetRessurs = null,
                    clock = fixedClock,
                    utfall = KlagevedtakUtfall.RETUR
                )
            },
        )
        TestSessionFactory().withTransactionContext { tx ->
            verify(klageRepoMock).lagre(
                VurdertKlage.Bekreftet.create(
                    id = klage.id,
                    opprettet = klage.opprettet,
                    sakId = klage.sakId,
                    saksnummer = klage.saksnummer,
                    fnr = klage.fnr,
                    journalpostId = klage.journalpostId,
                    oppgaveId = OppgaveId("212121"),
                    saksbehandler = klage.saksbehandler,
                    vilkårsvurderinger = klage.vilkårsvurderinger,
                    vurderinger = klage.vurderinger,
                    attesteringer = klage.attesteringer,
                    datoKlageMottatt = klage.datoKlageMottatt,
                    klagevedtakshistorikk = Klagevedtakshistorikk.create(
                        listOf(
                            mappedKlagevedtak.tilProsessert(OppgaveId("212121"))
                        )
                    )
                ),
                tx
            )
            verify(klagevedtakRepoMock).lagre(mappedKlagevedtak.tilProsessert(OppgaveId("212121")), tx)
        }
    }

    private fun uprosessertFattetKlagevedtak(id: UUID) = UprosessertFattetKlageinstansvedtak(
        id = id,
        opprettet = fixedTidspunkt,
        metadata = UprosessertFattetKlageinstansvedtak.Metadata(
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
            clock = fixedClock,
            sessionFactory = TestSessionFactory()
        )
    }
}
