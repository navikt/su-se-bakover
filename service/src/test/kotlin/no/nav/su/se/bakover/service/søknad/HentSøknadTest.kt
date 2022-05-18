package no.nav.su.se.bakover.service.søknad

import arrow.core.left
import arrow.core.right
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.SøknadInnholdTestdataBuilder
import no.nav.su.se.bakover.domain.SøknadsinnholdUføre
import no.nav.su.se.bakover.domain.søknad.SøknadRepo
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.test.fixedClock
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import java.util.UUID

class HentSøknadTest {
    @Test
    fun `fant ikke søknad`() {

        val søknadId = UUID.randomUUID()

        val søknadRepoMock = mock<SøknadRepo> {
            on { hentSøknad(any()) } doReturn null
        }

        val søknadService = SøknadServiceImpl(
            søknadRepo = søknadRepoMock,
            sakService = mock(),
            sakFactory = mock(),
            pdfGenerator = mock(),
            dokArkiv = mock(),
            personService = mock(),
            oppgaveService = mock(),
            søknadMetrics = mock(),
            toggleService = mock(),
            clock = fixedClock
        )

        val actual = søknadService.hentSøknad(søknadId)
        actual shouldBe FantIkkeSøknad.left()
    }

    @Test
    fun `fant søknad`() {
        val søknadInnhold: SøknadsinnholdUføre = SøknadInnholdTestdataBuilder.build()
        val søknadId = UUID.randomUUID()
        val søknad = Søknad.Ny(
            id = søknadId,
            opprettet = Tidspunkt.EPOCH,
            sakId = UUID.randomUUID(),
            søknadInnhold = søknadInnhold,
        )
        val søknadRepoMock = mock<SøknadRepo> {
            on { hentSøknad(any()) } doReturn søknad
        }

        val søknadService = SøknadServiceImpl(
            søknadRepo = søknadRepoMock,
            sakService = mock(),
            sakFactory = mock(),
            pdfGenerator = mock(),
            dokArkiv = mock(),
            personService = mock(),
            oppgaveService = mock(),
            søknadMetrics = mock(),
            toggleService = mock(),
            clock = fixedClock,
        )

        val actual = søknadService.hentSøknad(søknadId)
        actual shouldBe søknad.right()

        verify(søknadRepoMock).hentSøknad(argThat { it shouldBe søknadId })
        verifyNoMoreInteractions(søknadRepoMock)
    }
}
