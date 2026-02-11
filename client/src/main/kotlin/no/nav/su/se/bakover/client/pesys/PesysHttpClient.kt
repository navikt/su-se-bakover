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
    fun hentVedtakForPersonPaaDatoAlder(fnrList: List<Fnr>, dato: LocalDate): Either<ClientError, ResponseDtoAlder>
    fun hentVedtakForPersonPaaDatoUføre(fnrList: List<Fnr>, dato: LocalDate): Either<ClientError, ResponseDtoUføre>
}

class PesysclientStub : PesysClient {
    override fun hentVedtakForPersonPaaDatoAlder(
        fnrList: List<Fnr>,
        dato: LocalDate,
    ): Either<ClientError, ResponseDtoAlder> {
        return ResponseDtoAlder(emptyList()).right()
    }

    override fun hentVedtakForPersonPaaDatoUføre(
        fnrList: List<Fnr>,
        dato: LocalDate,
    ): Either<ClientError, ResponseDtoUføre> {
        return ResponseDtoUføre(emptyList()).right()
    }
}

class PesysHttpClient(
    private val azureAd: AzureAd,
    private val url: String,
    private val clientId: String,
) : PesysClient {
    private val log = LoggerFactory.getLogger(this::class.java)

    val alderUri = "alderspensjon/vedtak/iverksatt" // + eks:  ?fom=2024-12-15"
    val uforeUri = "api/uforetrygd/ekstern/supplerede-stonad/beregningsperioder"
    override fun hentVedtakForPersonPaaDatoAlder(fnrList: List<Fnr>, dato: LocalDate): Either<ClientError, ResponseDtoAlder> {
        val correlationId = getOrCreateCorrelationIdFromThreadLocal()

        val fullUrl = "$url$alderUri"
        val (request, response, result) =
            fullUrl
                .httpPost(listOf("fom" to dato.toString()))
                .authentication().bearer(azureAd.getSystemToken(clientId))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header(CORRELATION_ID_HEADER, correlationId)
                .body(serialize(fnrList.map(Fnr::toString)))
                .responseString()

        return result.fold(
            { json ->
                try {
                    deserialize<ResponseDtoAlder>(json).right()
                } catch (e: Exception) {
                    log.error("Alder: Deserialization failed", e)
                    sikkerLogg.error("Alder: Deserialization failed $json", e)
                    ClientError(HttpStatusCode.InternalServerError.value, "Klarte ikke å deserialise objekt, se sikkerlogg").left()
                }
            },
            { error ->
                log.error("Alder: HTTP error from Pesys", error)
                val body = response.body().toByteArray().toString(Charsets.UTF_8)
                ClientError(
                    httpStatus = error.response.statusCode,
                    message = body.ifBlank {
                        error.message ?: "Ukjent feil ved henting av vedtak fra Pesys"
                    },
                ).left()
            },
        )
    }

    override fun hentVedtakForPersonPaaDatoUføre(
        fnrList: List<Fnr>,
        dato: LocalDate,
    ): Either<ClientError, ResponseDtoUføre> {
        val correlationId = getOrCreateCorrelationIdFromThreadLocal()

        val fullUrl = "$url$uforeUri"
        val (request, response, result) =
            fullUrl
                .httpPost(listOf("fom" to dato.toString()))
                .authentication().bearer(azureAd.getSystemToken(clientId))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header(CORRELATION_ID_HEADER, correlationId)
                .body(serialize(fnrList.map(Fnr::toString)))
                .responseString()

        return result.fold(
            { json ->
                try {
                    deserialize<ResponseDtoUføre>(json).right()
                } catch (e: Exception) {
                    log.error("uføre: Deserialization failed", e)
                    sikkerLogg.error("uføre: Deserialization failed $json", e)
                    ClientError(HttpStatusCode.InternalServerError.value, "Klarte ikke å deserialise objekt, se sikkerlogg").left()
                }
            },
            { error ->
                log.error("HTTP error from Pesys uføre", error)
                val body = response.body().toByteArray().toString(Charsets.UTF_8)
                ClientError(
                    httpStatus = error.response.statusCode,
                    message = body.ifBlank {
                        error.message ?: "Ukjent feil ved henting av vedtak fra Pesys"
                    },
                ).left()
            },
        )
    }
}

data class ResponseDtoAlder(val resultat: List<BeregningsperioderIverksatteVedtakDto>)
data class BeregningsperioderIverksatteVedtakDto(val fnr: String, val perioder: List<BeregningsperiodeDto>)
data class BeregningsperiodeDto(val netto: Int, val fom: LocalDate, val tom: LocalDate?, val grunnbelop: Int)

data class ResponseDtoUføre(
    val resultat: List<UføreBeregningsperioderPerPerson>,
)

data class UføreBeregningsperioderPerPerson(
    val fnr: String,
    val perioder: List<UføreBeregningsperiode>,
)

data class UføreBeregningsperiode(
    val netto: Int,
    val fom: LocalDate,
    val tom: LocalDate? = null,
    val grunnbelop: Int,
    val oppjustertInntektEtterUfore: Int?,
)
