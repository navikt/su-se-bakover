package no.nav.su.se.bakover.service.søknad

import arrow.core.left
import arrow.core.right
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.database.søknad.SøknadRepo
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.SøknadInnhold
import no.nav.su.se.bakover.domain.SøknadInnholdTestdataBuilder
import no.nav.su.se.bakover.service.argThat
import org.junit.jupiter.api.Test
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
            søknadMetrics = mock()
        )

        val actual = søknadService.hentSøknad(søknadId)
        actual shouldBe FantIkkeSøknad.left()
    }

    @Test
    fun `fant søknad`() {
        val søknadInnhold: SøknadInnhold = SøknadInnholdTestdataBuilder.build()
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
            søknadMetrics = mock()
        )

        val actual = søknadService.hentSøknad(søknadId)
        actual shouldBe søknad.right()

        verify(søknadRepoMock).hentSøknad(argThat { it shouldBe søknadId })
        verifyNoMoreInteractions(søknadRepoMock)
    }
}
