package no.nav.su.se.bakover.service.søknad

import arrow.core.right
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.database.søknad.AvsluttetSøknadsBehandlingOK
import no.nav.su.se.bakover.database.søknad.SøknadRepo
import no.nav.su.se.bakover.domain.AvsluttSøknadsBehandlingBody
import no.nav.su.se.bakover.domain.AvsluttSøkndsBehandlingBegrunnelse
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.SøknadInnholdTestdataBuilder
import org.junit.jupiter.api.Test
import java.util.UUID

internal class SøknadServiceImplTest {
    @Test
    fun `avslutter en søknadsbehandling`() {
        val sakId = UUID.randomUUID()
        val søknad = Søknad(
            id = UUID.randomUUID(),
            opprettet = Tidspunkt.now(),
            søknadInnhold = SøknadInnholdTestdataBuilder.build(),
            avsluttetBegrunnelse = AvsluttSøkndsBehandlingBegrunnelse.Trukket
        )
        val avsluttSøknadsBehandlingBody = AvsluttSøknadsBehandlingBody(
            sakId = sakId,
            søknadId = søknad.id,
            avsluttSøkndsBehandlingBegrunnelse =
                søknad.avsluttetBegrunnelse ?: AvsluttSøkndsBehandlingBegrunnelse.Trukket
        )
        val repoMock = mock<SøknadRepo> {
            on {
                avsluttSøknadsBehandling(avsluttSøknadsBehandlingBody)
            } doReturn AvsluttetSøknadsBehandlingOK.right()
        }

        SøknadServiceImpl(repoMock).avsluttSøknadsBehandling(avsluttSøknadsBehandlingBody) shouldBe AvsluttetSøknadsBehandlingOK.right()

        verify(repoMock).avsluttSøknadsBehandling(avsluttSøknadsBehandlingBody)
    }
}
