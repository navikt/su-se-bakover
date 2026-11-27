package no.nav.su.se.bakover.service.brev

import arrow.core.left
import arrow.core.right
import dokument.domain.KunneIkkeGenererePdf
import dokument.domain.PdfByggerKlientFeil
import dokument.domain.PensjonPdfByggerKlient
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.domain.PdfA
import no.nav.su.se.bakover.common.infrastructure.config.ApplicationConfig
import no.nav.su.se.bakover.service.brev.migrerTilPdfBygger.testdata.kontrollnotatPdf
import no.nav.su.se.bakover.service.brev.migrerTilPdfBygger.testdata.soknadUførePdf
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.slf4j.Logger

internal class PdfByggerServiceTest {

    private val bytes = "%PDF".toByteArray()
    private val feilmelding = "Kunne ikke generere PDF med pdf-bygger for {}.{}"

    @Test
    fun `sender innholdets egen bestilling til pdf-bygger`() {
        val klient = mock<PensjonPdfByggerKlient> {
            on { lagPdf(any()) } doReturn bytes.right()
        }
        val service = PdfByggerService(klient, ApplicationConfig.RuntimeEnvironment.Prod)

        listOf(kontrollnotatPdf, soknadUførePdf).forEach { innhold ->
            service.genererPdf(innhold) shouldBe PdfA(bytes).right()
            verify(klient).lagPdf(innhold.bestilling())
        }
    }

    @Test
    fun `feil fra pdf-bygger gir KunneIkkeGenererePdf og logger hvilken mal som feilet`() {
        val klient = mock<PensjonPdfByggerKlient> {
            on { lagPdf(any()) } doReturn PdfByggerKlientFeil.left()
        }
        val log = mock<Logger>()

        PdfByggerService(klient, ApplicationConfig.RuntimeEnvironment.Prod, log)
            .genererPdf(kontrollnotatPdf) shouldBe KunneIkkeGenererePdf.left()

        verify(log).error(eq(feilmelding), eq(kontrollnotatPdf.dokumentMal.tittel), eq(""))
    }

    @Test
    fun `lokalt forteller feilmeldingen hvordan pdf-bygger startes`() {
        val klient = mock<PensjonPdfByggerKlient> {
            on { lagPdf(any()) } doReturn PdfByggerKlientFeil.left()
        }
        val log = mock<Logger>()

        PdfByggerService(klient, ApplicationConfig.RuntimeEnvironment.Local, log)
            .genererPdf(kontrollnotatPdf) shouldBe KunneIkkeGenererePdf.left()

        verify(log).error(
            eq(feilmelding),
            eq(kontrollnotatPdf.dokumentMal.tittel),
            argThat<String> { contains("localhost:8082") },
        )
    }
}
