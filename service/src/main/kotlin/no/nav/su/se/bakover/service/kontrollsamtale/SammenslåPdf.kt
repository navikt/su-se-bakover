package no.nav.su.se.bakover.service.kontrollsamtale

import no.nav.su.se.bakover.common.domain.PdfA
import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.PDDocument
import java.io.ByteArrayOutputStream

internal object SammenslåPdf {
    fun slåsSammen(
        forsteside: ByteArray,
        dokument: PdfA,
    ): PdfA {
        Loader.loadPDF(forsteside).use { forstesideDokument ->
            Loader.loadPDF(dokument.unsafeBytes()).use { hoveddokument ->
                println(
                    "SammenslåPdf: forsteside=${forstesideDokument.numberOfPages} sider, " + "kontrollnotat=${hoveddokument.numberOfPages} sider",
                )
                PDDocument().use { resultat ->
                    forstesideDokument.pages.forEach { side ->
                        resultat.importPage(side)
                    }
                    hoveddokument.pages.forEach { side ->
                        resultat.importPage(side)
                    }
                    val output = ByteArrayOutputStream()
                    resultat.save(output)

                    return PdfA(output.toByteArray())
                }
            }
        }
    }
}
