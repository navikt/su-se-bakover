package no.nav.su.se.bakover.client.aap

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.github.kittinunf.fuel.core.extensions.authentication
import com.github.kittinunf.fuel.httpPost
import io.ktor.http.HttpStatusCode
import no.nav.su.se.bakover.common.auth.AzureAd
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.domain.client.ClientError
import no.nav.su.se.bakover.common.infrastructure.correlation.getOrCreateCorrelationIdFromThreadLocal
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.common.sikkerLogg
import org.slf4j.LoggerFactory
import java.time.LocalDate

interface AapApiInternClient {
    fun hentMaksimum(
        fnr: Fnr,
        fraOgMedDato: LocalDate,
        tilOgMedDato: LocalDate,
    ): Either<ClientError, MaksimumResponseDto>
}

class AapApiInternClientStub : AapApiInternClient {
    override fun hentMaksimum(
        fnr: Fnr,
        fraOgMedDato: LocalDate,
        tilOgMedDato: LocalDate,
    ): Either<ClientError, MaksimumResponseDto> {
        return MaksimumResponseDto(vedtak = emptyList()).right()
    }
}

class AapApiInternHttpClient(
    private val azureAd: AzureAd,
    private val url: String,
    private val clientId: String,
) : AapApiInternClient {
    private val log = LoggerFactory.getLogger(this::class.java)
    private val baseUrl = if (url.endsWith("/")) url else "$url/"
    private val maksimumUri = "maksimum"

    override fun hentMaksimum(
        fnr: Fnr,
        fraOgMedDato: LocalDate,
        tilOgMedDato: LocalDate,
    ): Either<ClientError, MaksimumResponseDto> {
        val callId = getOrCreateCorrelationIdFromThreadLocal().toString()

        val (_, response, result) = "$baseUrl$maksimumUri"
            .httpPost()
            .authentication().bearer(azureAd.getSystemToken(clientId))
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .header("nav-callid", callId)
            .header("x-correlation-id", callId)
            .body(
                serialize(
                    MaksimumRequestDto(
                        fraOgMedDato = fraOgMedDato,
                        personidentifikator = fnr.toString(),
                        tilOgMedDato = tilOgMedDato,
                    ),
                ),
            )
            .responseString()

        return result.fold(
            { json ->
                try {
                    deserialize<MaksimumResponseDto>(json).right()
                } catch (e: Exception) {
                    log.error("Deserialization failed for AAP maksimum", e)
                    sikkerLogg.error("Deserialization failed for AAP maksimum: $json", e)
                    ClientError(
                        HttpStatusCode.InternalServerError.value,
                        "Klarte ikke å deserialisere respons fra AAP-api-intern, se sikkerlogg",
                    ).left()
                }
            },
            { error ->
                log.error("HTTP error from AAP-api-intern", error)
                val body = response.body().toByteArray().toString(Charsets.UTF_8)
                ClientError(
                    httpStatus = error.response.statusCode,
                    message = body.ifBlank {
                        error.message ?: "Ukjent feil ved henting av maksimum fra AAP-api-intern"
                    },
                ).left()
            },
        )
    }
}

data class MaksimumRequestDto(
    val fraOgMedDato: LocalDate,
    val personidentifikator: String,
    val tilOgMedDato: LocalDate,
)

data class MaksimumResponseDto(
    val vedtak: List<MaksimumVedtakDto>,
)

data class MaksimumVedtakDto(
    val vedtakId: String? = null,
    val kildesystem: String? = null,
)
