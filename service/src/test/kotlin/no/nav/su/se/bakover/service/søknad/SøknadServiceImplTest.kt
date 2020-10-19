package no.nav.su.se.bakover.service.søknad

import arrow.core.left
import arrow.core.right
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.client.ClientError
import no.nav.su.se.bakover.client.stubs.pdf.PdfGeneratorStub
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.now
import no.nav.su.se.bakover.database.søknad.SøknadRepo
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.Saksbehandler
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.SøknadInnholdTestdataBuilder
import no.nav.su.se.bakover.domain.oppdrag.Oppdrag
import no.nav.su.se.bakover.service.brev.BrevService
import no.nav.su.se.bakover.service.doNothing
import no.nav.su.se.bakover.service.sak.SakService
import org.junit.jupiter.api.Test
import java.util.UUID

internal class SøknadServiceImplTest {
    private val sakId = UUID.randomUUID()
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

    @Test
    fun `trekker en søknad`() {
        val sakId = UUID.randomUUID()
        val søknad = Søknad(
            sakId = sakId,
            id = UUID.randomUUID(),
            opprettet = Tidspunkt.now(),
            søknadInnhold = SøknadInnholdTestdataBuilder.build(),
            lukket = null
        )
        val saksbehandler = Saksbehandler("Z993156")
        val søknadRepoMock = mock<SøknadRepo> {
            on { hentSøknad(søknadId = søknad.id) } doReturn søknad
            on { lukkSøknad(søknad.id, Søknad.Lukket.Trukket(tidspunkt = now(), saksbehandler)) }.doNothing()
            on { harSøknadPåbegyntBehandling(søknad.id) } doReturn false
        }
        val sakServiceMock = mock<SakService> {
            on { hentSak(sakId = søknad.sakId) } doReturn sak.right()
        }
        val brevServiceMock = mock<BrevService> {
            on {
                journalførLukketSøknadOgSendBrev(
                    sakId = sakId
                )
            } doReturn "en bestillingsid".right()
        }

        SøknadServiceImpl(
            søknadRepo = søknadRepoMock,
            sakService = sakServiceMock,
            brevService = brevServiceMock
        ).lukkSøknad(søknad.id, saksbehandler) shouldBe sak.right()
    }

    @Test
    fun `en søknad med behandling skal ikke bli trukket`() {
        val sakId = UUID.randomUUID()
        val søknad = Søknad(
            sakId = sakId,
            id = UUID.randomUUID(),
            opprettet = Tidspunkt.now(),
            søknadInnhold = SøknadInnholdTestdataBuilder.build(),
            lukket = null
        )
        val saksbehandler = Saksbehandler("Z993156")
        val søknadRepoMock = mock<SøknadRepo> {
            on { hentSøknad(søknadId = søknad.id) } doReturn søknad
            on { harSøknadPåbegyntBehandling(søknad.id) } doReturn true
        }
        val sakServiceMock = mock<SakService> {
            on { hentSak(sakId = søknad.sakId) } doReturn sak.right()
        }
        val brevServiceMock = mock<BrevService> {
            on {
                journalførLukketSøknadOgSendBrev(
                    sakId = UUID.randomUUID()
                )
            } doReturn "en bestillingsid".right()
        }

        SøknadServiceImpl(
            søknadRepo = søknadRepoMock,
            sakService = sakServiceMock,
            brevService = brevServiceMock
        ).lukkSøknad(søknadId = søknad.id, saksbehandler) shouldBe KunneIkkeLukkeSøknad.SøknadHarEnBehandling.left()
    }

    @Test
    fun `en allerede trukket søknad skal ikke bli trukket`() {
        val sakId = UUID.randomUUID()
        val saksbehandler = Saksbehandler("Z993156")
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
        val søknadRepoMock = mock<SøknadRepo> {
            on { hentSøknad(søknadId = søknad.id) } doReturn søknad
            on { lukkSøknad(søknad.id, Søknad.Lukket.Trukket(tidspunkt = now(), saksbehandler)) }.doNothing()
            on { harSøknadPåbegyntBehandling(søknad.id) } doReturn false
        }
        val sakServiceMock = mock<SakService> {
            on { hentSak(sakId = søknad.sakId) } doReturn sak.right()
        }
        val brevServiceMock = mock<BrevService> {
            on {
                journalførLukketSøknadOgSendBrev(
                    sakId = UUID.randomUUID()
                )
            } doReturn "en bestillingsid".right()
        }

        SøknadServiceImpl(
            søknadRepo = søknadRepoMock,
            sakService = sakServiceMock,
            brevService = brevServiceMock
        ).lukkSøknad(søknadId = søknad.id, saksbehandler) shouldBe KunneIkkeLukkeSøknad.SøknadErAlleredeLukket.left()
    }

    @Test
    fun `generer et brevutkast for en lukket søknad`() {
        val sakId = UUID.randomUUID()
        val saksbehandler = Saksbehandler("Z993156")
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
        val brevPdf = PdfGeneratorStub.pdf.toByteArray()

        val søknadRepoMock = mock<SøknadRepo> {
            on { hentSøknad(søknadId = søknad.id) } doReturn søknad
            on { lukkSøknad(søknad.id, Søknad.Lukket.Trukket(tidspunkt = now(), saksbehandler)) }.doNothing()
            on { harSøknadPåbegyntBehandling(søknad.id) } doReturn false
        }
        val sakServiceMock = mock<SakService> {
            on { hentSak(sakId = søknad.sakId) } doReturn sak.right()
        }
        val brevServiceMock = mock<BrevService> {
            on {
                lagLukketSøknadBrevutkast(
                    sakId = søknad.sakId,
                    typeLukking = Søknad.TypeLukking.Trukket
                )
            } doReturn brevPdf.right()
        }
        SøknadServiceImpl(
            søknadRepo = søknadRepoMock,
            sakService = sakServiceMock,
            brevService = brevServiceMock
        ).lagLukketSøknadBrevutkast(
            søknadId = søknad.id,
            typeLukking = Søknad.TypeLukking.Trukket
        ) shouldBe brevPdf.right()
    }

    @Test
    fun `får ikke brevutkast hvis brevService returnere clientError`() {
        val sakId = UUID.randomUUID()
        val saksbehandler = Saksbehandler("Z993156")
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

        val søknadRepoMock = mock<SøknadRepo> {
            on { hentSøknad(søknadId = søknad.id) } doReturn søknad
            on { lukkSøknad(søknad.id, Søknad.Lukket.Trukket(tidspunkt = now(), saksbehandler)) }.doNothing()
            on { harSøknadPåbegyntBehandling(søknad.id) } doReturn false
        }
        val sakServiceMock = mock<SakService> {
            on { hentSak(sakId = søknad.sakId) } doReturn sak.right()
        }
        val brevServiceMock = mock<BrevService> {
            on {
                lagLukketSøknadBrevutkast(
                    sakId = søknad.sakId,
                    typeLukking = Søknad.TypeLukking.Trukket
                )
            } doReturn ClientError(
                httpStatus = 400,
                message = "Noe gikk galt og jeg gir deg ikke et brev"
            ).left()
        }
        SøknadServiceImpl(
            søknadRepo = søknadRepoMock,
            sakService = sakServiceMock,
            brevService = brevServiceMock
        ).lagLukketSøknadBrevutkast(
            søknadId = søknad.id,
            typeLukking = Søknad.TypeLukking.Trukket
        ) shouldBe KunneIkkeLageBrevutkast.FeilVedGenereringAvBrevutkast.left()
    }
}
