package no.nav.su.se.bakover.client.pesys

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.github.kittinunf.fuel.core.extensions.authentication
import com.github.kittinunf.fuel.httpPost
import io.ktor.http.HttpStatusCode
import no.nav.su.se.bakover.common.CORRELATION_ID_HEADER
import no.nav.su.se.bakover.common.auth.AzureAd
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.domain.client.ClientError
import no.nav.su.se.bakover.common.infrastructure.correlation.getOrCreateCorrelationIdFromThreadLocal
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.common.sikkerLogg
import org.slf4j.LoggerFactory
import java.time.LocalDate

interface PesysClient {
    fun hentVedtakForPersonPaaDatoAlder(fnrList: List<Fnr>, dato: LocalDate): Either<ClientError, ResponseDto>
}

class PesysclientStub : PesysClient {
    override fun hentVedtakForPersonPaaDatoAlder(
        fnrList: List<Fnr>,
        dato: LocalDate,
    ): Either<ClientError, ResponseDto> {
        TODO("Not yet implemented")
    }
}

class PesysHttpClient(
    private val azureAd: AzureAd,
    private val url: String,
    private val clientId: String,
) : PesysClient {
    private val log = LoggerFactory.getLogger(this::class.java)

    val alderUri = "alderspensjon/vedtak/iverksatt" // + eks:  ?fom=2024-12-15"
    override fun hentVedtakForPersonPaaDatoAlder(fnrList: List<Fnr>, dato: LocalDate): Either<ClientError, ResponseDto> {
        val correlationId = getOrCreateCorrelationIdFromThreadLocal()

        val (request, response, result) = "${url}$alderUri".httpPost(listOf(Pair("fom", dato)))
            .authentication().bearer(azureAd.getSystemToken(clientId))
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .header(CORRELATION_ID_HEADER, correlationId)
            .body(serialize(fnrList.map(Fnr::toString))).responseString()

        return result.fold(
            { json ->
                log.info("json: $json")
                try {
                    deserialize<ResponseDto>(json).right()
                } catch (e: Exception) {
                    log.error("Deserialization failed", e)
                    sikkerLogg.error("Deserialization failed $json", e)
                    ClientError(HttpStatusCode.InternalServerError.value, "Klarte ikke å deserialise objekt, se sikkerlogg").left()
                }
            },
            { error ->
                log.error("HTTP error from Pesys", error)
                ClientError(
                    httpStatus = error.response.statusCode,
                    message = error.message ?: "Ukjent feil ved henting av vedtak fra Pesys",
                ).left()
            },
        )
    }
}

data class ResponseDto(val resultat: List<BeregningsperioderIverksatteVedtakDto>)
data class BeregningsperioderIverksatteVedtakDto(val fnr: String, val perioder: List<BeregningsperiodeDto>)
data class BeregningsperiodeDto(val netto: Int, val fom: LocalDate, val tom: LocalDate?, val grunnbelop: Int)
