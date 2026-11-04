package no.nav.su.se.bakover.service.brev

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import dokument.domain.KunneIkkeGenererePdf
import dokument.domain.PdfGenerator
import dokument.domain.PensjonPdfByggerKlient
import dokument.domain.generer.PdfInnhold
import dokument.domain.generer.bygger.GenerellData
import no.nav.su.se.bakover.common.domain.KjøreMiljø
import no.nav.su.se.bakover.common.domain.PdfA
import java.time.Clock
import javax.jms.IllegalStateException

class PdfByggerService(
    val klient: PensjonPdfByggerKlient,
    val gammelKlient: PdfGenerator,
    val kjøreMiljø: KjøreMiljø = KjøreMiljø.UNITTEST,
    val clock: Clock,
) : PdfGenerator {
    override fun genererPdf(pdfInnhold: PdfInnhold): Either<KunneIkkeGenererePdf, PdfA> {
        val mal = pdfInnhold.dokumentMal
        if (!mal.pdfByggerProd) {
            val brukPdfBygger = when (kjøreMiljø) {
                KjøreMiljø.PROD,
                -> false

                KjøreMiljø.DEV,
                KjøreMiljø.LOCAL,
                KjøreMiljø.UNITTEST,
                -> mal.pdfByggerDev
            }
            if (!brukPdfBygger) {
                return gammelKlient.genererPdf(pdfInnhold)
            }
        }
        val bestilling = if (mal.lagInnhold != null) {
            mal.lagInnhold!!(mal.tittel, pdfInnhold)
        } else {
            val generellData = GenerellData.parse(pdfInnhold.toJson())
            mal.lagInnholdGenerell?.let { it(generellData) }
                ?: throw IllegalStateException("Innhold av mal er ikke implementert")
        }
        return klient.lagPdf(bestilling).fold(
            ifLeft = {
                // TODO utdyp
                KunneIkkeGenererePdf.left()
            },
            ifRight = {
                PdfA(it).right()
            },
        )
    }
}
