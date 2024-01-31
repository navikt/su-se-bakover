package no.nav.su.se.bakover.dokument.infrastructure.client

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.github.kittinunf.fuel.httpPost
import dokument.domain.pdf.PdfInnhold
import no.nav.su.se.bakover.common.CORRELATION_ID_HEADER
import no.nav.su.se.bakover.common.domain.PdfA
import no.nav.su.se.bakover.common.domain.client.ClientError
import no.nav.su.se.bakover.common.infrastructure.correlation.getOrCreateCorrelationIdFromThreadLocal
import org.slf4j.Logger
import org.slf4j.LoggerFactory

internal const val SU_PDF_GEN_PATH = "/api/v1/genpdf/supdfgen"

class PdfClient(private val baseUrl: String) : PdfGenerator {

    private val log: Logger = LoggerFactory.getLogger(this::class.java)

    override fun genererPdf(pdfInnhold: PdfInnhold): Either<KunneIkkeGenererePdf, PdfA> {
        return genererPdf(pdfInnhold.toJson(), pdfInnhold.pdfTemplate.template())
            .mapLeft { KunneIkkeGenererePdf }
    }

    private fun genererPdf(input: String, template: String): Either<ClientError, PdfA> {
        val (_, response, result) = "$baseUrl$SU_PDF_GEN_PATH/$template".httpPost()
            .header("Content-Type", "application/json")
            .header(CORRELATION_ID_HEADER, getOrCreateCorrelationIdFromThreadLocal())
            .body(input).response()

        return result.fold(
            {
                PdfA(it).right()
            },
            {
                log.error("Kall mot PdfClient feilet", it)
                ClientError(response.statusCode, "Kall mot PdfClient feilet").left()
            },
        )
    }
}
