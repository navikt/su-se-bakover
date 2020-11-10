package no.nav.su.se.bakover.service.søknad.lukk

import arrow.core.left
import arrow.core.right
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.inOrder
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.database.søknad.SøknadRepo
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.SøknadInnholdTestdataBuilder
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
import java.util.UUID

internal class LukkSøknadServiceImplTest {
    private val sakId = UUID.randomUUID()
    private val saksbehandler = NavIdentBruker.Saksbehandler("Z993156")
    private val søknadInnhold = SøknadInnholdTestdataBuilder.build()
    private val journalpostId = JournalpostId("j")
    private val oppgaveId = OppgaveId("o")
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
    private val journalførtSøknadMedOppgave = nySøknad
        .journalfør(journalpostId)
        .medOppgave(oppgaveId)

    private val trekkSøknadRequest = LukkSøknadRequest.MedBrev.TrekkSøknad(
        søknadId = nySøknad.id,
        saksbehandler = saksbehandler,
        trukketDato = 1.januar(2020)
    )

    @Test
    fun `trekker en søknad og håndterer brev`() {

        val søknadRepoMock = mock<SøknadRepo> {
            on { hentSøknad(any()) } doReturn journalførtSøknadMedOppgave
            on { lukkSøknad(any()) }.doNothing()
            on { harSøknadPåbegyntBehandling(any()) } doReturn false
        }
        val sakServiceMock = mock<SakService> {
            on { hentSak(any<UUID>()) } doReturn sak.right()
        }
        val brevServiceMock = mock<BrevService> {
            on { journalførBrev(any(), any()) } doReturn JournalpostId("en id").right()
            on { distribuerBrev(any()) } doReturn "en bestillings id".right()
        }
        val oppgaveServiceMock = mock<OppgaveService> {
            on { lukkOppgave(any()) } doReturn Unit.right()
        }

        LukkSøknadServiceImpl(
            søknadRepo = søknadRepoMock,
            sakService = sakServiceMock,
            brevService = brevServiceMock,
            oppgaveService = oppgaveServiceMock
        ).lukkSøknad(trekkSøknadRequest) shouldBe sak.right()

        inOrder(
            søknadRepoMock, sakServiceMock, brevServiceMock, oppgaveServiceMock
        ) {
            verify(søknadRepoMock).hentSøknad(argThat { it shouldBe journalførtSøknadMedOppgave.id })
            verify(søknadRepoMock).harSøknadPåbegyntBehandling(argThat { it shouldBe journalførtSøknadMedOppgave.id })
            verify(søknadRepoMock).lukkSøknad(
                argThat {
                    it shouldBe Søknad.Lukket(
                        sakId = sakId,
                        id = journalførtSøknadMedOppgave.id,
                        opprettet = journalførtSøknadMedOppgave.opprettet,
                        søknadInnhold = søknadInnhold,
                        journalpostId = journalpostId,
                        oppgaveId = oppgaveId,
                        lukketTidspunkt = it.lukketTidspunkt,
                        lukketAv = saksbehandler,
                        lukketType = Søknad.Lukket.LukketType.TRUKKET
                    )
                }
            )
            verify(brevServiceMock).journalførBrev(
                request = argThat { it shouldBe TrukketSøknadBrevRequest(journalførtSøknadMedOppgave, 1.januar(2020)) },
                sakId = argThat { it shouldBe journalførtSøknadMedOppgave.sakId }
            )
            verify(brevServiceMock).distribuerBrev(argThat { it shouldBe JournalpostId("en id") })
            verify(sakServiceMock).hentSak(argThat<UUID> { it shouldBe journalførtSøknadMedOppgave.sakId })
            verify(oppgaveServiceMock).lukkOppgave(argThat { it shouldBe oppgaveId })
        }
        verifyNoMoreInteractions(søknadRepoMock, sakServiceMock, brevServiceMock, oppgaveServiceMock)
    }

