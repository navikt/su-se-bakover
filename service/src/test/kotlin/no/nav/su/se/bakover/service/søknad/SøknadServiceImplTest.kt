package no.nav.su.se.bakover.service.søknad

import arrow.core.left
import arrow.core.right
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.now
import no.nav.su.se.bakover.database.behandling.BehandlingRepo
import no.nav.su.se.bakover.database.søknad.SøknadRepo
import no.nav.su.se.bakover.domain.Saksbehandler
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.SøknadInnholdTestdataBuilder
import no.nav.su.se.bakover.domain.SøknadTrukket
import no.nav.su.se.bakover.service.doNothing
import org.junit.jupiter.api.Test
import java.util.UUID

internal class SøknadServiceImplTest {
    @Test
    fun `trekker en søknadsbehandling`() {
        val sakId = UUID.randomUUID()
        val søknad = Søknad(
            sakId = sakId,
            id = UUID.randomUUID(),
            opprettet = Tidspunkt.now(),
            søknadInnhold = SøknadInnholdTestdataBuilder.build(),
            søknadTrukket = null
        )
        val saksbehandler = Saksbehandler("Z993156")
        val søknadRepoMock = mock<SøknadRepo> {
            on { hentSøknad(søknadId = søknad.id) } doReturn søknad
            on { trekkSøknad(søknad.id, SøknadTrukket(tidspunkt = now(), saksbehandler)) }.doNothing()
        }
        val behandlingRepoMock = mock<BehandlingRepo> {
            on { harSøknadsbehandling(søknad.id) } doReturn false
        }
        SøknadServiceImpl(
            søknadRepo = søknadRepoMock,
            behandlingRepo = behandlingRepoMock
        ).trekkSøknad(søknad.id, saksbehandler) shouldBe SøknadTrukketOk.right()
    }
    @Test
    fun `en søknad med behandling skal ikke bli trukket`() {
        val sakId = UUID.randomUUID()
        val søknad = Søknad(
            sakId = sakId,
            id = UUID.randomUUID(),
            opprettet = Tidspunkt.now(),
            søknadInnhold = SøknadInnholdTestdataBuilder.build(),
            søknadTrukket = null
        )
        val saksbehandler = Saksbehandler("Z993156")
        val søknadRepoMock = mock<SøknadRepo> {
            on { hentSøknad(søknadId = søknad.id) } doReturn søknad
            on { trekkSøknad(søknad.id, SøknadTrukket(tidspunkt = now(), saksbehandler)) }.doNothing()
        }
        val behandlingRepoMock = mock<BehandlingRepo> {
            on { harSøknadsbehandling(søknad.id) } doReturn true
        }
        SøknadServiceImpl(
            søknadRepo = søknadRepoMock,
            behandlingRepo = behandlingRepoMock
        ).trekkSøknad(søknadId = søknad.id, saksbehandler) shouldBe SøknadServiceFeil.SøknadHarEnBehandling.left()
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
            søknadTrukket = SøknadTrukket(
                tidspunkt = Tidspunkt.now(),
                saksbehandler = saksbehandler
            )
        )
        val søknadRepoMock = mock<SøknadRepo> {
            on { hentSøknad(søknadId = søknad.id) } doReturn søknad
            on { trekkSøknad(søknad.id, SøknadTrukket(tidspunkt = now(), saksbehandler)) }.doNothing()
        }
        val behandlingRepoMock = mock<BehandlingRepo> {
            on { harSøknadsbehandling(søknad.id) } doReturn true
        }
        SøknadServiceImpl(
            søknadRepo = søknadRepoMock,
            behandlingRepo = behandlingRepoMock
        ).trekkSøknad(søknadId = søknad.id, saksbehandler) shouldBe SøknadServiceFeil.SøknadErAlleredeTrukket.left()
    }
}
