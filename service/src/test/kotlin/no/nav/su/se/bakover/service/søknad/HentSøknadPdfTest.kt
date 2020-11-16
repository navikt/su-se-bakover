package no.nav.su.se.bakover.service.søknad

import arrow.core.left
import arrow.core.right
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.inOrder
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.client.ClientError
import no.nav.su.se.bakover.client.pdf.PdfGenerator
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.database.søknad.SøknadRepo
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.SøknadInnhold
import no.nav.su.se.bakover.domain.SøknadInnholdTestdataBuilder
import no.nav.su.se.bakover.service.argThat
import org.junit.jupiter.api.Test
import java.nio.charset.StandardCharsets
import java.util.UUID

class HentSøknadPdfTest {

    @Test
    fun `fant ikke søknad`() {

        val søknadId = UUID.randomUUID()

        val søknadRepoMock = mock<SøknadRepo> {
            on { hentSøknad(any()) } doReturn null
        }
        val pdfGeneratorMock = mock<PdfGenerator>()
        val søknadService = SøknadServiceImpl(
            søknadRepo = søknadRepoMock,
            sakService = mock(),
            sakFactory = mock(),
            pdfGenerator = pdfGeneratorMock,
            dokArkiv = mock(),
            personOppslag = mock(),
            oppgaveService = mock(),
            søknadMetrics = mock()
        )

        val actual = søknadService.hentSøknadPdf(søknadId)
        actual shouldBe KunneIkkeLageSøknadPdf.FantIkkeSøknad.left()
        verify(søknadRepoMock).hentSøknad(argThat { it shouldBe søknadId })
        verifyNoMoreInteractions(søknadRepoMock, pdfGeneratorMock)
    }

    @Test
    fun `kunne ikke generere PDF`() {
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
        val pdfGeneratorMock = mock<PdfGenerator> {
            on { genererPdf(any<SøknadInnhold>()) } doReturn ClientError(0, "").left()
        }
        val søknadService = SøknadServiceImpl(
            søknadRepo = søknadRepoMock,
            sakService = mock(),
            sakFactory = mock(),
            pdfGenerator = pdfGeneratorMock,
            dokArkiv = mock(),
            personOppslag = mock(),
            oppgaveService = mock(),
            søknadMetrics = mock()
        )

        val actual = søknadService.hentSøknadPdf(søknadId)
        actual shouldBe KunneIkkeLageSøknadPdf.KunneIkkeLagePdf.left()

        inOrder(søknadRepoMock, pdfGeneratorMock) {
            verify(søknadRepoMock).hentSøknad(argThat { it shouldBe søknadId })
            verify(pdfGeneratorMock).genererPdf(argThat<SøknadInnhold> { it shouldBe søknadInnhold })
        }
        verifyNoMoreInteractions(søknadRepoMock, pdfGeneratorMock)
    }

    @Test
    fun `henter PDF`() {
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
        val pdf = "".toByteArray(StandardCharsets.UTF_8)
        val pdfGeneratorMock = mock<PdfGenerator> {
            on { genererPdf(any<SøknadInnhold>()) } doReturn pdf.right()
        }
        val søknadService = SøknadServiceImpl(
            søknadRepo = søknadRepoMock,
            sakService = mock(),
            sakFactory = mock(),
            pdfGenerator = pdfGeneratorMock,
            dokArkiv = mock(),
            personOppslag = mock(),
            oppgaveService = mock(),
            søknadMetrics = mock()
        )

        val actual = søknadService.hentSøknadPdf(søknadId)
        actual shouldBe pdf.right()
        inOrder(søknadRepoMock, pdfGeneratorMock) {

            verify(søknadRepoMock).hentSøknad(argThat { it shouldBe søknadId })
            verify(pdfGeneratorMock).genererPdf(argThat<SøknadInnhold> { it shouldBe søknadInnhold })
        }
        verifyNoMoreInteractions(søknadRepoMock, pdfGeneratorMock)
    }
}