    @Test
    fun `lukker søknad hvor brev ikke skal sendes`() {
        val søknadRepoMock = mock<SøknadRepo> {
            on { hentSøknad(any()) } doReturn journalførtSøknadMedOppgave
            on { lukkSøknad(any()) }.doNothing()
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
            oppgaveService = oppgaveServiceMock
        ).lukkSøknad(
            LukkSøknadRequest.UtenBrev.AvvistSøknad(
                søknadId = journalførtSøknadMedOppgave.id,
                saksbehandler = saksbehandler
            )
        ) shouldBe sak.right()

        inOrder(
            søknadRepoMock, sakServiceMock, oppgaveServiceMock
        ) {
            verify(søknadRepoMock).hentSøknad(argThat { it shouldBe journalførtSøknadMedOppgave.id })
            verify(søknadRepoMock).harSøknadPåbegyntBehandling(argThat { it shouldBe journalførtSøknadMedOppgave.id })
            verify(søknadRepoMock).lukkSøknad(
                argThat {
                    it shouldBe Søknad.Lukket(
                        sakId = sakId,
                        id = journalførtSøknadMedOppgave.id,
                        opprettet = journalførtSøknadMedOppgave.opprettet,
                        søknadInnhold = søknadInnhold,
                        journalpostId = journalpostId,
                        oppgaveId = oppgaveId,
                        lukketTidspunkt = it.lukketTidspunkt,
                        lukketAv = saksbehandler,
                        lukketType = Søknad.Lukket.LukketType.AVVIST
                    )
                },
            )
            verify(sakServiceMock).hentSak(argThat<UUID> { it shouldBe journalførtSøknadMedOppgave.sakId })
            verify(oppgaveServiceMock).lukkOppgave(argThat { it shouldBe oppgaveId })
        }
        verifyNoMoreInteractions(søknadRepoMock, sakServiceMock, brevServiceMock, oppgaveServiceMock)
    }

