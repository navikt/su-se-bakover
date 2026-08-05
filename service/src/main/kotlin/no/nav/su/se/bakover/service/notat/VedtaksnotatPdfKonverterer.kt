package no.nav.su.se.bakover.service.notat

import no.nav.su.se.bakover.common.domain.PdfA
import no.nav.su.se.bakover.domain.notat.JournalførbartVedlegg
import no.nav.su.se.bakover.domain.notat.NotatVedlegg
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import org.apache.pdfbox.pdmodel.graphics.image.JPEGFactory
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

/**
 * Konverterer notat-tekst og vedlegg til PDF slik at de kan journalføres i Joark.
 *
 * Joark krever at hvert dokument har en dokumentvariant av typen ARKIV, og støtter pr. i dag ikke
 * lagring av bildefiler (PNG/JPEG) som arkivvariant. Derfor må selve bytene konverteres til en
 * ekte PDF før journalføring - det holder ikke å endre mimeType.
 */
internal object VedtaksnotatPdfKonverterer {

    private const val MARGIN = 40f
    private const val BODY_FONT_SIZE = 11f
    private const val TITLE_FONT_SIZE = 16f
    private const val HEADING_FONT_SIZE = 12f
    private const val LINE_HEIGHT = 15f

    private val bodyFont = PDType1Font(Standard14Fonts.FontName.HELVETICA)
    private val boldFont = PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD)

    /**
     * Konverterer et [NotatVedlegg] til et [JournalførbartVedlegg] med innholdet som ekte PDF.
     * PDF-vedlegg sendes gjennom uendret, mens bildefiler (PNG/JPEG) legges inn på en PDF-side.
     */
    fun konverterVedlegg(vedlegg: NotatVedlegg): JournalførbartVedlegg {
        val pdf = when (vedlegg.mimeType.lowercase()) {
            "application/pdf" -> PdfA(vedlegg.innhold)
            "image/png", "image/jpeg", "image/jpg" -> bildeTilPdf(vedlegg.mimeType, vedlegg.innhold)
            else -> throw IllegalArgumentException(
                "Støtter ikke konvertering av mimeType=${vedlegg.mimeType} til PDF for journalføring av vedtaksnotat.",
            )
        }
        return JournalførbartVedlegg(
            filnavn = vedlegg.filnavn.medPdfExtension(),
            pdf = pdf,
        )
    }

    /**
     * Legger et bilde ([innhold]) inn på en A4-side, skalert til å passe med bevart størrelsesforhold,
     * og returnerer en ekte PDF.
     */
    fun bildeTilPdf(mimeType: String, innhold: ByteArray): PdfA {
        val image = ImageIO.read(ByteArrayInputStream(innhold))
            ?: throw IllegalArgumentException(
                "Kunne ikke lese bilde (mimeType=$mimeType) for konvertering til PDF.",
            )
        return PDDocument().use { document ->
            val page = PDPage(PDRectangle.A4)
            document.addPage(page)

            val pdImage = when (mimeType.lowercase()) {
                "image/jpeg", "image/jpg" -> JPEGFactory.createFromImage(document, image)
                else -> LosslessFactory.createFromImage(document, image)
            }

            val maxWidth = PDRectangle.A4.width - 2 * MARGIN
            val maxHeight = PDRectangle.A4.height - 2 * MARGIN
            val scale = minOf(maxWidth / image.width, maxHeight / image.height)
            val drawWidth = image.width * scale
            val drawHeight = image.height * scale
            val x = (PDRectangle.A4.width - drawWidth) / 2
            val y = (PDRectangle.A4.height - drawHeight) / 2

            PDPageContentStream(document, page).use { content ->
                content.drawImage(pdImage, x, y, drawWidth, drawHeight)
            }

            document.tilPdfA()
        }
    }

    /**
     * Rendrer notat-teksten (saksbehandlers notat og attestants notat) til en lesbar PDF som kan
     * brukes som arkivvariant. Returnerer null dersom det ikke finnes noe tekstinnhold.
     */
    fun tekstTilPdf(tittel: String, notat: String, attestantNotat: String): PdfA? {
        if (notat.isBlank() && attestantNotat.isBlank()) {
            return null
        }
        val avsnitt = buildList {
            add(Avsnitt(tittel, boldFont, TITLE_FONT_SIZE))
            if (notat.isNotBlank()) {
                add(Avsnitt("Saksbehandlers notat", boldFont, HEADING_FONT_SIZE))
                add(Avsnitt(notat, bodyFont, BODY_FONT_SIZE))
            }
            if (attestantNotat.isNotBlank()) {
                add(Avsnitt("Attestants notat", boldFont, HEADING_FONT_SIZE))
                add(Avsnitt(attestantNotat, bodyFont, BODY_FONT_SIZE))
            }
        }
        return PDDocument().use { document -> document.rendreAvsnitt(avsnitt) }
    }

    private data class Avsnitt(val tekst: String, val font: PDType1Font, val fontSize: Float)

    private fun PDDocument.rendreAvsnitt(avsnitt: List<Avsnitt>): PdfA {
        val maxWidth = PDRectangle.A4.width - 2 * MARGIN
        var page = PDPage(PDRectangle.A4)
        addPage(page)
        var content = PDPageContentStream(this, page)
        var y = PDRectangle.A4.height - MARGIN

        try {
            for (a in avsnitt) {
                for (rawLine in a.tekst.split("\n")) {
                    for (line in rawLine.wrap(a.font, a.fontSize, maxWidth)) {
                        if (y - LINE_HEIGHT < MARGIN) {
                            content.close()
                            page = PDPage(PDRectangle.A4)
                            addPage(page)
                            content = PDPageContentStream(this, page)
                            y = PDRectangle.A4.height - MARGIN
                        }
                        content.beginText()
                        content.setFont(a.font, a.fontSize)
                        content.newLineAtOffset(MARGIN, y)
                        content.showText(line.sanitize())
                        content.endText()
                        y -= LINE_HEIGHT
                    }
                }
                y -= LINE_HEIGHT / 2
            }
        } finally {
            content.close()
        }
        return tilPdfA()
    }

    private fun String.wrap(font: PDType1Font, fontSize: Float, maxWidth: Float): List<String> {
        if (isEmpty()) return listOf("")
        val lines = mutableListOf<String>()
        var current = StringBuilder()
        for (word in split(" ")) {
            val candidate = if (current.isEmpty()) word else "$current $word"
            if (font.stringWidth(candidate, fontSize) <= maxWidth) {
                current = StringBuilder(candidate)
            } else {
                if (current.isNotEmpty()) {
                    lines.add(current.toString())
                }
                if (font.stringWidth(word, fontSize) <= maxWidth) {
                    current = StringBuilder(word)
                } else {
                    // Enkeltord som er lengre enn linjen brytes hardt.
                    lines.addAll(word.hardWrap(font, fontSize, maxWidth))
                    current = StringBuilder()
                }
            }
        }
        lines.add(current.toString())
        return lines
    }

    private fun String.hardWrap(font: PDType1Font, fontSize: Float, maxWidth: Float): List<String> {
        val lines = mutableListOf<String>()
        var current = StringBuilder()
        for (ch in this) {
            val candidate = current.toString() + ch
            if (font.stringWidth(candidate, fontSize) <= maxWidth) {
                current.append(ch)
            } else {
                lines.add(current.toString())
                current = StringBuilder().append(ch)
            }
        }
        if (current.isNotEmpty()) {
            lines.add(current.toString())
        }
        return lines
    }

    private fun PDType1Font.stringWidth(text: String, fontSize: Float): Float {
        // Fjern tegn som ikke kan tegnes med standardfonten for å unngå unntak ved bredde-/teksttegning.
        return getStringWidth(text.sanitize()) / 1000f * fontSize
    }

    private fun String.sanitize(): String = replace(Regex("[^\\x20-\\xFF]"), "?")

    private fun PDDocument.tilPdfA(): PdfA = ByteArrayOutputStream().use { out ->
        save(out)
        PdfA(out.toByteArray())
    }

    private fun String.medPdfExtension(): String {
        val utenExtension = substringBeforeLast('.', this)
        return "$utenExtension.pdf"
    }
}

internal fun NotatVedlegg.tilJournalførbartVedlegg(): JournalførbartVedlegg =
    VedtaksnotatPdfKonverterer.konverterVedlegg(this)
