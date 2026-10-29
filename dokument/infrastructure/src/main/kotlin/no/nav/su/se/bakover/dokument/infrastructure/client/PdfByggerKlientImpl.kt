package no.nav.su.se.bakover.dokument.infrastructure.client

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.github.kittinunf.fuel.core.extensions.authentication
import com.github.kittinunf.fuel.httpPost
import dokument.domain.PdfByggerKlientFeil
import dokument.domain.PensjonPdfByggerKlient
import dokument.domain.generer.bygger.PdfBestilling
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import no.nav.su.se.bakover.common.CORRELATION_ID_HEADER
import no.nav.su.se.bakover.common.auth.AzureAd
import no.nav.su.se.bakover.common.infrastructure.correlation.getOrCreateCorrelationIdFromThreadLocal
import no.nav.su.se.bakover.common.jsonNode
import no.nav.su.se.bakover.common.serialize
import org.slf4j.LoggerFactory
import java.time.Duration
import kotlin.jvm.java

class PdfByggerKlientImpl(
    private val baseUrl: String,
    private val azureAd: AzureAd,
    private val clientId: String,
    private val timeout: Duration = Duration.ofSeconds(60),
) : PensjonPdfByggerKlient {
    private val log = LoggerFactory.getLogger(this::class.java)

    override fun lagPdf(bestilling: PdfBestilling): Either<PdfByggerKlientFeil, ByteArray> {
        // TODO ta stilling til om vi ønsker å bruke begge eller bare en
        val (sti, request) = when (bestilling) {
            is PdfBestilling.Brev -> "v2/produserBrev" to serialize(bestilling.request)
            is PdfBestilling.Dokument -> "produserDokument" to serialize(bestilling.request)
        }

        val korrelasjonsId = getOrCreateCorrelationIdFromThreadLocal().toString()
        val (_, response, result) = "$baseUrl/$sti".httpPost()
            .authentication().bearer(azureAd.getSystemToken(clientId))
            .header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            .header(HttpHeaders.Accept, ContentType.Application.Json.toString())
            .header("X-Request-ID", korrelasjonsId)
            .header("Nav-Callid", korrelasjonsId)
            .header(CORRELATION_ID_HEADER, korrelasjonsId)
            .timeout(timeout.toMillis().toInt())
            .body(request).responseString()

        return result.fold(
            {
                jsonNode(it)["bytes"].binaryValue().right()
            },
            {
                val feil = response.body().toString()
                log.error("pdf-bygger svarte ${response.statusCode} på /$sti: $feil")
                PdfByggerKlientFeil.left()
            },
        )
    }
}
