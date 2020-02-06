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

internal class SuInntektClient(
    suInntektBaseUrl: String,
    private val suInntektClientId: String,
    private val exchange: TokenExchange,
    private val persontilgang: Persontilgang
) {
    private val inntektResource = "$suInntektBaseUrl/inntekt"
    private val suInntektIdentLabel = "fnr"

    internal fun inntekt(ident: String, innloggetSaksbehandlerToken: String, fomDato: String, tomDato: String): Result =
        when (val personSvar = persontilgang.person(ident, innloggetSaksbehandlerToken)) {
            is Ok -> finnInntekt(ident, innloggetSaksbehandlerToken, fomDato, tomDato)
            is Feil -> personSvar
        }

    private fun finnInntekt(ident: String, innloggetSaksbehandlerToken: String, fomDato: String, tomDato: String): Result {
        val onBehalfOfToken = exchange.onBehalfOFToken(innloggetSaksbehandlerToken, suInntektClientId)
        val (_, _, result) = inntektResource.httpPost(
            listOf(
                suInntektIdentLabel to ident,
                "fom" to fomDato.substring(0, 7),
                "tom" to tomDato.substring(0, 7)
            )
        )
            .header(Authorization, "Bearer $onBehalfOfToken")
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

internal interface Persontilgang {
    fun person(ident: String, innloggetSaksbehandlerToken: String): Result
}
internal interface TokenExchange {
    fun onBehalfOFToken(originalToken: String, otherAppId: String): String
}