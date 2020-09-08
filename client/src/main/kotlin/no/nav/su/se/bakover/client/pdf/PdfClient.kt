package no.nav.su.se.bakover.client.pdf

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.github.kittinunf.fuel.httpPost
import no.nav.su.se.bakover.client.ClientError
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.domain.SøknadInnhold
import no.nav.su.se.bakover.domain.VedtakInnhold
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.slf4j.MDC

internal const val suPdfGenPath = "/api/v1/genpdf/supdfgen"
internal const val SOKNAD_TEMPLATE = "soknad"

internal class PdfClient(private val baseUrl: String) : PdfGenerator {

    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    override fun genererPdf(søknad: SøknadInnhold): Either<ClientError, ByteArray> {
        return genererPdf(objectMapper.writeValueAsString(søknad), SOKNAD_TEMPLATE)
    }

    override fun genererPdf(vedtak: VedtakInnhold, vedtakstype: Vedtakstype): Either<ClientError, ByteArray> {
        return genererPdf(objectMapper.writeValueAsString(vedtak), vedtakstype.template)
    }

    private fun genererPdf(input: String, template: String): Either<ClientError, ByteArray> {
        val (_, response, result) = "$baseUrl$suPdfGenPath/$template".httpPost()
            .header("Content-Type", "application/json")
            .header("X-Correlation-ID", MDC.get("X-Correlation-ID"))
            .body(input).response()

        return result.fold(
            {
                it.right()
            },
            {
                logger.warn("Kall mot PdfClient feilet", it)
                ClientError(response.statusCode, "Kall mot PdfClient feilet").left()
            }
        )
    }
}
