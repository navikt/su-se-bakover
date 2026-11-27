package no.nav.su.se.bakover.service.brev.maltest

import arrow.core.getOrElse
import dokument.domain.generer.PdfInnhold
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.ints.shouldBeGreaterThan
import no.nav.su.se.bakover.dokument.infrastructure.client.PdfByggerKlientImpl
import no.nav.su.se.bakover.service.brev.PDF_BYGGER_RESSURS
import no.nav.su.se.bakover.service.brev.PdfByggerExtension
import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock

/**
 * Nivå 2: sender de faste variantene av hver mal til pdf-bygger i en testcontainer.
 *
 * - E9: pdf-bygger godtar bestillingen og lager en PDF som kan åpnes og har minst én side.
 * - E10: saksbehandlers fritekst kommer fram i PDF-en uten at ord eller tegn forsvinner eller byttes ut.
 *
 * Variantene er faste (se [fasteVarianter]), så en feil her kan alltid gjenskapes med samme kode.
 */
@Tag("pdf-integrasjon")
@ExtendWith(PdfByggerExtension::class)
@ResourceLock(PDF_BYGGER_RESSURS)
@Execution(ExecutionMode.SAME_THREAD)
internal class MalRenderTest(private val klient: PdfByggerKlientImpl) {

    @TestFactory
    fun `pdf-bygger lager gyldig PDF for alle maler og varianter`(): List<DynamicTest> =
        malEksempler.flatMap { (mal, eksempel) ->
            fasteVarianter(eksempel).map { (variant, innhold) ->
                DynamicTest.dynamicTest("$mal, $variant") { sjekkPdf(innhold) }
            }
        }

    private fun sjekkPdf(innhold: PdfInnhold) {
        val pdf = klient.lagPdf(bestillingFor(innhold)).getOrElse {
            throw AssertionError("E9: pdf-bygger avviste bestillingen. Svaret fra pdf-bygger står i sikkerloggen.")
        }
        val tekst = Loader.loadPDF(pdf).use { dokument ->
            dokument.numberOfPages shouldBeGreaterThan 0
            PDFTextStripper().getText(dokument)
        }
        innhold.fritekst()?.let { fritekstFinnesI(it, vask(tekst)) }
    }
}

private val ordmønster = Regex("""[\p{L}\p{N}]+""")
private val orddelingVedLinjeskift = Regex("""-\s*\n\s*""")

/** Tegn som LaTeX eller PDF-uttrekket bytter til en typografisk variant av det samme tegnet. */
private val typografiskeVarianter = mapOf(
    '’' to '\'',
    '‘' to '\'',
    '”' to '"',
    '“' to '"',
    '˜' to '~',
    'ˆ' to '^',
    '−' to '-',
    '‑' to '-',
)

/** Samme rensing som de gamle migreringstestene: pdf-bygger legger inn usynlige skilletegn. */
private fun vask(tekst: String): String = tekst.replace("​", "").replace(' ', ' ')

/**
 * Ordrekkefølgen sjekkes ikke, fordi et sideskift kan legge topp- og bunntekst midt i et avsnitt.
 * Orddeling ved linjeskift telles både som ett og som to ord, fordi uttrekket ikke skiller
 * orddeling fra en bindestrek som står i teksten.
 */
private fun fritekstFinnesI(fritekst: String, pdfTekst: String) {
    val ordIPdf = størst(ordtelling(pdfTekst), ordtelling(pdfTekst.replace(orddelingVedLinjeskift, "")))
    ordtelling(fritekst).filter { (ord, antall) -> (ordIPdf[ord] ?: 0) < antall }.keys.shouldBeEmpty()

    val tegnIPdf = pdfTekst.map { typografiskeVarianter[it] ?: it }.toSet()
    (fritekst.filterNot { it.isLetterOrDigit() || it.isWhitespace() }.toSet() - tegnIPdf).shouldBeEmpty()
}

private fun ordtelling(tekst: String): Map<String, Int> =
    ordmønster.findAll(tekst).map { it.value }.groupingBy { it }.eachCount()

private fun størst(a: Map<String, Int>, b: Map<String, Int>): Map<String, Int> =
    (a.keys + b.keys).associateWith { maxOf(a[it] ?: 0, b[it] ?: 0) }