    @Test
    fun `en søknad med behandling skal ikke bli trukket`() {
        val søknadRepoMock = mock<SøknadRepo> {
            on { hentSøknad(any()) } doReturn nySøknad
            on { lukkSøknad(any()) }.doNothing()
            on { harSøknadPåbegyntBehandling(any()) } doReturn true
        }
        val sakServiceMock = mock<SakService>()
        val brevServiceMock = mock<BrevService>()
        val oppgaveServiceMock = mock<OppgaveService>()

        LukkSøknadServiceImpl(
            søknadRepo = søknadRepoMock,
            sakService = sakServiceMock,
            brevService = brevServiceMock,
            oppgaveService = oppgaveServiceMock
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
        val trukketSøknad = Søknad.Lukket(
            sakId = sakId,
            id = nySøknad.id,
            opprettet = nySøknad.opprettet,
            søknadInnhold = søknadInnhold,
            journalpostId = null,
            oppgaveId = null,
            lukketTidspunkt = Tidspunkt.EPOCH,
            lukketAv = saksbehandler,
            lukketType = Søknad.Lukket.LukketType.TRUKKET
        )
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
            oppgaveService = oppgaveServiceMock
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
            oppgaveService = oppgaveServiceMock
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
            oppgaveService = oppgaveServiceMock
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
    fun `svarer med feilmelding dersom distribusjon av brev feiler`() {
        val søknadRepoMock = mock<SøknadRepo> {
            on { hentSøknad(any()) } doReturn nySøknad
            on { lukkSøknad(any()) }.doNothing()
            on { harSøknadPåbegyntBehandling(any()) } doReturn false
        }
        val sakServiceMock = mock<SakService> {
            on { hentSak(any<UUID>()) } doReturn sak.right()
        }
        val brevServiceMock = mock<BrevService> {
            on { journalførBrev(any(), any()) } doReturn JournalpostId("en id").right()

            on { distribuerBrev(any()) } doReturn KunneIkkeDistribuereBrev.left()
        }
        val oppgaveServiceMock = mock<OppgaveService>()

        LukkSøknadServiceImpl(
            søknadRepo = søknadRepoMock,
            sakService = sakServiceMock,
            brevService = brevServiceMock,
            oppgaveService = oppgaveServiceMock
        ).lukkSøknad(trekkSøknadRequest) shouldBe KunneIkkeLukkeSøknad.KunneIkkeDistribuereBrev.left()

        inOrder(
            søknadRepoMock, sakServiceMock, brevServiceMock
        ) {
            verify(søknadRepoMock).hentSøknad(argThat { it shouldBe nySøknad.id })
            verify(søknadRepoMock).harSøknadPåbegyntBehandling(argThat { it shouldBe nySøknad.id })
            verify(søknadRepoMock).lukkSøknad(
                argThat {
                    it shouldBe Søknad.Lukket(
                        sakId = sakId,
                        id = nySøknad.id,
                        opprettet = nySøknad.opprettet,
                        søknadInnhold = søknadInnhold,
                        journalpostId = null,
                        oppgaveId = null,
                        lukketTidspunkt = it.lukketTidspunkt,
                        lukketAv = saksbehandler,
                        lukketType = Søknad.Lukket.LukketType.TRUKKET
                    )
                },
            )
            verify(brevServiceMock).journalførBrev(
                argThat {
                    it shouldBe TrukketSøknadBrevRequest(
                        nySøknad,
                        1.januar(2020)
                    )
                },
                argThat { it shouldBe nySøknad.sakId }
            )
            verify(brevServiceMock).distribuerBrev(argThat { it shouldBe JournalpostId("en id") })
        }
        verifyNoMoreInteractions(søknadRepoMock, sakServiceMock, brevServiceMock, oppgaveServiceMock)
    }

    @Test
    fun `lukker søknaden selvom den ikke har oppgave`() {
        val søknad = Søknad.Journalført.UtenOppgave(
            sakId = sakId,
            id = nySøknad.id,
            opprettet = nySøknad.opprettet,
            søknadInnhold = søknadInnhold,
            journalpostId = journalpostId,
        )
        val søknadRepoMock = mock<SøknadRepo> {
            on { hentSøknad(any()) } doReturn søknad
            on { lukkSøknad(any()) }.doNothing()
            on { harSøknadPåbegyntBehandling(any()) } doReturn false
        }
        val sakServiceMock = mock<SakService> {
            on { hentSak(any<UUID>()) } doReturn sak.right()
        }
        val brevServiceMock = mock<BrevService> {
            on { journalførBrev(any(), any()) } doReturn JournalpostId("en id").right()
            on { distribuerBrev(JournalpostId("en id")) } doReturn "en bestillings id".right()
        }

        val oppgaveServiceMock = mock<OppgaveService>()

        LukkSøknadServiceImpl(
            søknadRepo = søknadRepoMock,
            sakService = sakServiceMock,
            brevService = brevServiceMock,
            oppgaveService = oppgaveServiceMock
        ).lukkSøknad(trekkSøknadRequest) shouldBe sak.right()

        inOrder(
            søknadRepoMock, sakServiceMock, brevServiceMock
        ) {
            verify(søknadRepoMock).hentSøknad(argThat { it shouldBe søknad.id })
            verify(søknadRepoMock).harSøknadPåbegyntBehandling(argThat { it shouldBe søknad.id })
            verify(søknadRepoMock).lukkSøknad(
                argThat {
                    it shouldBe Søknad.Lukket(
                        sakId = sakId,
                        id = nySøknad.id,
                        opprettet = nySøknad.opprettet,
                        søknadInnhold = søknadInnhold,
                        journalpostId = journalpostId,
                        oppgaveId = null,
                        lukketTidspunkt = it.lukketTidspunkt,
                        lukketAv = saksbehandler,
                        lukketType = Søknad.Lukket.LukketType.TRUKKET
                    )
                },
            )
            verify(brevServiceMock).journalførBrev(
                request = argThat {
                    it shouldBe TrukketSøknadBrevRequest(
                        søknad,
                        1.januar(2020)
                    )
                },
                sakId = argThat {
                    it shouldBe søknad.sakId
                }
            )
            verify(brevServiceMock).distribuerBrev(argThat { it shouldBe JournalpostId("en id") })
            verify(sakServiceMock).hentSak(argThat<UUID> { it shouldBe søknad.sakId })
        }
        verifyNoMoreInteractions(søknadRepoMock, sakServiceMock, brevServiceMock, oppgaveServiceMock)
    }
}
