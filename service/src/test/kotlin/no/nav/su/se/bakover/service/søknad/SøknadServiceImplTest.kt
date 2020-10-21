package no.nav.su.se.bakover.service.søknad

import arrow.core.left
import arrow.core.right
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.now
import no.nav.su.se.bakover.common.startOfDay
import no.nav.su.se.bakover.database.søknad.SøknadRepo
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.NavIdentBruker.Saksbehandler
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.SøknadInnholdTestdataBuilder
import no.nav.su.se.bakover.domain.oppdrag.Oppdrag
import no.nav.su.se.bakover.service.doNothing
import no.nav.su.se.bakover.service.sak.SakService
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.ZoneOffset
import java.util.UUID

internal class SøknadServiceImplTest {
    private val fixedClock = Clock.fixed(1.januar(2020).plusDays(9).startOfDay().instant, ZoneOffset.UTC)

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
            on { lukkSøknad(søknad.id, Søknad.Lukket.Trukket(tidspunkt = now(), saksbehandler, "")) }.doNothing()
            on { harSøknadPåbegyntBehandling(søknad.id) } doReturn false
        }
        val sakServiceMock = mock<SakService> {
            on { hentSak(sakId = søknad.sakId) } doReturn sak.right()
        }

        SøknadServiceImpl(
            søknadRepo = søknadRepoMock,
            sakService = sakServiceMock,
            sakFactory = mock(),
            pdfGenerator = mock(),
            dokArkiv = mock(),
            personOppslag = mock(),
            oppgaveClient = mock(),

        ).trekkSøknad(søknad.id, saksbehandler, "") shouldBe sak.right()
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
            on { lukkSøknad(søknad.id, Søknad.Lukket.Trukket(tidspunkt = now(), saksbehandler, "")) }.doNothing()
            on { harSøknadPåbegyntBehandling(søknad.id) } doReturn true
        }
        val sakServiceMock = mock<SakService> {
            on { hentSak(sakId = søknad.sakId) } doReturn sak.right()
        }
        SøknadServiceImpl(
            søknadRepo = søknadRepoMock,
            sakService = sakServiceMock,
            sakFactory = mock(),
            pdfGenerator = mock(),
            dokArkiv = mock(),
            personOppslag = mock(),
            oppgaveClient = mock(),
        ).trekkSøknad(søknadId = søknad.id, saksbehandler, "") shouldBe KunneIkkeLukkeSøknad.SøknadHarEnBehandling.left()
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
                begrunnelse = ""
            )
        )
        val søknadRepoMock = mock<SøknadRepo> {
            on { hentSøknad(søknadId = søknad.id) } doReturn søknad
            on { lukkSøknad(søknad.id, Søknad.Lukket.Trukket(tidspunkt = now(), saksbehandler, "")) }.doNothing()
            on { harSøknadPåbegyntBehandling(søknad.id) } doReturn false
        }
        val sakServiceMock = mock<SakService> {
            on { hentSak(sakId = søknad.sakId) } doReturn sak.right()
        }
        SøknadServiceImpl(
            søknadRepo = søknadRepoMock,
            sakService = sakServiceMock,
            sakFactory = mock(),
            pdfGenerator = mock(),
            dokArkiv = mock(),
            personOppslag = mock(),
            oppgaveClient = mock(),
        ).trekkSøknad(søknadId = søknad.id, saksbehandler, "") shouldBe KunneIkkeLukkeSøknad.SøknadErAlleredeLukket.left()
    }
}
