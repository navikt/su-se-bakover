@file:Suppress("HttpUrlsUsage")

package tilbakekreving.infrastructure.client

import arrow.core.Either
import arrow.core.flatten
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.auth.SamlTokenProvider
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.infrastructure.soap.buildSoapEnvelope
import no.nav.su.se.bakover.common.sikkerLogg
import no.nav.su.se.bakover.common.tid.Tidspunkt
import org.slf4j.LoggerFactory
import tilbakekreving.domain.kravgrunnlag.rått.RåTilbakekrevingsvedtakForsendelse
import tilbakekreving.domain.vedtak.KunneIkkeSendeTilbakekrevingsvedtak
import tilbakekreving.domain.vedtak.Tilbakekrevingsklient
import tilbakekreving.domain.vurdering.VurderingerMedKrav
import tilbakekreving.infrastructure.client.dto.Alvorlighetsgrad
import tilbakekreving.infrastructure.client.dto.deserializeTilbakekrevingsvedtakResponse
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Clock
import java.time.Duration
import java.util.UUID

private const val ACTION =
    "http://okonomi.nav.no/tilbakekrevingService/TilbakekrevingPortType/tilbakekrevingsvedtakRequest"

class TilbakekrevingSoapClient(
    private val baseUrl: String,
    private val samlTokenProvider: SamlTokenProvider,
    private val clock: Clock,
) : Tilbakekrevingsklient {

    private val log = LoggerFactory.getLogger(this::class.java)

    private val client: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .followRedirects(HttpClient.Redirect.NEVER)
        .build()

    /**
     * Sender informasjon til oppdrag hvordan vi vil avgjøre om vi vil kreve tilbake eller ikke.
     *
     * @param attestertAv Saksbehandleren som har attestert vedtaket og trykker iverksett. Ideélt sett skulle vi sendt både saksbehandler og attestant, siden økonomiloven krever attstant.
     */
    override fun sendTilbakekrevingsvedtak(
        vurderingerMedKrav: VurderingerMedKrav,
        attestertAv: NavIdentBruker.Attestant,
    ): Either<KunneIkkeSendeTilbakekrevingsvedtak, RåTilbakekrevingsvedtakForsendelse> {
        val saksnummer = vurderingerMedKrav.saksnummer

        return Either.catch {
            val soapBody = buildTilbakekrevingSoapRequest(
                vurderingerMedKrav = vurderingerMedKrav,
                attestertAv = attestertAv,
            ).getOrElse { return it.left() }

            val assertion = getSamlToken(saksnummer, soapBody).getOrElse { return it.left() }

            val soapRequest = buildSoapEnvelope(
                action = ACTION,
                messageId = UUID.randomUUID().toString(),
                serviceUrl = baseUrl,
                assertion = assertion,
                body = soapBody,
            )
            val httpRequest = HttpRequest.newBuilder(URI(baseUrl))
                .header("SOAPAction", ACTION)
                .POST(HttpRequest.BodyPublishers.ofString(soapRequest))
                .build()
            val (soapResponse: String?, status: Int) = client.send(httpRequest, HttpResponse.BodyHandlers.ofString())
                .let {
                    it.body() to it.statusCode()
                }
            if (status != 200) {
                log.error(
                    "Feil ved sending av tilbakekrevingsvedtak: Forventet statusCode 200 for saksnummer: $saksnummer, statusCode: $status. Se sikkerlogg for request.",
                    RuntimeException("Trigger stacktrace"),
                )
                sikkerLogg.error("Feil ved sending av tilbakekrevingsvedtak: Forventet statusCode 200 for saksnummer: $saksnummer, statusCode: $status, Response: $soapResponse Request: $soapRequest")
                return KunneIkkeSendeTilbakekrevingsvedtak.FeilStatusFraOppdrag.left()
            }

            kontrollerResponse(soapRequest, soapResponse, saksnummer)
                .map {
                    log.info(
                        "SOAP kall mot tilbakekrevingskomponenten OK for saksnummer $saksnummer. Se sikkerlogg for detaljer.",
                    )
                    sikkerLogg.info(
                        "SOAP kall mot tilbakekrevingskomponenten OK for saksnummer $saksnummer. Response: $soapResponse, Request: $soapRequest. ",
                    )

                    RåTilbakekrevingsvedtakForsendelse(
                        requestXml = soapRequest,
                        tidspunkt = Tidspunkt.now(clock),
                        responseXml = soapResponse,
                    )
                }
        }.mapLeft { throwable ->
            log.error(
                "SOAP kall mot tilbakekrevingskomponenten feilet for saksnummer $saksnummer og eksternKravgrunnlagId ${vurderingerMedKrav.eksternKravgrunnlagId}. Se sikkerlogg for detaljer.",
                RuntimeException("Legger på stacktrace for enklere debug"),
            )
            sikkerLogg.error(
                "SOAP kall mot tilbakekrevingskomponenten feilet for saksnummer $saksnummer og eksternKravgrunnlagId ${vurderingerMedKrav.eksternKravgrunnlagId}. Se vanlig logg for stacktrace.",
                throwable,
            )
            KunneIkkeSendeTilbakekrevingsvedtak.UkjentFeil
        }.flatten()
    }

    private fun getSamlToken(
        saksnummer: Saksnummer,
        soapBody: String,
    ): Either<KunneIkkeSendeTilbakekrevingsvedtak, String> {
        return samlTokenProvider.samlToken().getOrElse {
            // SamlTokenProvider logger, men mangler kontekst.
            log.error(
                "Feil ved sending av tilbakekrevingsvedtak: Kunne ikke hente SAML-token for saksnummer: $saksnummer. Se sikkerlogg for soap body.",
                RuntimeException("Trigger stacktrace"),
            )
            sikkerLogg.error("Feil ved sending av tilbakekrevingsvedtak: Kunne ikke hente SAML-token for saksnummer: $saksnummer. soapBody: $soapBody")
            return KunneIkkeSendeTilbakekrevingsvedtak.KlarteIkkeHenteSamlToken.left()
        }.toString().right()
    }

    /**
     * Dersom vi får en alvorlighetsgrad som ikke er OK, så skal vi logge dette og returnere en feil.
     * I andre tilfeller antar vi at alt er OK, men logger error der noe må følges opp manuelt.
     */
    private fun kontrollerResponse(
        request: String,
        response: String,
        saksnummer: Saksnummer,
    ): Either<KunneIkkeSendeTilbakekrevingsvedtak, Unit> {
        return response.deserializeTilbakekrevingsvedtakResponse(request).fold(
            {
                // Vi logger i funksjonen. Dersom vi ikke klarer deserialisere antar vi at det har gått OK. Men det må følges opp manuelt.
                Unit.right()
            },
            { result ->
                when (val a = result.mmel.alvorlighetsgrad) {
                    null -> {
                        log.error(
                            "Mottok ikke mmel.alvorlighetsgrad. Antar det var OK. Må følges opp manuelt. Saksnummer $saksnummer. Se sikkerlogg for detaljer.",
                            RuntimeException("Legger på stacktrace for enklere debug"),
                        )
                        sikkerLogg.error(
                            "Mottok ikke mmel.alvorlighetsgrad. Antar det var OK. Må følges opp manuelt. Saksnummer $saksnummer. Response $response. Request: $request.",
                        )
                        Unit.right()
                    }
                    else -> Alvorlighetsgrad.fromString(a).fold(
                        {
                            log.error(
                                "Feil ved sending av tilbakekrevingsvedtak: Ukjent alvorlighetsgrad: $a. Antar det var OK. Må følges opp manuelt. Se sikkerlogg for detaljer.",
                                RuntimeException("Trigger stacktrace"),
                            )
                            sikkerLogg.error("Feil ved sending av tilbakekrevingsvedtak: Ukjent alvorlighetsgrad: $a. Antar det var OK. Må følges opp manuelt. Response: $response, Request: $request.")
                            Unit.right()
                        },
                        { alvorlighetsgrad ->
                            kontrollerAlvorlighetsgrad(alvorlighetsgrad, saksnummer, response, request)
                        },
                    )
                }
            },
        )
    }

    private fun kontrollerAlvorlighetsgrad(
        alvorlighetsgrad: Alvorlighetsgrad,
        saksnummer: Saksnummer,
        response: String,
        request: String,
    ): Either<KunneIkkeSendeTilbakekrevingsvedtak, Unit> {
        return when (alvorlighetsgrad) {
            Alvorlighetsgrad.OK -> Unit.right()

            Alvorlighetsgrad.OK_MED_VARSEL,
            -> {
                log.error(
                    "Fikk et varsel fra tilbakekrevingskomponenten når vi vedtok en tilbakekreving. Saksnummer $saksnummer. Se sikkerlogg for detaljer.",
                    RuntimeException("Legger på stacktrace for enklere debug"),
                )
                sikkerLogg.error(
                    "Fikk et varsel fra tilbakekrevingskomponenten når vi vedtok en tilbakekreving. Den er fremdeles sendt OK. Saksnummer $saksnummer. Response $response. Request: $request. ",
                )
                Unit.right()
            }

            Alvorlighetsgrad.ALVORLIG_FEIL,
            Alvorlighetsgrad.SQL_FEIL,
            -> {
                log.error(
                    "Fikk $alvorlighetsgrad fra tilbakekrevingskomponenten når vi vedtok en tilbakekreving. Saksnummer $saksnummer. Se sikkerlogg for detaljer.",
                    RuntimeException("Legger på stacktrace for enklere debug"),
                )
                sikkerLogg.error(
                    "Fikk et varsel fra tilbakekrevingskomponenten når vi vedtok en tilbakekreving. Saksnummer $saksnummer. Response $response. Request: $request.",
                )
                KunneIkkeSendeTilbakekrevingsvedtak.AlvorlighetsgradFeil.left()
            }
        }
    }
}
