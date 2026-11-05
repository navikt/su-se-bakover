package no.nav.su.se.bakover.service.brev

import arrow.core.Either
import arrow.core.left
import arrow.core.raise.either
import arrow.core.right
import dokument.domain.KunneIkkeGenererePdf
import dokument.domain.PdfGenerator
import dokument.domain.PensjonPdfByggerKlient
import dokument.domain.generer.DokumentMal
import dokument.domain.generer.PdfInnhold
import dokument.domain.generer.SammenslåPdf
import no.nav.su.se.bakover.common.domain.PdfA
import no.nav.su.se.bakover.common.infrastructure.config.ApplicationConfig
import java.time.Clock

class PdfByggerService(
    val klient: PensjonPdfByggerKlient,
    val gammelKlient: PdfGenerator,
    val runtimeEnv: ApplicationConfig.RuntimeEnvironment,
    val clock: Clock,
) : PdfGenerator {
    override fun genererPdf(pdfInnhold: PdfInnhold): Either<KunneIkkeGenererePdf, PdfA> {
        val mal = pdfInnhold.dokumentMal

        if (skalBrukeGammelLøsning(mal, runtimeEnv)) {
            return gammelKlient.genererPdf(pdfInnhold)
        }

        val bestilling = mal.lagDokumentInnhold(pdfInnhold) ?: return KunneIkkeGenererePdf.left()
        return klient.lagPdf(bestilling).fold(
            ifLeft = {
                KunneIkkeGenererePdf.left()
            },
            ifRight = {
                PdfA(it).right()
            },
        ).let {
            if (runtimeEnv.erDevEllerLokalt()) {
                either {
                    val nyPdf = it.bind()
                    val gammelPdf = gammelKlient.genererPdf(pdfInnhold).bind()
                    SammenslåPdf.leggTilVedlegg(nyPdf, gammelPdf.getContent()).mapLeft {
                        KunneIkkeGenererePdf
                    }.bind()
                }
            } else {
                it
            }
        }
    }

    private fun skalBrukeGammelLøsning(mal: DokumentMal<*>, runtimeEnv: ApplicationConfig.RuntimeEnvironment) =
        !mal.pdfByggerProd &&
            when (runtimeEnv) {
                ApplicationConfig.RuntimeEnvironment.Prod -> true
                else -> !mal.pdfByggerDev
            }
}
