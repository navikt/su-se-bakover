package no.nav.su.se.bakover.client.pdf

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.github.kittinunf.fuel.httpPost
import no.nav.su.se.bakover.client.ClientError
import no.nav.su.se.bakover.common.getOrCreateCorrelationId
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.domain.Sakstype
import no.nav.su.se.bakover.domain.brev.BrevInnhold
import no.nav.su.se.bakover.domain.søknad.SøknadPdfInnhold
import org.slf4j.Logger
import org.slf4j.LoggerFactory

internal const val suPdfGenPath = "/api/v1/genpdf/supdfgen"
internal const val SOKNAD_TEMPLATE = "soknad"
internal const val ALDER_MISSING_TEMPLATE = "alderHarIkkeBrevEnda"

internal class PdfClient(private val baseUrl: String) : PdfGenerator {

    private val log: Logger = LoggerFactory.getLogger(this::class.java)

    override fun genererPdf(søknadPdfInnhold: SøknadPdfInnhold): Either<ClientError, ByteArray> {
        return genererPdf(objectMapper.writeValueAsString(søknadPdfInnhold), SOKNAD_TEMPLATE)
    }

    override fun genererPdf(brevInnhold: BrevInnhold): Either<KunneIkkeGenererePdf, ByteArray> {
        return genererPdf(brevInnhold.toJson(), brevInnhold.brevTemplate.template(), brevInnhold.sakstype)
            .mapLeft { KunneIkkeGenererePdf }
    }

    // TODO øh: Håndter alderbrev på en annen måte når det er på plass
    private fun genererPdf(input: String, template: String, brevtype: Sakstype): Either<ClientError, ByteArray> {
        if (brevtype == Sakstype.UFØRE) {
            return genererPdf(input, template)
        }
        // Manuelt overstyrer alle aldersbrev til denne
        return genererPdf(input, ALDER_MISSING_TEMPLATE)
    }

    private fun genererPdf(input: String, template: String): Either<ClientError, ByteArray> {
        val (_, response, result) = "$baseUrl$suPdfGenPath/$template".httpPost()
            .header("Content-Type", "application/json")
            .header("X-Correlation-ID", getOrCreateCorrelationId())
            .body(input).response()

        return result.fold(
            {
                it.right()
            },
            {
                log.error("Kall mot PdfClient feilet", it)
                ClientError(response.statusCode, "Kall mot PdfClient feilet").left()
            }
        )
    }
}
