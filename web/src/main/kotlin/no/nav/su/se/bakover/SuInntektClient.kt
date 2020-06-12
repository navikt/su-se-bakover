package no.nav.su.se.bakover

import com.github.kittinunf.fuel.httpPost
import io.ktor.http.ContentType.Application.FormUrlEncoded
import io.ktor.http.ContentType.Application.Json
import io.ktor.http.HttpHeaders.Authorization
import io.ktor.http.HttpHeaders.ContentType
import io.ktor.http.HttpHeaders.XCorrelationId
import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpStatusCode.Companion.OK
import org.slf4j.Logger
import org.slf4j.LoggerFactory

internal interface InntektOppslag {
    fun inntekt(ident: Fødselsnummer, innloggetSaksbehandlerToken: String, fomDato: String, tomDato: String): Resultat
}

internal class SuInntektClient(
    suInntektBaseUrl: String,
    private val suInntektClientId: String,
    private val exchange: OAuth,
    private val personOppslag: PersonOppslag
) : InntektOppslag {
    private val inntektResource = "$suInntektBaseUrl/inntekt"
    private val suInntektIdentLabel = "fnr"

    //TODO bedre håndtering av kode 6/7?
    override fun inntekt(ident: Fødselsnummer, innloggetSaksbehandlerToken: String, fomDato: String, tomDato: String): Resultat =
            personOppslag.person(ident).fold(
                    success = { finnInntekt(ident, innloggetSaksbehandlerToken, fomDato, tomDato) },
                    error = { it }
            )

    private fun finnInntekt(ident: Fødselsnummer, innloggetSaksbehandlerToken: String, fomDato: String, tomDato: String): Resultat {
        val onBehalfOfToken = exchange.onBehalfOFToken(innloggetSaksbehandlerToken, suInntektClientId)
        val (_, _, result) = inntektResource.httpPost(
                        listOf(
                                suInntektIdentLabel to ident.toString(),
                                "fom" to fomDato.daymonthSubstring(),
                                "tom" to tomDato.daymonthSubstring()
                        )
                )
                .header(Authorization, "Bearer $onBehalfOfToken")
                .header(XCorrelationId, ContextHolder.correlationId())
                .header(ContentType, FormUrlEncoded)
                .responseString()

        return result.fold(
                { OK.json(it) },
                { error ->
                    val errorMessage = error.response.body().asString(Json.toString())
                    val statusCode = error.response.statusCode
                    logger.debug("Kall mot Inntektskomponenten feilet, statuskode: $statusCode, feilmelding: $errorMessage")
                    (HttpStatusCode.fromValue(statusCode).tekst(errorMessage))
                }
        )
    }

    companion object {
        val logger: Logger = LoggerFactory.getLogger(SuInntektClient::class.java)
    }
}

private fun String.daymonthSubstring() = substring(0, 7)

