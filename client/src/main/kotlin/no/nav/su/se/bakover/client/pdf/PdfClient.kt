package no.nav.su.se.bakover.client.pdf

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.github.kittinunf.fuel.httpPost
import no.nav.su.se.bakover.domain.brev.PdfTemplate
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.slf4j.MDC

internal const val suPdfGenPath = "/api/v1/genpdf/supdfgen"

internal class PdfClient(private val baseUrl: String) : PdfGenerator {

    private val log: Logger = LoggerFactory.getLogger(this::class.java)

    override fun genererPdf(innholdJson: String, pdfTemplate: PdfTemplate): Either<KunneIkkeGenererePdf, ByteArray> {
        val (_, _, result) = "$baseUrl$suPdfGenPath/$pdfTemplate".httpPost()
            .header("Content-Type", "application/json")
            .header("X-Correlation-ID", MDC.get("X-Correlation-ID"))
            .body(innholdJson).response()
        return result.fold(
            { it.right() },
            {
                log.warn(
                    "Feil ved generering av PDF, status:{}, melding:{}",
                    it.response.statusCode,
                    it.response.responseMessage
                )
                KunneIkkeGenererePdf.left()
            }
        )
    }
}
