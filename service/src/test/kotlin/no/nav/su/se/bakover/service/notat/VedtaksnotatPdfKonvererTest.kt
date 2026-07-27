package no.nav.su.se.bakover.service.notat

import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.domain.notat.NotatVedlegg
import no.nav.su.se.bakover.test.fixedTidspunkt
import org.apache.pdfbox.Loader
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.util.UUID
import javax.imageio.ImageIO

internal class VedtaksnotatPdfKonvererTest {

    @Test
    fun `konverterer png-vedlegg til ekte pdf`() {
        val vedlegg = notatVedlegg(mimeType = "image/png", filnavn = "bilde.png", innhold = lagBilde("png"))

        val resultat = VedtaksnotatPdfKonverterer.konverterVedlegg(vedlegg)

        resultat.filnavn shouldBe "bilde.pdf"
        resultat.pdf.getContent().erPdf().shouldBeTrue()
        resultat.pdf.getContent().antallSider() shouldBe 1
    }

    @Test
    fun `konverterer jpeg-vedlegg til ekte pdf`() {
        val vedlegg = notatVedlegg(mimeType = "image/jpeg", filnavn = "bilde.jpg", innhold = lagBilde("jpg"))

        val resultat = VedtaksnotatPdfKonverterer.konverterVedlegg(vedlegg)

        resultat.filnavn shouldBe "bilde.pdf"
        resultat.pdf.getContent().erPdf().shouldBeTrue()
        resultat.pdf.getContent().antallSider() shouldBe 1
    }

    @Test
    fun `bytene endres faktisk fra bilde til pdf`() {
        val bildeBytes = lagBilde("png")
        bildeBytes.erPdf() shouldBe false

        val resultat = VedtaksnotatPdfKonverterer.bildeTilPdf("image/png", bildeBytes)

        resultat.getContent().erPdf().shouldBeTrue()
    }

    @Test
    fun `pdf-vedlegg sendes uendret gjennom`() {
        val pdfBytes = VedtaksnotatPdfKonverterer.bildeTilPdf("image/png", lagBilde("png")).getContent()
        val vedlegg = notatVedlegg(mimeType = "application/pdf", filnavn = "dokument.pdf", innhold = pdfBytes)

        val resultat = VedtaksnotatPdfKonverterer.konverterVedlegg(vedlegg)

        resultat.filnavn shouldBe "dokument.pdf"
        resultat.pdf.getContent() shouldBe pdfBytes
    }

    @Test
    fun `kaster for ustottet mimetype`() {
        val vedlegg = notatVedlegg(mimeType = "image/gif", filnavn = "bilde.gif", innhold = byteArrayOf(1, 2, 3))

        assertThrows<IllegalArgumentException> {
            VedtaksnotatPdfKonverterer.konverterVedlegg(vedlegg)
        }
    }

    @Test
    fun `tekstTilPdf gir null nar det ikke finnes tekst`() {
        VedtaksnotatPdfKonverterer.tekstTilPdf(tittel = "Vedtaksnotat", notat = "", attestantNotat = "  ") shouldBe null
    }

    @Test
    fun `tekstTilPdf lager pdf med innhold`() {
        val resultat = VedtaksnotatPdfKonverterer.tekstTilPdf(
            tittel = "Vedtaksnotat",
            notat = "Dette er saksbehandlers notat med litt tekst som må brytes over flere linjer ".repeat(20),
            attestantNotat = "Attestert og godkjent.",
        )

        resultat.shouldNotBeNull()
        resultat.getContent().erPdf().shouldBeTrue()
        resultat.getContent().antallSider() shouldBeGreaterThanOrEqual 1
    }

    private fun notatVedlegg(mimeType: String, filnavn: String, innhold: ByteArray) = NotatVedlegg(
        id = UUID.randomUUID(),
        notatId = UUID.randomUUID(),
        filnavn = filnavn,
        mimeType = mimeType,
        innhold = innhold,
        opprettet = fixedTidspunkt,
    )

    private fun lagBilde(format: String): ByteArray {
        val image = BufferedImage(120, 80, BufferedImage.TYPE_INT_RGB)
        val graphics = image.createGraphics()
        graphics.color = Color.BLUE
        graphics.fillRect(0, 0, 120, 80)
        graphics.dispose()
        return ByteArrayOutputStream().use { out ->
            ImageIO.write(image, format, out)
            out.toByteArray()
        }
    }

    private fun ByteArray.erPdf(): Boolean = size >= 4 &&
        this[0] == '%'.code.toByte() &&
        this[1] == 'P'.code.toByte() &&
        this[2] == 'D'.code.toByte() &&
        this[3] == 'F'.code.toByte()

    private fun ByteArray.antallSider(): Int = Loader.loadPDF(this).use { it.numberOfPages }
}
