package no.nav.su.se.bakover.service.brev

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import dokument.domain.KunneIkkeGenererePdf
import dokument.domain.PdfGenerator
import dokument.domain.PensjonPdfByggerKlient
import dokument.domain.pdf.PdfInnhold
import dokument.domain.pdf.bygger.PdfData
import no.nav.su.se.bakover.common.domain.PdfA
import no.nav.su.se.bakover.common.infrastructure.config.ApplicationConfig
import java.time.Clock

class PdfByggerService(
    val klient: PensjonPdfByggerKlient,
    val gammelKlient: PdfGenerator,
    val naisCluster: ApplicationConfig.NaisCluster? = null,
    val clock: Clock,
) : PdfGenerator {
    override fun genererPdf(pdfInnhold: PdfInnhold): Either<KunneIkkeGenererePdf, PdfA> {
        val mal = pdfInnhold.pdfTemplate.pdfTemplate
        if (!mal.pdfByggerProd) {
            when (naisCluster) {
                ApplicationConfig.NaisCluster.Prod -> if (!mal.pdfByggerProd) {
                    return gammelKlient.genererPdf(pdfInnhold)
                }

                ApplicationConfig.NaisCluster.Dev,
                null,
                -> if (!mal.pdfByggerDev) {
                    return gammelKlient.genererPdf(pdfInnhold)
                }
            }
        }
        val pdfData = PdfData.parse(pdfInnhold.toJson())
        return klient.lagPdf(mal.malInnhold(pdfData)).fold(
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
