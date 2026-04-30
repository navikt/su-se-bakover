package no.nav.su.se.bakover.client.pesys

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.github.kittinunf.fuel.core.extensions.authentication
import com.github.kittinunf.fuel.httpPost
import io.ktor.http.HttpStatusCode
import no.nav.su.se.bakover.common.auth.AzureAd
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.domain.client.ClientError
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.common.sikkerLogg
import org.slf4j.LoggerFactory
import java.time.LocalDate

interface PesysClient {
    fun hentVedtakForPersonPaaDatoAlder(fnrList: List<Fnr>, dato: LocalDate): Either<ClientError, ResponseDtoAlder>
    fun hentVedtakForPersonPaaDatoUføre(fnrList: List<Fnr>, dato: LocalDate): Either<ClientError, ResponseDtoUføre>
}

class PesysHttpClient(
    private val azureAd: AzureAd,
    private val url: String,
    private val clientId: String,
) : PesysClient {
    private val log = LoggerFactory.getLogger(this::class.java)
    private val baseUrl = if (url.endsWith("/")) url else "$url/"

    val alderUri = "alderspensjon/vedtak/iverksatt" // + eks:  ?fom=2024-12-15"
    val uforeUri = "uforetrygd/ekstern/supplerede-stonad/beregningsperioder"

    // TODO: tester for at feilede returneres mens håndteres et steg opp
    override fun hentVedtakForPersonPaaDatoAlder(fnrList: List<Fnr>, dato: LocalDate): Either<ClientError, ResponseDtoAlder> {
        if (fnrList.isEmpty()) {
            return ResponseDtoAlder(emptyList(), emptyList()).right()
        }

        val fullUrl = "$baseUrl$alderUri"
        val (_, response, result) =
            fullUrl
                .httpPost(listOf("fom" to dato.toString()))
                .authentication().bearer(azureAd.getSystemToken(clientId))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
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
        if (fnrList.isEmpty()) {
            return ResponseDtoUføre(emptyList(), emptyList()).right()
        }

        val fullUrl = "$baseUrl$uforeUri"
        val (_, response, result) =
            fullUrl
                .httpPost(listOf("fom" to dato.toString()))
                .authentication().bearer(azureAd.getSystemToken(clientId))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
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

interface PesysPerioderForPerson {
    val fnr: String
    val perioder: List<PesysPeriode>
}

interface PesysPeriode {
    val netto: Int
    val fom: LocalDate
    val tom: LocalDate?
    val grunnbelop: Int
}

data class ResponseDtoAlder(
    val resultat: List<AlderBeregningsperioderPerPerson>,
    val feilendeFnr: List<String>,
)

data class AlderBeregningsperioderPerPerson(
    override val fnr: String,
    override val perioder: List<AlderBeregningsperiode>,
) : PesysPerioderForPerson

data class AlderBeregningsperiode(
    override val netto: Int,
    override val fom: LocalDate,
    override val tom: LocalDate?,
    override val grunnbelop: Int,
) : PesysPeriode

data class ResponseDtoUføre(
    val resultat: List<UføreBeregningsperioderPerPerson>,
    val feilendeFnr: List<String>,
)

data class UføreBeregningsperioderPerPerson(
    override val fnr: String,
    override val perioder: List<UføreBeregningsperiode>,
) : PesysPerioderForPerson

data class UføreBeregningsperiode(
    override val netto: Int,
    override val fom: LocalDate,
    override val tom: LocalDate? = null,
    override val grunnbelop: Int,
    val oppjustertInntektEtterUfore: Int?,
) : PesysPeriode
