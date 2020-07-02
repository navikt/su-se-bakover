package no.nav.su.se.bakover.client.pdf

import com.github.kittinunf.fuel.httpPost
import no.nav.su.meldinger.kafka.soknad.NySøknad
import org.slf4j.Logger
import org.slf4j.LoggerFactory

internal const val suPdfGenPath = "/api/v1/genpdf/supdfgen/soknad"

interface PdfGenerator {
    fun genererPdf(nySøknad: NySøknad): ByteArray
}

internal class PdfClient(private val baseUrl: String) : PdfGenerator {

    val logger: Logger = LoggerFactory.getLogger(this::class.java)

    override fun genererPdf(nySøknad: NySøknad): ByteArray {
        val (_, _, result) = "$baseUrl$suPdfGenPath".httpPost()
            .header("Accept", "application/json")
            .header("X-Correlation-ID", nySøknad.correlationId)
            .body(nySøknad.søknad).response()

        return result.fold(
            { it },
            { error ->
                val statusCode = error.response.statusCode
                val feilmelding = "Kall mot PdfClient feilet, statuskode: $statusCode"
                logger.error(feilmelding, error)
                throw RuntimeException(feilmelding)
            }
        )
    }
}
