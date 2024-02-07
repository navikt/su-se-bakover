@file:Suppress("HttpUrlsUsage")

package no.nav.su.se.bakover.client.oppdrag.simulering

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.getOrElse
import arrow.core.left
import no.nav.su.se.bakover.common.domain.auth.SamlTokenProvider
import no.nav.su.se.bakover.common.sikkerLogg
import org.slf4j.LoggerFactory
import økonomi.domain.simulering.Simulering
import økonomi.domain.simulering.SimuleringClient
import økonomi.domain.simulering.SimuleringFeilet
import økonomi.domain.utbetaling.Utbetaling
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Clock
import java.time.Duration
import java.util.UUID

private const val ACTION =
    "http://nav.no/system/os/tjenester/simulerFpService/simulerFpServiceGrensesnitt/simulerFpService/simulerBeregningRequest"

internal class SimuleringSoapClient(
    private val baseUrl: String,
    private val samlTokenProvider: SamlTokenProvider,
    private val clock: Clock,
) : SimuleringClient {

    private val log = LoggerFactory.getLogger(this::class.java)

    private val client: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .followRedirects(HttpClient.Redirect.NEVER)
        .build()

    override fun simulerUtbetaling(
        utbetalingForSimulering: Utbetaling.UtbetalingForSimulering,
    ): Either<SimuleringFeilet, Simulering> {
        val soapBody = buildXmlRequestBody(utbetalingForSimulering)
        val saksnummer = utbetalingForSimulering.saksnummer
        val assertion = samlTokenProvider.samlToken().getOrElse {
            // SamlTokenProvider logger, men mangler kontekst.
            log.error(
                "Feil ved simulering: Kunne ikke hente SAML-token for saksnummer: $saksnummer. Se sikkerlogg for soap body.",
                RuntimeException("Trigger stacktrace"),
            )
            sikkerLogg.error("Feil ved simulering: Kunne ikke hente SAML-token for saksnummer: $saksnummer. soapBody: $soapBody")
            return SimuleringFeilet.TekniskFeil.left()
        }.toString()
        val soapRequest = buildXmlRequestSoapEnvelope(
            action = ACTION,
            messageId = UUID.randomUUID().toString(),
            serviceUrl = baseUrl,
            assertion = assertion,
            body = soapBody,
        )
        // TODO jah: Kan fjerne debug etter vi har fått verifisert.
        log.debug(
            "Simulerer utbetaling for saksnummer: {}, baseUrl: $baseUrl. Se sikkerlogg for mer kontekst.",
            saksnummer,
        )
        sikkerLogg.debug("Simulerer utbetaling for saksnummer: {}, soapRequest: {}", saksnummer, soapRequest)
        return Either.catch {
            val httpRequest = HttpRequest.newBuilder(URI(baseUrl))
                .header("SOAPAction", ACTION)
                .POST(HttpRequest.BodyPublishers.ofString(soapRequest))
                .build()
            val (response: String?, status: Int) = client.send(httpRequest, HttpResponse.BodyHandlers.ofString()).let {
                it.body() to it.statusCode()
            }
            // TODO jah: Kan fjerne debug etter vi har fått verifisert.
            log.debug(
                "Simuleringsrespons for saksnummer: {}. statusCode: {}. Se sikkerlogg for mer kontekst.",
                saksnummer,
                status,
            )
            sikkerLogg.debug(
                "Simuleringsrespons for saksnummer: {}. statusCode: {}, response: {}",
                saksnummer,
                status,
                response,
            )
            if (status != 200) {
                log.error(
                    "Feil ved simulering: Forventet statusCode 200 for saksnummer: $saksnummer, statusCode: $status. Se sikkerlogg for request.",
                    RuntimeException("Trigger stacktrace"),
                )
                sikkerLogg.error("Feil ved simulering: Forventet statusCode 200 for saksnummer: $saksnummer, statusCode: $status, Soap-request: $soapRequest")
                return SimuleringFeilet.TekniskFeil.left()
            }

            response ?: return SimuleringFeilet.TekniskFeil.left().also {
                log.error(
                    "Feil ved simulering: Simuleringsresponsen fra Oppdrag var tom (forventet soap) for saksnummer: $saksnummer. statusCode: $status. Se sikkerlogg for request.",
                    RuntimeException("Trigger stacktrace"),
                )
                sikkerLogg.error("Simuleringsresponsen fra Oppdrag var tom (forventet soap) for saksnummer: $saksnummer. statusCode: $status. Soap-request: $soapRequest")
            }
        }.mapLeft { error: Throwable ->
            when (error) {
                is IOException -> {
                    log.warn(
                        "Feil ved simulering: Antar Oppdrag/UR stengt. Se sikkerlogg for kontekst.",
                        RuntimeException("Trigger stacktrace"),
                    )
                    sikkerLogg.warn("Feil ved simulering: Antar Oppdrag/UR stengt. Soap-request: $soapRequest", error)
                    SimuleringFeilet.UtenforÅpningstid
                }

                else -> {
                    log.warn(
                        "Feil ved simulering: Ukjent feil. Se sikkerlogg for kontekst.",
                        RuntimeException("Trigger stacktrace"),
                    )
                    sikkerLogg.warn("Feil ved simulering: Ukjent feil. Soap-request: $soapRequest", error)
                    SimuleringFeilet.TekniskFeil
                }
            }
        }.flatMap { soapResponse ->
            mapSimuleringResponse(
                saksnummer = saksnummer,
                fnr = utbetalingForSimulering.fnr,
                simuleringsperiode = utbetalingForSimulering.periode,
                soapRequest = soapRequest,
                soapResponse = soapResponse,
                clock = clock,
            )
        }
    }
}
