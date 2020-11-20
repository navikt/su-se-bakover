package no.nav.su.se.bakover.service.søknad.lukk

import arrow.core.left
import arrow.core.right
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.inOrder
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.toTidspunkt
import no.nav.su.se.bakover.database.søknad.SøknadRepo
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.Søknad.Lukket.LukketType.AVVIST
import no.nav.su.se.bakover.domain.SøknadInnholdTestdataBuilder
import no.nav.su.se.bakover.domain.brev.BrevbestillingId
import no.nav.su.se.bakover.domain.brev.søknad.lukk.TrukketSøknadBrevRequest
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.oppdrag.Oppdrag
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.søknad.LukkSøknadRequest
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.service.brev.BrevService
import no.nav.su.se.bakover.service.brev.KunneIkkeDistribuereBrev
import no.nav.su.se.bakover.service.doNothing
import no.nav.su.se.bakover.service.oppgave.OppgaveService
import no.nav.su.se.bakover.service.sak.SakService
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.UUID

internal class LukkSøknadServiceImplTest {
    private val fixedEpochClock = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC)
    private val sakId = UUID.randomUUID()
    private val saksbehandler = NavIdentBruker.Saksbehandler("Z993156")
    private val søknadInnhold = SøknadInnholdTestdataBuilder.build()
    private val nySøknadJournalpostId = JournalpostId("nySøknadJournalpostId")
    private val lukketJournalpostId = JournalpostId("lukketJournalpostId")
    private val brevbestillingId = BrevbestillingId("brevbestillingId")
    private val oppgaveId = OppgaveId("oppgaveId")
    private val sak = Sak(
        id = sakId,
        opprettet = Tidspunkt.EPOCH,
        fnr = Fnr("12345678901"),
        søknader = emptyList(),
        behandlinger = emptyList(),
        oppdrag = Oppdrag(
            id = UUID30.randomUUID(),
            opprettet = Tidspunkt.EPOCH,
            sakId = sakId,
            utbetalinger = emptyList()
        )
    )
    private val nySøknad = Søknad.Ny(
        sakId = sakId,
        id = UUID.randomUUID(),
        opprettet = Tidspunkt.EPOCH,
        søknadInnhold = søknadInnhold,
    )
    private val lukketSøknad = Søknad.Lukket(
        sakId = sakId,
        id = nySøknad.id,
        opprettet = nySøknad.opprettet,
        søknadInnhold = søknadInnhold,
        journalpostId = null,
        oppgaveId = null,
        lukketTidspunkt = Tidspunkt.EPOCH,
        lukketAv = saksbehandler,
        lukketType = Søknad.Lukket.LukketType.TRUKKET,
        lukketJournalpostId = null,
        lukketBrevbestillingId = null
    )

    private val journalførtSøknadMedOppgave = nySøknad
        .journalfør(nySøknadJournalpostId)
        .medOppgave(oppgaveId)

    private val trekkSøknadRequest = LukkSøknadRequest.MedBrev.TrekkSøknad(
        søknadId = nySøknad.id,
        saksbehandler = saksbehandler,
        trukketDato = 1.januar(2020)
    )

    @Test
    fun `fant ikke søknad`() {
        val søknadRepoMock = mock<SøknadRepo> {
            on { hentSøknad(any()) } doReturn null
        }
        val sakServiceMock = mock<SakService> ()
        val brevServiceMock = mock<BrevService> ()
        val oppgaveServiceMock = mock<OppgaveService> ()

        LukkSøknadServiceImpl(
            søknadRepo = søknadRepoMock,
            sakService = sakServiceMock,
            brevService = brevServiceMock,
            oppgaveService = oppgaveServiceMock,
            clock = fixedEpochClock,
        ).lukkSøknad(trekkSøknadRequest) shouldBe KunneIkkeLukkeSøknad.FantIkkeSøknad.left()

        verify(søknadRepoMock).hentSøknad(argThat { it shouldBe journalførtSøknadMedOppgave.id })
        verifyNoMoreInteractions(søknadRepoMock, sakServiceMock, brevServiceMock, oppgaveServiceMock)
    }

    @Test
    fun `trekker en søknad uten mangler`() {

        val søknadRepoMock = mock<SøknadRepo> {
            on { hentSøknad(any()) } doReturn journalførtSøknadMedOppgave
            on { oppdaterSøknad(any()) }.doNothing()
            on { harSøknadPåbegyntBehandling(any()) } doReturn false
        }
        val sakServiceMock = mock<SakService> {
            on { hentSak(any<UUID>()) } doReturn sak.right()
        }
        val brevServiceMock = mock<BrevService> {
            on { journalførBrev(any(), any()) } doReturn lukketJournalpostId.right()
            on { distribuerBrev(any()) } doReturn brevbestillingId.right()
        }
        val oppgaveServiceMock = mock<OppgaveService> {
            on { lukkOppgave(any()) } doReturn Unit.right()
        }

        LukkSøknadServiceImpl(
            søknadRepo = søknadRepoMock,
            sakService = sakServiceMock,
            brevService = brevServiceMock,
            oppgaveService = oppgaveServiceMock,
            clock = fixedEpochClock,
        ).lukkSøknad(trekkSøknadRequest) shouldBe LukketSøknad.UtenMangler(sak).right()

        inOrder(
            søknadRepoMock, sakServiceMock, brevServiceMock, oppgaveServiceMock
        ) {
            verify(søknadRepoMock).hentSøknad(argThat { it shouldBe journalførtSøknadMedOppgave.id })
            verify(søknadRepoMock).harSøknadPåbegyntBehandling(argThat { it shouldBe journalførtSøknadMedOppgave.id })

            verify(brevServiceMock).journalførBrev(
                request = argThat {
                    it shouldBe TrukketSøknadBrevRequest(
                        søknad = lukketSøknad.copy(
                            journalpostId = nySøknadJournalpostId,
                            oppgaveId = oppgaveId,
                        ),
                        trukketDato = 1.januar(2020)
                    )
                },
                sakId = argThat { it shouldBe journalførtSøknadMedOppgave.sakId }
            )
            verify(brevServiceMock).distribuerBrev(argThat { it shouldBe lukketJournalpostId })
            verify(søknadRepoMock).oppdaterSøknad(
                argThat {
                    it shouldBe lukketSøknad.copy(
                        oppgaveId = oppgaveId,
                        journalpostId = nySøknadJournalpostId,
                        lukketJournalpostId = lukketJournalpostId,
                        lukketBrevbestillingId = brevbestillingId
                    )
                }
            )
            verify(sakServiceMock).hentSak(argThat<UUID> { it shouldBe journalførtSøknadMedOppgave.sakId })
            verify(oppgaveServiceMock).lukkOppgave(argThat { it shouldBe oppgaveId })
        }
        verifyNoMoreInteractions(søknadRepoMock, sakServiceMock, brevServiceMock, oppgaveServiceMock)
    }

    @Test
    fun `lukker avvist søknad uten brev`() {
        val søknadRepoMock = mock<SøknadRepo> {
            on { hentSøknad(any()) } doReturn journalførtSøknadMedOppgave
            on { oppdaterSøknad(any()) }.doNothing()
            on { harSøknadPåbegyntBehandling(any()) } doReturn false
        }

        val sakServiceMock = mock<SakService> {
            on { hentSak(any<UUID>()) } doReturn sak.right()
        }

        val brevServiceMock = mock<BrevService>()

        val oppgaveServiceMock = mock<OppgaveService> {
            on { lukkOppgave(any()) } doReturn Unit.right()
        }

        LukkSøknadServiceImpl(
            søknadRepo = søknadRepoMock,
            sakService = sakServiceMock,
            brevService = brevServiceMock,
            oppgaveService = oppgaveServiceMock,
            clock = fixedEpochClock,
        ).lukkSøknad(
            LukkSøknadRequest.UtenBrev.AvvistSøknad(
                søknadId = journalførtSøknadMedOppgave.id,
                saksbehandler = saksbehandler
            )
        ) shouldBe LukketSøknad.UtenMangler(sak).right()

        inOrder(
            søknadRepoMock, sakServiceMock, oppgaveServiceMock
        ) {
            verify(søknadRepoMock).hentSøknad(argThat { it shouldBe journalførtSøknadMedOppgave.id })
            verify(søknadRepoMock).harSøknadPåbegyntBehandling(argThat { it shouldBe journalførtSøknadMedOppgave.id })
            verify(søknadRepoMock).oppdaterSøknad(
                argThat {
                    it shouldBe lukketSøknad.copy(
                        journalpostId = nySøknadJournalpostId,
                        oppgaveId = oppgaveId,
                        lukketType = AVVIST,
                    )
                },
            )
            verify(sakServiceMock).hentSak(argThat<UUID> { it shouldBe journalførtSøknadMedOppgave.sakId })
            verify(oppgaveServiceMock).lukkOppgave(argThat { it shouldBe oppgaveId })
        }
        verifyNoMoreInteractions(søknadRepoMock, sakServiceMock, brevServiceMock, oppgaveServiceMock)
    }

    @Test
    fun `en søknad kan ikke trekkes før den er opprettet`() {

        val treDagerGammelSøknad = Søknad.Ny(
            sakId = sakId,
            id = UUID.randomUUID(),
            opprettet = LocalDateTime.now().minusDays(3).toTidspunkt(),
            søknadInnhold = søknadInnhold,
        )

        val søknadRepoMock = mock<SøknadRepo> {
            on { hentSøknad(any()) } doReturn treDagerGammelSøknad
            on { oppdaterSøknad(any()) }.doNothing()
            on { harSøknadPåbegyntBehandling(any()) } doReturn true
        }
        val sakServiceMock = mock<SakService>()
        val brevServiceMock = mock<BrevService>()
        val oppgaveServiceMock = mock<OppgaveService>()

        LukkSøknadServiceImpl(
            søknadRepo = søknadRepoMock,
            sakService = sakServiceMock,
            brevService = brevServiceMock,
            oppgaveService = oppgaveServiceMock,
            clock = fixedEpochClock,
        ).lukkSøknad(
            LukkSøknadRequest.MedBrev.TrekkSøknad(
                søknadId = treDagerGammelSøknad.id,
                saksbehandler = saksbehandler,
                trukketDato = LocalDate.now().minusDays(4)
            )
        ) shouldBe KunneIkkeLukkeSøknad.UgyldigDato.left()

        inOrder(
            søknadRepoMock, sakServiceMock, brevServiceMock
        ) {
            verify(søknadRepoMock).hentSøknad(argThat { it shouldBe treDagerGammelSøknad.id })
        }

        verifyNoMoreInteractions(søknadRepoMock, sakServiceMock, brevServiceMock, oppgaveServiceMock)
    }

    @Test
    fun `en søknad med behandling skal ikke bli trukket`() {
        val søknadRepoMock = mock<SøknadRepo> {
            on { hentSøknad(any()) } doReturn nySøknad
            on { oppdaterSøknad(any()) }.doNothing()
            on { harSøknadPåbegyntBehandling(any()) } doReturn true
        }
        val sakServiceMock = mock<SakService>()
        val brevServiceMock = mock<BrevService>()
        val oppgaveServiceMock = mock<OppgaveService>()

        LukkSøknadServiceImpl(
            søknadRepo = søknadRepoMock,
            sakService = sakServiceMock,
            brevService = brevServiceMock,
            oppgaveService = oppgaveServiceMock,
            clock = fixedEpochClock,
        ).lukkSøknad(
            LukkSøknadRequest.UtenBrev.BortfaltSøknad(
                søknadId = nySøknad.id,
                saksbehandler = saksbehandler
            )
        ) shouldBe KunneIkkeLukkeSøknad.SøknadHarEnBehandling.left()

        inOrder(
            søknadRepoMock, sakServiceMock, brevServiceMock
        ) {
            verify(søknadRepoMock).hentSøknad(argThat { it shouldBe nySøknad.id })
            verify(søknadRepoMock).harSøknadPåbegyntBehandling(argThat { it shouldBe nySøknad.id })
        }

        verifyNoMoreInteractions(søknadRepoMock, sakServiceMock, brevServiceMock, oppgaveServiceMock)
    }

    @Test
    fun `en allerede trukket søknad skal ikke bli trukket`() {
        val trukketSøknad = lukketSøknad.copy()
        val trekkSøknadRequest = trekkSøknadRequest.copy(
            søknadId = trukketSøknad.id
        )

        val søknadRepoMock = mock<SøknadRepo> {
            on { hentSøknad(any()) } doReturn trukketSøknad
            on { harSøknadPåbegyntBehandling(any()) } doReturn false
        }
        val sakServiceMock = mock<SakService>()

        val brevServiceMock = mock<BrevService>()

        val oppgaveServiceMock = mock<OppgaveService>()
        LukkSøknadServiceImpl(
            søknadRepo = søknadRepoMock,
            sakService = sakServiceMock,
            brevService = brevServiceMock,
            oppgaveService = oppgaveServiceMock,
            clock = fixedEpochClock,
        ).lukkSøknad(trekkSøknadRequest) shouldBe KunneIkkeLukkeSøknad.SøknadErAlleredeLukket.left()

        inOrder(søknadRepoMock) {
            verify(søknadRepoMock).hentSøknad(argThat { it shouldBe trukketSøknad.id })
            verify(søknadRepoMock).harSøknadPåbegyntBehandling(argThat { it shouldBe trukketSøknad.id })
        }
        verifyNoMoreInteractions(søknadRepoMock, sakServiceMock, brevServiceMock, oppgaveServiceMock)
    }

    @Test
    fun `lager brevutkast`() {
        val pdf = "".toByteArray()
        val søknadRepoMock = mock<SøknadRepo> {
            on { hentSøknad(any()) } doReturn nySøknad
        }
        val sakServiceMock = mock<SakService>()
        val brevServiceMock = mock<BrevService> {
            on { lagBrev(any()) } doReturn pdf.right()
        }

        val oppgaveServiceMock = mock<OppgaveService>()
        LukkSøknadServiceImpl(
            søknadRepo = søknadRepoMock,
            sakService = sakServiceMock,
            brevService = brevServiceMock,
            oppgaveService = oppgaveServiceMock,
            clock = fixedEpochClock,
        ).lagBrevutkast(trekkSøknadRequest) shouldBe pdf.right()

        inOrder(
            søknadRepoMock, brevServiceMock
        ) {
            verify(søknadRepoMock).hentSøknad(argThat { it shouldBe nySøknad.id })

            verify(brevServiceMock).lagBrev(
                argThat {
                    it shouldBe TrukketSøknadBrevRequest(nySøknad, 1.januar(2020))
                }
            )
        }

        verifyNoMoreInteractions(søknadRepoMock, sakServiceMock, brevServiceMock, oppgaveServiceMock)
    }

    @Test
    fun `svarer med ukjentBrevType når det ikke skal lages brev`() {

        val søknadRepoMock = mock<SøknadRepo> {
            on { hentSøknad(any()) } doReturn nySøknad
        }
        val sakServiceMock = mock<SakService>()

        val brevServiceMock = mock<BrevService>()

        val oppgaveServiceMock = mock<OppgaveService>()

        LukkSøknadServiceImpl(
            søknadRepo = søknadRepoMock,
            sakService = sakServiceMock,
            brevService = brevServiceMock,
            oppgaveService = oppgaveServiceMock,
            clock = fixedEpochClock,
        ).lagBrevutkast(
            LukkSøknadRequest.UtenBrev.BortfaltSøknad(
                søknadId = nySøknad.id,
                saksbehandler = saksbehandler
            )
        ) shouldBe KunneIkkeLageBrevutkast.UkjentBrevtype.left()

        inOrder(
            søknadRepoMock, sakServiceMock, brevServiceMock
        ) {
            verify(søknadRepoMock).hentSøknad(argThat { it shouldBe nySøknad.id })
        }

        verifyNoMoreInteractions(søknadRepoMock, sakServiceMock, brevServiceMock, oppgaveServiceMock)
    }

    @Test
    fun `Kan ikke sende brevbestilling`() {
        val søknadRepoMock = mock<SøknadRepo> {
            on { hentSøknad(any()) } doReturn nySøknad
            on { oppdaterSøknad(any()) }.doNothing()
            on { harSøknadPåbegyntBehandling(any()) } doReturn false
        }
        val sakServiceMock = mock<SakService> {
            on { hentSak(any<UUID>()) } doReturn sak.right()
        }
        val brevServiceMock = mock<BrevService> {
            on { journalførBrev(any(), any()) } doReturn lukketJournalpostId.right()
            on { distribuerBrev(any()) } doReturn KunneIkkeDistribuereBrev.left()
        }
        val oppgaveServiceMock = mock<OppgaveService> {
            on { lukkOppgave(any()) } doReturn Unit.right()
        }

        LukkSøknadServiceImpl(
            søknadRepo = søknadRepoMock,
            sakService = sakServiceMock,
            brevService = brevServiceMock,
            oppgaveService = oppgaveServiceMock,
            clock = fixedEpochClock,
        ).lukkSøknad(trekkSøknadRequest) shouldBe LukketSøknad.MedMangler.KunneIkkeDistribuereBrev(sak).right()

        inOrder(
            søknadRepoMock, sakServiceMock, brevServiceMock
        ) {
            verify(søknadRepoMock).hentSøknad(argThat { it shouldBe nySøknad.id })
            verify(søknadRepoMock).harSøknadPåbegyntBehandling(argThat { it shouldBe nySøknad.id })
            verify(brevServiceMock).journalførBrev(
                argThat {
                    it shouldBe TrukketSøknadBrevRequest(
                        lukketSøknad.copy(
                            lukketJournalpostId = null,
                        ),
                        1.januar(2020)
                    )
                },
                argThat { it shouldBe nySøknad.sakId }
            )
            verify(brevServiceMock).distribuerBrev(argThat { it shouldBe lukketJournalpostId })
            verify(søknadRepoMock).oppdaterSøknad(
                argThat {
                    it shouldBe lukketSøknad.copy(
                        lukketJournalpostId = lukketJournalpostId,
                        lukketBrevbestillingId = null,
                    )
                }
            )

            verify(sakServiceMock).hentSak(argThat<UUID> { it shouldBe nySøknad.sakId })
        }
        verifyNoMoreInteractions(søknadRepoMock, sakServiceMock, brevServiceMock, oppgaveServiceMock)
    }

    @Test
    fun `lukker journalført søknad uten oppgave`() {
        val søknad = Søknad.Journalført.UtenOppgave(
            sakId = nySøknad.sakId,
            id = nySøknad.id,
            opprettet = nySøknad.opprettet,
            søknadInnhold = nySøknad.søknadInnhold,
            journalpostId = nySøknadJournalpostId,
        )

        val søknadRepoMock = mock<SøknadRepo> {
            on { hentSøknad(any()) } doReturn søknad
            on { oppdaterSøknad(any()) }.doNothing()
            on { harSøknadPåbegyntBehandling(any()) } doReturn false
        }
        val sakServiceMock = mock<SakService> {
            on { hentSak(any<UUID>()) } doReturn sak.right()
        }
        val brevServiceMock = mock<BrevService> {
            on { journalførBrev(any(), any()) } doReturn lukketJournalpostId.right()
            on { distribuerBrev(lukketJournalpostId) } doReturn brevbestillingId.right()
        }

        val oppgaveServiceMock = mock<OppgaveService>()

        val actual = LukkSøknadServiceImpl(
            søknadRepo = søknadRepoMock,
            sakService = sakServiceMock,
            brevService = brevServiceMock,
            oppgaveService = oppgaveServiceMock,
            clock = fixedEpochClock,
        ).lukkSøknad(trekkSøknadRequest)

        actual shouldBe LukketSøknad.UtenMangler(sak).right()

        inOrder(
            søknadRepoMock, sakServiceMock, brevServiceMock
        ) {
            verify(søknadRepoMock).hentSøknad(argThat { it shouldBe søknad.id })
            verify(søknadRepoMock).harSøknadPåbegyntBehandling(argThat { it shouldBe søknad.id })
            verify(brevServiceMock).journalførBrev(
                request = argThat {
                    it shouldBe TrukketSøknadBrevRequest(
                        lukketSøknad.copy(
                            journalpostId = nySøknadJournalpostId,
                        ),
                        1.januar(2020)
                    )
                },
                sakId = argThat {
                    it shouldBe søknad.sakId
                }
            )
            verify(brevServiceMock).distribuerBrev(argThat { it shouldBe lukketJournalpostId })
            verify(søknadRepoMock).oppdaterSøknad(
                argThat {
                    it shouldBe lukketSøknad.copy(
                        journalpostId = nySøknadJournalpostId,
                        lukketJournalpostId = lukketJournalpostId,
                        lukketBrevbestillingId = brevbestillingId
                    )
                },
            )
            verify(sakServiceMock).hentSak(argThat<UUID> { it shouldBe søknad.sakId })
        }
        verifyNoMoreInteractions(søknadRepoMock, sakServiceMock, brevServiceMock, oppgaveServiceMock)
    }
}
