package no.nav.su.se.bakover.service.brev

import arrow.core.right
import dokument.domain.PdfGenerator
import dokument.domain.PensjonPdfByggerKlient
import dokument.domain.generer.DokumentMal
import dokument.domain.generer.PdfInnhold
import dokument.domain.generer.bygger.PdfBestilling
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.domain.KjøreMiljø
import no.nav.su.se.bakover.common.domain.PdfA
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.test.fixedClock
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions

internal class PdfByggerServiceTest {

    private val pdfInnhold = object : PdfInnhold {
        // Mal med både pdfByggerDev = false og pdfByggerProd = false
        override val dokumentMal: DokumentMal = DokumentMal.InnvilgetVedtak
        override val sakstype: Sakstype = Sakstype.UFØRE
    }

    @ParameterizedTest
    @EnumSource(KjøreMiljø::class)
    fun `bruker gammel klient når malen ikke er aktivert for pdf-bygger`(kjøreMiljø: KjøreMiljø) {
        verifiserGammelKlientBrukes(kjøreMiljø)
    }

    @ParameterizedTest
    @EnumSource(KjøreMiljø::class)
    fun `bruker pdf-bygger når pdfByggerProd er true`(kjøreMiljø: KjøreMiljø) {
        verifiserKlientBrukes(kjøreMiljø = kjøreMiljø, pdfByggerDev = false, pdfByggerProd = true)
    }

    @Test
    fun `bruker pdf-bygger når pdfByggerDev er true og kjøreMiljø er dev`() {
        verifiserKlientBrukes(kjøreMiljø = KjøreMiljø.DEV, pdfByggerDev = true, pdfByggerProd = false)
    }

    private fun verifiserKlientBrukes(
        kjøreMiljø: KjøreMiljø,
        pdfByggerDev: Boolean,
        pdfByggerProd: Boolean,
    ) {
        // Ingen ekte mal har pdfByggerProd = true ennå, så vi mocker en konkret mal med ønskede flagg.
        val bestilling = mock<PdfBestilling.Dokument>()
        val mal = mock<DokumentMal.Kontrollnotat> {
            on { tittel } doReturn "Kontrollnotat"
            on { this.pdfByggerDev } doReturn pdfByggerDev
            on { this.pdfByggerProd } doReturn pdfByggerProd
            on { lagInnhold } doReturn { _: String, _: PdfInnhold -> bestilling }
        }
        val innhold = object : PdfInnhold {
            override val dokumentMal: DokumentMal = mal
            override val sakstype: Sakstype = Sakstype.UFØRE
        }
        val bytes = "ny".toByteArray()
        val klient = mock<PensjonPdfByggerKlient> {
            on { lagPdf(any()) } doReturn bytes.right()
        }
        val gammelKlient = mock<PdfGenerator>()

        val resultat = PdfByggerService(
            klient = klient,
            gammelKlient = gammelKlient,
            kjøreMiljø = kjøreMiljø,
            clock = fixedClock,
        ).genererPdf(innhold)

        resultat shouldBe PdfA(bytes).right()
        verify(klient).lagPdf(bestilling)
        verifyNoInteractions(gammelKlient)
    }

    private fun verifiserGammelKlientBrukes(kjøreMiljø: KjøreMiljø) {
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
            kjøreMiljø = kjøreMiljø,
            clock = fixedClock,
        ).genererPdf(pdfInnhold)

        resultat shouldBe pdf.right()
        verify(gammelKlient).genererPdf(pdfInnhold)
        verifyNoInteractions(klient)
    }
}
