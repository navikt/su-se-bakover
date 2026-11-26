package no.nav.su.se.bakover.client.oppdrag.simulering

import arrow.core.Either
import arrow.core.flatMap
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.github.kittinunf.fuel.core.extensions.authentication
import com.github.kittinunf.fuel.httpPost
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import no.nav.su.se.bakover.client.oppdrag.simulering.historisk.byggMeldingskandidat
import no.nav.su.se.bakover.client.oppdrag.simulering.historisk.tilHistoriskRespons
import no.nav.su.se.bakover.client.oppdrag.utbetaling.UtbetalingRequest
import no.nav.su.se.bakover.common.auth.AzureAd
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.infrastructure.config.ApplicationConfig.ClientsConfig.SuProxyConfig
import no.nav.su.se.bakover.common.infrastructure.token.JwtToken
import no.nav.su.se.bakover.common.infrastructure.xml.xmlMapper
import no.nav.su.se.bakover.common.sikkerLogg
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.domain.historisk.revurdering.utbetaling.HistoriskSimuleringClient
import no.nav.su.se.bakover.domain.historisk.revurdering.utbetaling.HistoriskSimuleringsforespørsel
import no.nav.su.se.bakover.domain.historisk.revurdering.utbetaling.HistoriskSimuleringsrespons
import no.nav.su.se.bakover.domain.historisk.revurdering.utbetaling.KunneIkkeSimulereHistoriskUtbetaling
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
) : SimuleringClient,
    HistoriskSimuleringClient {
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
        return sendSimuleringsforespørsel(soapRequest, utbetalingForSimulering.saksnummer).flatMap { soapResponse ->
            mapSimuleringResponse(
                saksnummer = utbetalingForSimulering.saksnummer,
                fnr = utbetalingForSimulering.fnr,
                simuleringsperiode = utbetalingForSimulering.periode,
                soapRequest = soapRequest,
                soapResponse = soapResponse,
                clock = clock,
            )
        }
    }

    override fun simulerHistorisk(
        forespørsel: HistoriskSimuleringsforespørsel,
    ): Either<KunneIkkeSimulereHistoriskUtbetaling, HistoriskSimuleringsrespons> =
        forespørsel.byggMeldingskandidat(Tidspunkt.now(clock))
            .mapLeft<KunneIkkeSimulereHistoriskUtbetaling> { KunneIkkeSimulereHistoriskUtbetaling.UgyldigMeldingskandidat }
            .flatMap { request ->
                simulerHistoriskUtbetaling(request, forespørsel.grunnlag.periode)
                    .mapLeft { KunneIkkeSimulereHistoriskUtbetaling.Klientfeil(it) }
            }

    internal fun simulerHistoriskUtbetaling(
        request: UtbetalingRequest.OppdragRequest,
        simuleringsperiode: Periode,
    ): Either<SimuleringFeilet, HistoriskSimuleringsrespons> {
        val soapRequest = buildXmlRequestBody(simuleringsperiode, request)
        val saksnummer = request.saksnummer()
        return sendSimuleringsforespørsel(soapRequest, saksnummer).flatMap { soapResponse ->
            Either.catch {
                xmlMapper.readValue<Envelope>(soapResponse).body.simulerBeregningResponse
                    .tilHistoriskRespons(soapResponse)
            }.mapLeft { throwable ->
                log.error("Kunne ikke lese historisk simuleringsrespons for saksnummer $saksnummer. Se sikkerlogg for context.")
                sikkerLogg.error(
                    "Kunne ikke lese historisk simuleringsrespons for saksnummer $saksnummer. " +
                        "Response: $soapResponse, SoapRequest: $soapRequest",
                    throwable,
                )
                SimuleringFeilet.TekniskFeil
            }
        }
    }

    private fun sendSimuleringsforespørsel(
        soapRequest: String,
        saksnummer: Saksnummer,
    ): Either<SimuleringFeilet, String> {
        val token = bearerToken()
        val (_, response, result) =
            "${config.url}/simulerberegning"
                .httpPost()
                .authentication().bearer(token)
                .header(HttpHeaders.ContentType, "application/xml; charset=utf-8")
                .header(HttpHeaders.Accept, "${ContentType.Application.Xml}, ${ContentType.Application.Json}")
                .body(soapRequest)
                .responseString()

        return result.fold(
            success = { soapResponse -> Either.Right(soapResponse) },
            failure = {
                val feil = when (response.statusCode) {
                    500 -> {
                        try {
                            val dto = jacksonObjectMapper()
                                .readValue(response.data, SimuleringErrorDto::class.java)
                            val mappetFeil = mapError(dto.code)
                            when (mappetFeil) {
                                SimuleringFeilet.UtenforÅpningstid -> {
                                    log.warn("500: Feil ved simulering saksnummer $saksnummer: ${response.statusCode} ${response.responseMessage} feilkode: $mappetFeil")
                                }
                                else -> {
                                    log.error("500: Feil ved simulering saksnummer $saksnummer: ${response.statusCode} ${response.responseMessage} feilkode: $mappetFeil")
                                }
                            }
                            mappetFeil
                        } catch (e: Exception) {
                            log.error(
                                "Kunne ikke parse SimuleringErrorDto fra response. Returnerer TekniskFeil",
                                e,
                            )
                            SimuleringFeilet.TekniskFeil
                        }
                    }
                    else -> {
                        log.error(
                            "Feil ved simulering saksnummer $saksnummer: " +
                                "statusCode=${response.statusCode} responseMessage=${response.responseMessage}. " +
                                "Se sikkerlogg for response body. Merk dette skal ikke skje, sjekk hvorfor.",
                        )
                        sikkerLogg.error(
                            "Feil ved simulering saksnummer $saksnummer: " +
                                "statusCode=${response.statusCode} responseBody=${response.data.toString(Charsets.UTF_8)}",
                        )
                        SimuleringFeilet.TekniskFeil
                    }
                }
                Either.Left(feil)
            },
        )
    }
}
