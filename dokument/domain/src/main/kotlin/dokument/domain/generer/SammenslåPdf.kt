package dokument.domain.generer

import arrow.core.Either
import no.nav.su.se.bakover.common.domain.PdfA
import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.PDDocument
import java.io.ByteArrayOutputStream

object SammenslåPdf {

    fun leggTilVedlegg(
        dokument: PdfA,
        vedlegg: ByteArray,
    ): Either<Throwable, PdfA> =
        Either.catch {
            Loader.loadPDF(dokument.unsafeBytes()).use { hoveddokument ->
                Loader.loadPDF(vedlegg).use { vedleggDokument ->
                    PDDocument().use { resultat ->
                        hoveddokument.pages.forEach { side ->
                            resultat.importPage(side)
                        }
                        vedleggDokument.pages.forEach { side ->
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
