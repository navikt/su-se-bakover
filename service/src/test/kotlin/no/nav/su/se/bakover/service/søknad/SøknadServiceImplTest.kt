package no.nav.su.se.bakover.service.søknad

import arrow.core.left
import arrow.core.right
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.client.dokarkiv.DokArkiv
import no.nav.su.se.bakover.client.pdf.PdfGenerator
import no.nav.su.se.bakover.client.person.PersonOppslag
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.now
import no.nav.su.se.bakover.database.søknad.SøknadRepo
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.NavIdentBruker.Saksbehandler
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.SakFactory
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.SøknadInnholdTestdataBuilder
import no.nav.su.se.bakover.domain.brev.LagBrevRequest
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.oppdrag.Oppdrag
import no.nav.su.se.bakover.domain.oppgave.OppgaveClient
import no.nav.su.se.bakover.service.brev.BrevService
import no.nav.su.se.bakover.service.doNothing
import no.nav.su.se.bakover.service.sak.SakService
import org.junit.jupiter.api.Test
import java.util.UUID

internal class SøknadServiceImplTest {

    private val sakId = UUID.randomUUID()
    private val saksbehandler = Saksbehandler("Z993156")
    private val sak = Sak(
        id = sakId,
        opprettet = Tidspunkt.now(),
        fnr = Fnr("12345678901"),
        søknader = mutableListOf(),
        behandlinger = mutableListOf(),
        oppdrag = Oppdrag(
            id = UUID30.randomUUID(),
            opprettet = Tidspunkt.now(),
            sakId = sakId,
            utbetalinger = emptyList()
        )
    )
    private val søknad = Søknad(
        sakId = sakId,
        id = UUID.randomUUID(),
        opprettet = Tidspunkt.now(),
        søknadInnhold = SøknadInnholdTestdataBuilder.build(),
        lukket = null
    )
    private val trekkSøknadRequest = LukkSøknadRequest.TrekkSøknad(
        søknadId = søknad.id,
        saksbehandler = Saksbehandler(navIdent = "navIdent"),
        trukketDato = 1.januar(2020)
    )

    @Test
    fun `trekker en søknad`() {
        val søknadRepoMock = mock<SøknadRepo> {
            on { hentSøknad(søknad.id) } doReturn søknad
            on { lukkSøknad(søknad.id, Søknad.Lukket.Trukket(tidspunkt = now(), saksbehandler)) }.doNothing()
            on { harSøknadPåbegyntBehandling(søknad.id) } doReturn false
        }
        val sakServiceMock = mock<SakService> {
            on { hentSak(søknad.sakId) } doReturn sak.right()
        }
        val brevServiceMock = mock<BrevService> {
            on {
                journalførBrev(
                    LagBrevRequest.TrukketSøknad(søknad, 1.januar(2020)),
                    sak.id
                )
            } doReturn JournalpostId("en id").right()

            on { distribuerBrev(JournalpostId("en id")) } doReturn "en bestillings id".right()
        }

        createSøknadServiceImpl(
            søknadRepo = søknadRepoMock,
            sakService = sakServiceMock,
            brevService = brevServiceMock
        ).lukkSøknad(trekkSøknadRequest) shouldBe sak.right()
    }

    @Test
    fun `en søknad med behandling skal ikke bli trukket`() {
        val søknadRepoMock = mock<SøknadRepo> {
            on { hentSøknad(søknadId = søknad.id) } doReturn søknad
            on { lukkSøknad(søknad.id, Søknad.Lukket.Trukket(tidspunkt = now(), saksbehandler)) }.doNothing()
            on { harSøknadPåbegyntBehandling(søknad.id) } doReturn true
        }
        val sakServiceMock = mock<SakService> {
            on { hentSak(sakId = søknad.sakId) } doReturn sak.right()
        }
        createSøknadServiceImpl(
            søknadRepo = søknadRepoMock,
            sakService = sakServiceMock,
        ).lukkSøknad(trekkSøknadRequest) shouldBe KunneIkkeLukkeSøknad.SøknadHarEnBehandling.left()
    }

    @Test
    fun `en allerede trukket søknad skal ikke bli trukket`() {
        val søknad = Søknad(
            sakId = sakId,
            id = UUID.randomUUID(),
            opprettet = Tidspunkt.now(),
            søknadInnhold = SøknadInnholdTestdataBuilder.build(),
            lukket = Søknad.Lukket.Trukket(
                tidspunkt = Tidspunkt.now(),
                saksbehandler = saksbehandler
            )
        )
        val trekkSøknadRequest = trekkSøknadRequest.copy(
            søknadId = søknad.id
        )

        val søknadRepoMock = mock<SøknadRepo> {
            on { hentSøknad(søknadId = søknad.id) } doReturn søknad
            on {
                lukkSøknad(
                    søknad.id,
                    Søknad.Lukket.Trukket(tidspunkt = now(), saksbehandler = saksbehandler)
                )
            }.doNothing()
            on { harSøknadPåbegyntBehandling(søknad.id) } doReturn false
        }
        val sakServiceMock = mock<SakService> {
            on { hentSak(sakId = søknad.sakId) } doReturn sak.right()
        }
        createSøknadServiceImpl(
            søknadRepo = søknadRepoMock,
            sakService = sakServiceMock,
        ).lukkSøknad(trekkSøknadRequest) shouldBe KunneIkkeLukkeSøknad.SøknadErAlleredeLukket.left()
    }

    @Test
    fun `lager brevutkast`() {
        val pdf = "".toByteArray()
        val søknadRepoMock = mock<SøknadRepo> {
            on { hentSøknad(søknad.id) } doReturn søknad
        }
        val brevServiceMock = mock<BrevService> {
            on { lagBrev(LagBrevRequest.TrukketSøknad(søknad, 1.januar(2020))) } doReturn pdf.right()
        }

        createSøknadServiceImpl(
            søknadRepo = søknadRepoMock,
            brevService = brevServiceMock
        ).lagBrevutkastForLukketSøknad(trekkSøknadRequest) shouldBe pdf.right()
    }

    private fun createSøknadServiceImpl(
        søknadRepo: SøknadRepo = mock(),
        sakService: SakService = mock(),
        sakFactory: SakFactory = mock(),
        pdfGenerator: PdfGenerator = mock(),
        dokArkiv: DokArkiv = mock(),
        personOppslag: PersonOppslag = mock(),
        oppgaveClient: OppgaveClient = mock(),
        brevService: BrevService = mock()
    ) = SøknadServiceImpl(
        søknadRepo = søknadRepo,
        sakService = sakService,
        sakFactory = sakFactory,
        pdfGenerator = pdfGenerator,
        dokArkiv = dokArkiv,
        personOppslag = personOppslag,
        oppgaveClient = oppgaveClient,
        brevService = brevService
    )
}
