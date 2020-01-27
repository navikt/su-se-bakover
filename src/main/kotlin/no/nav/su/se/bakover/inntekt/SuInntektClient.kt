package no.nav.su.se.bakover.inntekt

import com.github.kittinunf.fuel.httpPost
import io.ktor.http.ContentType.Application.FormUrlEncoded
import io.ktor.http.ContentType.Application.Json
import io.ktor.http.HttpHeaders.Authorization
import io.ktor.http.HttpHeaders.ContentType
import io.ktor.http.HttpHeaders.XRequestId
import no.nav.su.se.bakover.Feil
import no.nav.su.se.bakover.Ok
import no.nav.su.se.bakover.Result
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.slf4j.MDC

class SuInntektClient(suInntektBaseUrl: String) {
    private val inntektResource = "$suInntektBaseUrl/inntekt"
    private val suInntektIdentLabel = "fnr"

    internal fun inntekt(ident: String, suInntektToken: String, fomDato: String, tomDato: String): Result {
        val (_, _, result) = inntektResource.httpPost(
                listOf(
                        suInntektIdentLabel to ident,
                        "fom" to fomDato.substring(0, 7),
                        "tom" to tomDato.substring(0, 7)
                )
        )
                .header(Authorization, "Bearer $suInntektToken")
                .header(XRequestId, MDC.get(XRequestId))
                .header(ContentType, FormUrlEncoded)
                .responseString()

        return result.fold(
                { Ok(it) },
                {
                    val errorMessage = it.response.body().asString(Json.toString())
                    val statusCode = it.response.statusCode
                    logger.debug("Kall mot Inntektskomponenten feilet, statuskode: $statusCode, feilmelding: $errorMessage");
                    Feil(statusCode, errorMessage)
                }
        )
    }

    companion object {
        val logger: Logger = LoggerFactory.getLogger(SuInntektClient::class.java)
    }
}
