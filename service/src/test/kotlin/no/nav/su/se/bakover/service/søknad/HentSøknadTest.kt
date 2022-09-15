package no.nav.su.se.bakover.service.søknad

import arrow.core.left
import arrow.core.right
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.domain.SøknadInnholdTestdataBuilder
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.nySøknad
import no.nav.su.se.bakover.test.veileder
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.util.UUID

class HentSøknadTest {
    @Test
    fun `fant ikke søknad`() {
        SøknadServiceOgMocks(
            søknadRepo = mock {
                on { hentSøknad(any()) } doReturn null
            },
        ).also {
            it.service.hentSøknad(UUID.randomUUID()) shouldBe FantIkkeSøknad.left()
        }
    }

    @Test
    fun `fant søknad`() {
        val søknad = nySøknad(
            clock = fixedClock,
            sakId = UUID.randomUUID(),
            søknadInnhold = SøknadInnholdTestdataBuilder.build(),
            søknadInnsendtAv = veileder,
        )

        SøknadServiceOgMocks(
            søknadRepo = mock {
                on { hentSøknad(any()) } doReturn søknad
            },
        ).also {
            it.service.hentSøknad(UUID.randomUUID()) shouldBe søknad.right()
        }
    }
}
