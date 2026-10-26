package no.nav.su.se.bakover.dokument.infrastructure.client

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.github.kittinunf.fuel.core.FuelError
import com.github.kittinunf.fuel.httpPost
import dokument.domain.KunneIkkeGenererePdf
import dokument.domain.PdfGenerator
import dokument.domain.pdf.PdfInnhold
import no.nav.su.se.bakover.common.CORRELATION_ID_HEADER
import no.nav.su.se.bakover.common.domain.PdfA
import no.nav.su.se.bakover.common.infrastructure.correlation.getOrCreateCorrelationIdFromThreadLocal
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.EOFException
import java.net.NoRouteToHostException
import java.net.SocketException
import java.net.SocketTimeoutException

internal const val SU_PDF_GEN_PATH = "/api/v1/genpdf/supdfgen"

class PdfClient(private val baseUrl: String) : PdfGenerator {

    private val log: Logger = LoggerFactory.getLogger(this::class.java)
    private val retryIntervallerMs = listOf(400L, 800L, 1600L)

    override fun genererPdf(pdfInnhold: PdfInnhold): Either<KunneIkkeGenererePdf, PdfA> {
        val input = XmlValidString.create(pdfInnhold.toJson())
        val template = pdfInnhold.pdfTemplate.template()
        var antallRetries = 0

        while (true) {
            when (val resultat = genererPdf(input, template)) {
                is Either.Right -> return resultat.value.right()
                is Either.Left -> {
                    val feil = resultat.value
                    if (antallRetries >= retryIntervallerMs.size || !feil.erForbigåendePdfTransportfeil()) {
                        log.error("Kall mot PdfClient feilet", feil)
                        return KunneIkkeGenererePdf.left()
                    }

                    val ventetidMs = retryIntervallerMs[antallRetries]
                    log.warn(
                        "Kall mot PdfClient feilet med transportfeil. Nytt forsøk {} av {} om {} ms.",
                        antallRetries + 1,
                        retryIntervallerMs.size,
                        ventetidMs,
                    )
                    Thread.sleep(ventetidMs)
                    antallRetries++
                }
            }
        }
    }

    private fun genererPdf(input: XmlValidString, template: String): Either<FuelError, PdfA> {
        val (_, _, result) = "$baseUrl$SU_PDF_GEN_PATH/$template".httpPost()
            .header("Content-Type", "application/json")
            .header(CORRELATION_ID_HEADER, getOrCreateCorrelationIdFromThreadLocal())
            .body(input.value).response()

        return result.fold(
            {
                PdfA(it).right()
            },
            {
                it.left()
            },
        )
    }
}

internal fun Throwable.erForbigåendePdfTransportfeil(): Boolean {
    val årsaker = generateSequence(this) { it.cause }.toList()
    return årsaker.none { it is NoRouteToHostException } &&
        årsaker.any { it is SocketTimeoutException || it is SocketException || it is EOFException }
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
