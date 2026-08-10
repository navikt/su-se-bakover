package no.nav.su.se.bakover.service.kontrollsamtale

import arrow.core.Either
import no.nav.su.se.bakover.common.domain.PdfA
import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.PDDocument
import java.io.ByteArrayOutputStream

internal object SammenslåPdf {
    fun slåsSammen(
        forsteside: ByteArray,
        dokument: PdfA,
    ): Either<Throwable, PdfA> =
        Either.catch {
            Loader.loadPDF(forsteside).use { forstesideDokument ->
                Loader.loadPDF(dokument.unsafeBytes()).use { hoveddokument ->
                    PDDocument().use { resultat ->
                        forstesideDokument.pages.forEach { side ->
                            resultat.importPage(side)
                        }
                        hoveddokument.pages.forEach { side ->
                            resultat.importPage(side)
                        }
                        val output = ByteArrayOutputStream()
                        resultat.save(output)

                        PdfA(output.toByteArray())
                    }
                }
            }
        }
}
