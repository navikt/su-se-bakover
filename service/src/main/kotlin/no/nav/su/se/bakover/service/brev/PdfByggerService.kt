package no.nav.su.se.bakover.service.brev

import arrow.core.Either
import dokument.domain.KunneIkkeGenererePdf
import dokument.domain.PdfGenerator
import dokument.domain.PensjonPdfByggerKlient
import dokument.domain.generer.PdfInnhold
import no.nav.su.se.bakover.common.domain.PdfA
import no.nav.su.se.bakover.common.infrastructure.config.ApplicationConfig
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Lager PDF med pdf-bygger. Innholdet eier sin egen bestilling ([PdfInnhold.bestilling]),
 * så tjenesten trenger ikke å kjenne malene.
 */
class PdfByggerService(
    private val klient: PensjonPdfByggerKlient,
    private val runtimeEnv: ApplicationConfig.RuntimeEnvironment,
    private val log: Logger = LoggerFactory.getLogger(PdfByggerService::class.java),
) : PdfGenerator {
    override fun genererPdf(pdfInnhold: PdfInnhold): Either<KunneIkkeGenererePdf, PdfA> =
        klient.lagPdf(pdfInnhold.bestilling())
            .map { PdfA(it) }
            .mapLeft {
                log.error("Kunne ikke generere PDF med pdf-bygger for {}.{}", pdfInnhold.dokumentMal.tittel, lokalHjelp())
                KunneIkkeGenererePdf
            }

    private fun lokalHjelp(): String = if (runtimeEnv == ApplicationConfig.RuntimeEnvironment.Local) {
        " Kontroller at pdf-bygger kjører på localhost:8082. Start den med docker compose up -d pdf-bygger."
    } else {
        ""
    }
}
