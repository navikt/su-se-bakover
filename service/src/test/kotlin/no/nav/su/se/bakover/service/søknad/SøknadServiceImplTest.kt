package no.nav.su.se.bakover.service.søknad

import arrow.core.left
import arrow.core.right
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import io.kotest.matchers.shouldBe
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
import no.nav.su.se.bakover.service.brev.KunneIkkeLageBrev
import no.nav.su.se.bakover.service.doNothing
import no.nav.su.se.bakover.service.sak.SakService
import org.junit.jupiter.api.Test
import java.time.LocalDate
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
    private val lukketSøknad = Søknad.Lukket.Trukket(
        tidspunkt = Tidspunkt.now(),
        saksbehandler = Saksbehandler(navIdent = "12345"),
        datoSøkerTrakkSøknad = LocalDate.now()
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
            on {
                lukkSøknad(
                    søknad.id,
                    Søknad.Lukket.Trukket(
                        tidspunkt = now(),
                        saksbehandler = saksbehandler,
                        datoSøkerTrakkSøknad = LocalDate.now()
                    )
                )
            }.doNothing()
            on { harSøknadPåbegyntBehandling(søknad.id) } doReturn false
        }
        val sakServiceMock = mock<SakService> {
            on { hentSak(sakId = søknad.sakId) } doReturn sak.right()
        }
        val brevServiceMock = mock<BrevService> {
            on {
                journalførLukketSøknadOgSendBrev(
                    sakId = sakId,
                    søknad = søknad,
                    lukketSøknad = lukketSøknad
                )
            } doReturn "en bestillingsid".right()
        }

        SøknadServiceImpl(
            søknadRepo = søknadRepoMock,
            sakService = sakServiceMock,
            brevService = brevServiceMock
        ).lukkSøknad(
            søknadId = søknad.id,
            lukketSøknad = lukketSøknad
        ) shouldBe sak.right()
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
                    sakId = UUID.randomUUID(),
                    søknad = søknad,
                    lukketSøknad = lukketSøknad
                )
            } doReturn "en bestillingsid".right()
        }

        SøknadServiceImpl(
            søknadRepo = søknadRepoMock,
            sakService = sakServiceMock,
            brevService = brevServiceMock
        ).lukkSøknad(
            søknadId = søknad.id,
            lukketSøknad = lukketSøknad
        ) shouldBe KunneIkkeLukkeSøknad.SøknadHarEnBehandling.left()
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
                saksbehandler = saksbehandler,
                datoSøkerTrakkSøknad = LocalDate.now()
            )
        )
        val søknadRepoMock = mock<SøknadRepo> {
            on { hentSøknad(søknadId = søknad.id) } doReturn søknad
            on {
                lukkSøknad(
                    søknad.id,
                    Søknad.Lukket.Trukket(
                        tidspunkt = now(),
                        saksbehandler = saksbehandler,
                        datoSøkerTrakkSøknad = LocalDate.now()
                    )
                )
            }.doNothing()
            on { harSøknadPåbegyntBehandling(søknad.id) } doReturn false
        }
        val sakServiceMock = mock<SakService> {
            on { hentSak(sakId = søknad.sakId) } doReturn sak.right()
        }
        val brevServiceMock = mock<BrevService> {
            on {
                journalførLukketSøknadOgSendBrev(
                    sakId = UUID.randomUUID(),
                    søknad = søknad,
                    lukketSøknad = lukketSøknad
                )
            } doReturn "en bestillingsid".right()
        }

        SøknadServiceImpl(
            søknadRepo = søknadRepoMock,
            sakService = sakServiceMock,
            brevService = brevServiceMock
        ).lukkSøknad(
            søknadId = søknad.id,
            lukketSøknad = lukketSøknad
        ) shouldBe KunneIkkeLukkeSøknad.SøknadErAlleredeLukket.left()
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
                saksbehandler = saksbehandler,
                datoSøkerTrakkSøknad = LocalDate.now()
            )
        )
        val brevPdf = "some-pdf-document".toByteArray()

        val søknadRepoMock = mock<SøknadRepo> {
            on { hentSøknad(søknadId = søknad.id) } doReturn søknad
            on { lukkSøknad(søknad.id, Søknad.Lukket.Trukket(tidspunkt = now(), saksbehandler, datoSøkerTrakkSøknad = LocalDate.now())) }.doNothing()
            on { harSøknadPåbegyntBehandling(søknad.id) } doReturn false
        }
        val sakServiceMock = mock<SakService> {
            on { hentSak(sakId = søknad.sakId) } doReturn sak.right()
        }
        val brevServiceMock = mock<BrevService> {
            on {
                lagLukketSøknadBrevutkast(
                    søknad = søknad,
                    lukketSøknad = lukketSøknad
                )
            } doReturn brevPdf.right()
        }
        SøknadServiceImpl(
            søknadRepo = søknadRepoMock,
            sakService = sakServiceMock,
            brevService = brevServiceMock
        ).lagLukketSøknadBrevutkast(
            søknadId = søknad.id,
            lukketSøknad = lukketSøknad
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
                saksbehandler = saksbehandler,
                datoSøkerTrakkSøknad = LocalDate.now()
            )
        )

        val søknadRepoMock = mock<SøknadRepo> {
            on { hentSøknad(søknadId = søknad.id) } doReturn søknad
            on {
                lukkSøknad(
                    søknad.id,
                    Søknad.Lukket.Trukket(
                        tidspunkt = now(),
                        saksbehandler = saksbehandler,
                        datoSøkerTrakkSøknad = LocalDate.now()
                    )
                )
            }.doNothing()
            on { harSøknadPåbegyntBehandling(søknad.id) } doReturn false
        }
        val sakServiceMock = mock<SakService> {
            on { hentSak(sakId = søknad.sakId) } doReturn sak.right()
        }
        val brevServiceMock = mock<BrevService> {
            on {
                lagLukketSøknadBrevutkast(
                    søknad = søknad,
                    lukketSøknad = lukketSøknad
                )
            } doReturn KunneIkkeLageBrev.KunneIkkeGenererePdf.left()
        }
        SøknadServiceImpl(
            søknadRepo = søknadRepoMock,
            sakService = sakServiceMock,
            brevService = brevServiceMock
        ).lagLukketSøknadBrevutkast(
            søknadId = søknad.id,
            lukketSøknad = lukketSøknad
        ) shouldBe KunneIkkeLageBrevutkast.FeilVedGenereringAvBrevutkast.left()
    }
}
