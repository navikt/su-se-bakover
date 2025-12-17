package no.nav.su.se.bakover.client.oppdrag.simulering

import arrow.core.Either
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.github.kittinunf.fuel.core.extensions.authentication
import com.github.kittinunf.fuel.httpPost
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import no.nav.su.se.bakover.common.auth.AzureAd
import no.nav.su.se.bakover.common.infrastructure.config.ApplicationConfig.ClientsConfig.SuProxyConfig
import no.nav.su.se.bakover.common.infrastructure.correlation.getOrCreateCorrelationIdFromThreadLocal
import no.nav.su.se.bakover.common.infrastructure.token.JwtToken
import org.slf4j.LoggerFactory
import økonomi.domain.simulering.Simulering
import økonomi.domain.simulering.SimuleringClient
import økonomi.domain.simulering.SimuleringFeilet
import økonomi.domain.utbetaling.Utbetaling
import java.time.Clock

private fun mapError(code: SimuleringErrorCode): SimuleringFeilet =
    when (code) {
        SimuleringErrorCode.UTENFOR_APNINGSTID ->
            SimuleringFeilet.UtenforÅpningstid

        SimuleringErrorCode.PERSON_FINNES_IKKE_I_TPS ->
            SimuleringFeilet.PersonFinnesIkkeITPS

        SimuleringErrorCode.FINNER_IKKE_KJOREPLAN ->
            SimuleringFeilet.FinnerIkkeKjøreplanForFraOgMed

        SimuleringErrorCode.OPPDRAG_EKSISTERER_IKKE ->
            SimuleringFeilet.OppdragEksistererIkke

        SimuleringErrorCode.FUNKSJONELL_FEIL ->
            SimuleringFeilet.FunksjonellFeil

        SimuleringErrorCode.TEKNISK_FEIL ->
            SimuleringFeilet.TekniskFeil
    }
enum class SimuleringErrorCode {
    UTENFOR_APNINGSTID,
    PERSON_FINNES_IKKE_I_TPS,
    FINNER_IKKE_KJOREPLAN,
    OPPDRAG_EKSISTERER_IKKE,
    FUNKSJONELL_FEIL,
    TEKNISK_FEIL,
}
data class SimuleringErrorDto(
    val code: SimuleringErrorCode,
)

class SimuleringProxyClientGcp(
    private val config: SuProxyConfig,
    private val azureAd: AzureAd,
    private val clock: Clock,
) : SimuleringClient {
    private val log = LoggerFactory.getLogger(this::class.java)

    private fun bearerToken(): String {
        val brukerToken = JwtToken.BrukerToken.fraCoroutineContextOrNull()

        return if (brukerToken != null) {
            azureAd.onBehalfOfToken(brukerToken.value, config.clientId)
        } else {
            azureAd.getSystemToken(config.clientId)
        }
    }

    override fun simulerUtbetaling(utbetalingForSimulering: Utbetaling.UtbetalingForSimulering): Either<SimuleringFeilet, Simulering> {
        val soapRequest = buildXmlRequestBody(utbetalingForSimulering)
        val token = bearerToken()
        val (_, response, result) =
            "${config.url}/simulerberegning"
                .httpPost()
                .authentication().bearer(token)
                .header(HttpHeaders.ContentType, "application/xml; charset=utf-8")
                .header(HttpHeaders.Accept, ContentType.Application.Xml.toString())
                .header("Nav-Call-Id", getOrCreateCorrelationIdFromThreadLocal())
                .body(soapRequest)
                .responseString()

        return result.fold(
            success = { soapResponse ->
                mapSimuleringResponse(
                    saksnummer = utbetalingForSimulering.saksnummer,
                    fnr = utbetalingForSimulering.fnr,
                    simuleringsperiode = utbetalingForSimulering.periode,
                    soapRequest = soapRequest,
                    soapResponse = soapResponse,
                    clock = clock,
                )
            },
            failure = {
                val feil = when (response.statusCode) {
                    500 -> {
                        log.error("500: Feil ved simulering saksnummer ${utbetalingForSimulering.saksnummer}: ${response.statusCode} ${response.responseMessage}")
                        try {
                            val dto = jacksonObjectMapper()
                                .readValue(response.data, SimuleringErrorDto::class.java)

                            mapError(dto.code)
                        } catch (e: Exception) {
                            log.error(
                                "Kunne ikke parse SimuleringErrorDto fra response. Returnerer TekniskFeil",
                                e,
                            )
                            SimuleringFeilet.TekniskFeil
                        }
                    }
                    else -> {
                        log.error("Feil ved simulering saksnummer ${utbetalingForSimulering.saksnummer}: ${response.statusCode} ${response.responseMessage}")
                        SimuleringFeilet.TekniskFeil
                    }
                }
                Either.Left(feil)
            },
        )
    }
}
