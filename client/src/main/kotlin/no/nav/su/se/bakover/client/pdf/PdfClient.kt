package no.nav.su.se.bakover.client.pdf

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.github.kittinunf.fuel.httpPost
import no.nav.su.meldinger.kafka.soknad.SøknadInnhold
import no.nav.su.se.bakover.client.ClientError
import no.nav.su.se.bakover.common.CallContext
import org.slf4j.Logger
import org.slf4j.LoggerFactory

internal const val suPdfGenPath = "/api/v1/genpdf/supdfgen/soknad"

internal class PdfClient(private val baseUrl: String) : PdfGenerator {

    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    override fun genererPdf(søknad: SøknadInnhold): Either<ClientError, ByteArray> {
        val (_, response, result) = "$baseUrl$suPdfGenPath".httpPost()
            .header("Content-Type", "application/json")
            .header("X-Correlation-ID", CallContext.correlationId())
            .body(søknad.toJson()).response()

        return result.fold(
            { it.right() },
            {
                logger.warn("Kall mot PdfClient feilet", it)
                ClientError(response.statusCode, "Kall mot PdfClient feilet").left()
            }
        )
    }
}
