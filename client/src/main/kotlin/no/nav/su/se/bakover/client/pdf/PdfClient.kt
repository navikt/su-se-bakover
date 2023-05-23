package no.nav.su.se.bakover.client.pdf

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.github.kittinunf.fuel.httpPost
import no.nav.su.se.bakover.client.ClientError
import no.nav.su.se.bakover.common.CorrelationIdHeader
import no.nav.su.se.bakover.common.infrastructure.correlation.getOrCreateCorrelationIdFromThreadLocal
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.domain.brev.PdfInnhold
import no.nav.su.se.bakover.domain.søknad.SøknadPdfInnhold
import org.slf4j.Logger
import org.slf4j.LoggerFactory

internal const val suPdfGenPath = "/api/v1/genpdf/supdfgen"
internal const val SOKNAD_TEMPLATE = "soknad"

internal class PdfClient(private val baseUrl: String) : PdfGenerator {

    private val log: Logger = LoggerFactory.getLogger(this::class.java)

    override fun genererPdf(søknadPdfInnhold: SøknadPdfInnhold): Either<ClientError, ByteArray> {
        return genererPdf(objectMapper.writeValueAsString(søknadPdfInnhold), SOKNAD_TEMPLATE)
    }

    override fun genererPdf(pdfInnhold: PdfInnhold): Either<KunneIkkeGenererePdf, ByteArray> {
        return genererPdf(pdfInnhold.toJson(), pdfInnhold.brevTemplate.template())
            .mapLeft { KunneIkkeGenererePdf }
    }

    private fun genererPdf(input: String, template: String): Either<ClientError, ByteArray> {
        val (_, response, result) = "$baseUrl$suPdfGenPath/$template".httpPost()
            .header("Content-Type", "application/json")
            .header(CorrelationIdHeader, getOrCreateCorrelationIdFromThreadLocal())
            .body(input).response()

        return result.fold(
            {
                it.right()
            },
            {
                log.error("Kall mot PdfClient feilet", it)
                ClientError(response.statusCode, "Kall mot PdfClient feilet").left()
            },
        )
    }
}
