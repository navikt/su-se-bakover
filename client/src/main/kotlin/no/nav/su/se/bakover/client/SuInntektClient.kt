package no.nav.su.se.bakover.client

import com.github.kittinunf.fuel.httpPost
import no.nav.su.se.bakover.common.CallContext
import no.nav.su.se.bakover.domain.Fnr
import org.slf4j.Logger
import org.slf4j.LoggerFactory

interface InntektOppslag {
    fun inntekt(ident: Fnr, innloggetSaksbehandlerToken: String, fomDato: String, tomDato: String): ClientResponse
}

internal class SuInntektClient(
    suInntektBaseUrl: String,
    private val suInntektClientId: String,
    private val exchange: OAuth,
    private val personOppslag: PersonOppslag
) : InntektOppslag {
    private val inntektResource = "$suInntektBaseUrl/inntekt"
    private val suInntektIdentLabel = "fnr"

    // TODO bedre håndtering av kode 6/7?
    override fun inntekt(ident: Fnr, innloggetSaksbehandlerToken: String, fomDato: String, tomDato: String): ClientResponse {
        val oppslag = personOppslag.person(ident)
        return when (oppslag.success()) {
            true -> finnInntekt(ident, innloggetSaksbehandlerToken, fomDato, tomDato)
            else -> oppslag
        }
    }

    private fun finnInntekt(ident: Fnr, innloggetSaksbehandlerToken: String, fomDato: String, tomDato: String): ClientResponse {
        val onBehalfOfToken = exchange.onBehalfOFToken(innloggetSaksbehandlerToken, suInntektClientId)
        val (_, response, result) = inntektResource.httpPost(
                listOf(
                        suInntektIdentLabel to ident.toString(),
                        "fom" to fomDato.yearMonthSubstring(),
                        "tom" to tomDato.yearMonthSubstring()
                )
        )
                .header("Authorization", "Bearer $onBehalfOfToken")
                .header("X-Correlation-ID", CallContext.correlationId())
                .header("Content-Type", "application/x-www-form-urlencoded")
                .responseString()

        return result.fold(
                { ClientResponse(response.statusCode, it) },
                { error ->
                    val errorMessage = error.response.body().asString("application/json")
                    val statusCode = error.response.statusCode
                    logger.debug("Kall mot Inntektskomponenten feilet, statuskode: $statusCode, feilmelding: $errorMessage")
                    ClientResponse(response.statusCode, errorMessage)
                }
        )
    }

    companion object {
        val logger: Logger = LoggerFactory.getLogger(SuInntektClient::class.java)
    }
}

private fun String.yearMonthSubstring() = substring(0, 7)
