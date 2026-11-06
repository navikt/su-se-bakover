package no.nav.su.se.bakover.service.brev

import arrow.core.right
import dokument.domain.PdfGenerator
import dokument.domain.PensjonPdfByggerKlient
import dokument.domain.generer.DokumentMal
import dokument.domain.generer.PdfInnhold
import dokument.domain.generer.bygger.PdfBestilling
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.domain.PdfA
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.infrastructure.config.ApplicationConfig
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.getOrFail
import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import java.io.ByteArrayOutputStream

internal class PdfByggerServiceTest {

    private val pdfInnhold = object : PdfInnhold {
        // Mal med både pdfByggerDev = false og pdfByggerProd = false
        override val dokumentMal = DokumentMal.InnvilgetVedtak
        override val sakstype: Sakstype = Sakstype.UFØRE
    }

    @ParameterizedTest
    @EnumSource(ApplicationConfig.RuntimeEnvironment::class)
    fun `bruker gammel klient når malen ikke er aktivert for pdf-bygger`(runtimeEnvironment: ApplicationConfig.RuntimeEnvironment) {
        verifiserGammelKlientBrukes(runtimeEnvironment)
    }

    @ParameterizedTest
    @EnumSource(value = ApplicationConfig.RuntimeEnvironment::class, names = ["Prod"])
    fun `bruker bare pdf-bygger når pdfByggerProd er true i prod`(runtimeEnvironment: ApplicationConfig.RuntimeEnvironment) {
        verifiserPdfByggerKlientBrukes(
            runtimeEnvironment = runtimeEnvironment,
            pdfByggerDev = false,
            pdfByggerProd = true,
            forventGammelKlient = false,
        )
    }

    @ParameterizedTest
    @EnumSource(value = ApplicationConfig.RuntimeEnvironment::class, names = ["Test"])
    fun `bruker kun pdf-bygger når pdfByggerDev er true for unittester`(runtimeEnvironment: ApplicationConfig.RuntimeEnvironment) {
        verifiserPdfByggerKlientBrukes(
            runtimeEnvironment = runtimeEnvironment,
            pdfByggerDev = true,
            pdfByggerProd = false,
            forventGammelKlient = false,
        )
    }

    @ParameterizedTest
    @EnumSource(value = ApplicationConfig.RuntimeEnvironment::class, names = ["Dev", "Local"])
    fun `bruker begge klienter når pdfByggerDev er true i dev, lokalt`(runtimeEnvironment: ApplicationConfig.RuntimeEnvironment) {
        verifiserPdfByggerKlientBrukes(
            runtimeEnvironment = runtimeEnvironment,
            pdfByggerDev = true,
            pdfByggerProd = false,
            forventGammelKlient = true,
        )
    }

    private fun verifiserPdfByggerKlientBrukes(
        runtimeEnvironment: ApplicationConfig.RuntimeEnvironment,
        pdfByggerDev: Boolean,
        pdfByggerProd: Boolean,
        forventGammelKlient: Boolean,
    ) {
        // Ingen ekte mal har pdfByggerProd = true ennå, så vi mocker en konkret mal med ønskede flagg.
        val bestilling = mock<PdfBestilling.Dokument>()
        val mal = mock<DokumentMal.Kontrollnotat> {
            on { tittel } doReturn "Kontrollnotat"
            on { this.pdfByggerDev } doReturn pdfByggerDev
            on { this.pdfByggerProd } doReturn pdfByggerProd
            on { lagDokumentInnhold(any()) } doReturn bestilling
        }
        val innhold = object : PdfInnhold {
            override val dokumentMal = mal
            override val sakstype: Sakstype = Sakstype.UFØRE
        }
        val bytes = lagPdf(antallSider = 1)
        val gammelPdf = PdfA(lagPdf(antallSider = 2))
        val klient = mock<PensjonPdfByggerKlient> {
            on { lagPdf(any()) } doReturn bytes.right()
        }
        val gammelKlient = mock<PdfGenerator> {
            on { genererPdf(any()) } doReturn gammelPdf.right()
        }

        val resultat = PdfByggerService(
            klient = klient,
            gammelKlient = gammelKlient,
            runtimeEnv = runtimeEnvironment,
            clock = fixedClock,
        ).genererPdf(innhold)

        verify(klient).lagPdf(bestilling)
        if (forventGammelKlient) {
            verify(gammelKlient).genererPdf(innhold)
            Loader.loadPDF(resultat.getOrFail().unsafeBytes()).use { pdf ->
                pdf.numberOfPages shouldBe 3
            }
        } else {
            resultat shouldBe PdfA(bytes).right()
            verifyNoInteractions(gammelKlient)
        }
    }

    private fun lagPdf(antallSider: Int): ByteArray = PDDocument().use { dokument ->
        repeat(antallSider) { dokument.addPage(PDPage()) }
        ByteArrayOutputStream().use { output ->
            dokument.save(output)
            output.toByteArray()
        }
    }

    private fun verifiserGammelKlientBrukes(runtimeEnvironment: ApplicationConfig.RuntimeEnvironment) {
        pdfInnhold.dokumentMal.pdfByggerDev shouldBe false
        pdfInnhold.dokumentMal.pdfByggerProd shouldBe false

        val pdf = PdfA("gammel".toByteArray())
        val gammelKlient = mock<PdfGenerator> {
            on { genererPdf(any()) } doReturn pdf.right()
        }
        val klient = mock<PensjonPdfByggerKlient>()

        val resultat = PdfByggerService(
            klient = klient,
            gammelKlient = gammelKlient,
            runtimeEnv = runtimeEnvironment,
            clock = fixedClock,
        ).genererPdf(pdfInnhold)

        resultat shouldBe pdf.right()
        verify(gammelKlient).genererPdf(pdfInnhold)
        verifyNoInteractions(klient)
    }
}
