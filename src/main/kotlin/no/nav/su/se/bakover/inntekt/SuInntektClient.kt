package no.nav.su.se.bakover.inntekt

import com.github.kittinunf.fuel.httpPost
import io.ktor.http.ContentType.Application.FormUrlEncoded
import io.ktor.http.ContentType.Application.Json
import io.ktor.http.HttpHeaders.Authorization
import io.ktor.http.HttpHeaders.ContentType
import io.ktor.http.HttpHeaders.XRequestId
import no.nav.su.se.bakover.Feil
import no.nav.su.se.bakover.Ok
import no.nav.su.se.bakover.Resultat
import no.nav.su.se.bakover.azure.TokenExchange
import no.nav.su.se.bakover.person.PersonOppslag
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.slf4j.MDC

internal interface InntektOppslag {
    fun inntekt(ident: String, innloggetSaksbehandlerToken: String, fomDato: String, tomDato: String): Resultat
}

internal class SuInntektClient(
    suInntektBaseUrl: String,
    private val suInntektClientId: String,
    private val exchange: TokenExchange,
    private val personOppslag: PersonOppslag
): InntektOppslag {
    private val inntektResource = "$suInntektBaseUrl/inntekt"
    private val suInntektIdentLabel = "fnr"

    override fun inntekt(ident: String, innloggetSaksbehandlerToken: String, fomDato: String, tomDato: String): Resultat =
        when (val personSvar = personOppslag.person(ident, innloggetSaksbehandlerToken)) {
            is Ok -> finnInntekt(ident, innloggetSaksbehandlerToken, fomDato, tomDato)
            is Feil -> personSvar
        }

    private fun finnInntekt(ident: String, innloggetSaksbehandlerToken: String, fomDato: String, tomDato: String): Resultat {
        val onBehalfOfToken = exchange.onBehalfOFToken(innloggetSaksbehandlerToken, suInntektClientId)
        val (_, _, result) = inntektResource.httpPost(
            listOf(
                suInntektIdentLabel to ident,
                "fom" to fomDato.daymonthSubstring(),
                "tom" to tomDato.daymonthSubstring()
            )
        )
            .header(Authorization, "Bearer $onBehalfOfToken")
            .header(XRequestId, MDC.get(XRequestId))
            .header(ContentType, FormUrlEncoded)
            .responseString()

        return result.fold(
            { Ok(it) },
            { error ->
                val errorMessage = error.response.body().asString(Json.toString())
                val statusCode = error.response.statusCode
                logger.debug("Kall mot Inntektskomponenten feilet, statuskode: $statusCode, feilmelding: $errorMessage");
                Feil(statusCode, errorMessage)
            }
        )
    }

    companion object {
        val logger: Logger = LoggerFactory.getLogger(SuInntektClient::class.java)
    }
}

private fun String.daymonthSubstring() = substring(0, 7)

