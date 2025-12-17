package no.nav.su.se.bakover.client.oppdrag.simulering

import arrow.core.Either
import arrow.core.right
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.github.kittinunf.fuel.core.extensions.authentication
import com.github.kittinunf.fuel.httpPost
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import no.nav.su.se.bakover.common.auth.AzureAd
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.infrastructure.config.ApplicationConfig.ClientsConfig.SuProxyConfig
import no.nav.su.se.bakover.common.infrastructure.correlation.getOrCreateCorrelationIdFromThreadLocal
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

class SimuerlingProxyClientGcp(
    private val config: SuProxyConfig,
    private val azure: AzureAd,
    private val clock: Clock,
) : SimuleringClient {
    private val log = LoggerFactory.getLogger(this::class.java)

    override fun simulerUtbetaling(utbetalingForSimulering: Utbetaling.UtbetalingForSimulering): Either<SimuleringFeilet, Simulering> {
        val soapBody = buildXmlRequestBody(utbetalingForSimulering)

        val (_, response, result) =
            "${config.url}/simulerberegning"
                .httpPost()
                .authentication().bearer(azure.getSystemToken(config.clientId)) // TODO obo her
                .header(HttpHeaders.ContentType, "application/xml; charset=utf-8")
                .header(HttpHeaders.Accept, ContentType.Application.Json.toString())
                .header("Nav-Call-Id", getOrCreateCorrelationIdFromThreadLocal())
                .body(soapBody)
                .responseString()

        return result.fold(
            success = { body ->
                deserialize<Simulering>(body).right()
            },
            failure = {
                val feil = when (response.statusCode) {
                    500 -> {
                        log.error("500: Feil ved simulering saksnummer ${utbetalingForSimulering.saksnummer}: ${response.statusCode} ${response.responseMessage}")
                        val dto = jacksonObjectMapper()
                            .readValue(response.data, SimuleringErrorDto::class.java)

                        mapError(dto.code)
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
