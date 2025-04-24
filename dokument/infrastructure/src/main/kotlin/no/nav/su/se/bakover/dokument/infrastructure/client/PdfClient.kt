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
        return genererPdf(XmlValidString.create(pdfInnhold.toJson()), pdfInnhold.pdfTemplate.template())
            .mapLeft { KunneIkkeGenererePdf }
    }

    private fun genererPdf(input: XmlValidString, template: String): Either<ClientError, PdfA> {
        val (_, response, result) = "$baseUrl$SU_PDF_GEN_PATH/$template".httpPost()
            .header("Content-Type", "application/json")
            .header(CORRELATION_ID_HEADER, getOrCreateCorrelationIdFromThreadLocal())
            .body(input.value).response()

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

@JvmInline
/**
 * Removing all asci control characters from u0000 to u001F
 * Internal for testing
 */
internal value class XmlValidString private constructor(val value: String) {
    companion object {
        fun create(unvalidated: String): XmlValidString {
            val regexFilterLiteralControlCharacters = Regex("(\\\\u00[0-1][0-9A-F])")
            val regexFilterControlCharacters = Regex("[\\x00-\\x1F]")
            val sanitizedInput = unvalidated
                .replace(regexFilterLiteralControlCharacters, "")
                .replace(regexFilterControlCharacters, "")
            return XmlValidString(sanitizedInput)
        }
    }
}
