package tilbakekreving.infrastructure.client

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.github.kittinunf.fuel.core.extensions.authentication
import com.github.kittinunf.fuel.httpPost
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import no.nav.su.se.bakover.common.auth.AzureAd
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.infrastructure.config.ApplicationConfig.ClientsConfig.SuProxyConfig
import no.nav.su.se.bakover.common.infrastructure.correlation.getOrCreateCorrelationIdFromThreadLocal
import no.nav.su.se.bakover.common.infrastructure.token.JwtToken
import no.nav.su.se.bakover.common.sikkerLogg
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.domain.oppdrag.avstemming.toFagområde
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import tilbakekreving.domain.kravgrunnlag.Kravgrunnlag
import tilbakekreving.domain.kravgrunnlag.rått.RåTilbakekrevingsvedtakForsendelse
import tilbakekreving.domain.vedtak.KunneIkkeAnnullerePåbegynteVedtak
import tilbakekreving.domain.vedtak.KunneIkkeSendeTilbakekrevingsvedtak
import tilbakekreving.domain.vedtak.Tilbakekrevingsklient
import tilbakekreving.domain.vurdering.VurderingerMedKrav
import tilbakekreving.infrastructure.client.dto.Alvorlighetsgrad
import tilbakekreving.infrastructure.client.dto.deserializeTilbakekrevingsvedtakResponse
import java.time.Clock

data class TilbakekrevingErrorDto(
    val code: TilbakekrevingErrorCode,
)

enum class TilbakekrevingErrorCode {
    FeilStatusFraOppdrag,
    KlarteIkkeHenteSamlToken,
    NullRespons,
    UkjentFeil,
}

internal fun mapErrorSend(code: TilbakekrevingErrorCode): KunneIkkeSendeTilbakekrevingsvedtak =
    when (code) {
        TilbakekrevingErrorCode.FeilStatusFraOppdrag -> KunneIkkeSendeTilbakekrevingsvedtak.FeilStatusFraOppdrag
        TilbakekrevingErrorCode.KlarteIkkeHenteSamlToken -> KunneIkkeSendeTilbakekrevingsvedtak.KlarteIkkeHenteSamlToken
        TilbakekrevingErrorCode.NullRespons -> KunneIkkeSendeTilbakekrevingsvedtak.UkjentFeil
        TilbakekrevingErrorCode.UkjentFeil -> KunneIkkeSendeTilbakekrevingsvedtak.UkjentFeil
    }

internal fun mapErrorAnnuller(code: TilbakekrevingErrorCode): KunneIkkeAnnullerePåbegynteVedtak =
    when (code) {
        TilbakekrevingErrorCode.FeilStatusFraOppdrag -> KunneIkkeAnnullerePåbegynteVedtak.FeilStatusFraOppdrag
        TilbakekrevingErrorCode.KlarteIkkeHenteSamlToken -> KunneIkkeAnnullerePåbegynteVedtak.FeilVedGenereringAvToken
        TilbakekrevingErrorCode.NullRespons -> KunneIkkeAnnullerePåbegynteVedtak.UkjentFeil
        TilbakekrevingErrorCode.UkjentFeil -> KunneIkkeAnnullerePåbegynteVedtak.UkjentFeil
    }

class TilbakekrevingProxyClient(
    private val config: SuProxyConfig,
    private val azureAd: AzureAd,
    private val clock: Clock,
) : Tilbakekrevingsklient {
    private val log = LoggerFactory.getLogger(this::class.java)

    private fun bearerToken(): String {
        val brukerToken = JwtToken.BrukerToken.fraCoroutineContextOrNull()
            ?: throw IllegalStateException(
                "Må være person som prøver handlingen ikke systembruker",
            ).also {
                log.error(
                    "Mangler brukerToken i coroutine context ved kall til TilbakekrevingProxyClient.bearerToken",
                )
            }
        return azureAd.onBehalfOfToken(brukerToken.value, config.clientId)
    }

    /**
     * Sender informasjon til oppdrag hvordan vi vil avgjøre om vi vil kreve tilbake eller ikke.
     * @param attestertAv Saksbehandleren som har attestert vedtaket og trykker iverksett. Ideélt sett skulle vi sendt både saksbehandler og attestant, siden økonomiloven krever attstant.
     */
    override fun sendTilbakekrevingsvedtak(
        vurderingerMedKrav: VurderingerMedKrav,
        attestertAv: NavIdentBruker.Attestant,
        sakstype: Sakstype,
    ): Either<KunneIkkeSendeTilbakekrevingsvedtak, RåTilbakekrevingsvedtakForsendelse> {
        val saksnummer = vurderingerMedKrav.saksnummer

        val soapRequest = buildTilbakekrevingSoapRequest(
            vurderingerMedKrav = vurderingerMedKrav,
            attestertAv = attestertAv,
            fagområde = sakstype.toFagområde(),
        ).getOrElse { return it.left() }

        val token = bearerToken()
        val (_, response, result) = "${config.url}/tilbakekreving/vedtak"
            .httpPost()
            .authentication().bearer(token)
            .header(HttpHeaders.ContentType, "application/xml; charset=utf-8")
            .header(HttpHeaders.Accept, "${ContentType.Application.Xml}, ${ContentType.Application.Json}")
            .header("Nav-Call-Id", getOrCreateCorrelationIdFromThreadLocal())
            .body(soapRequest)
            .responseString()

        return result.fold(
            success = { soapResponse ->
                kontrollerResponse(
                    soapRequest,
                    soapResponse,
                    saksnummer,
                    log = log,
                )
                    .map {
                        mapKontrollertResponse(
                            saksnummer,
                            soapResponse,
                            soapRequest,
                            clock = clock,
                            log = log,
                        )
                    }
            },
            failure = {
                val feil = when (response.statusCode) {
                    500 -> {
                        log.error("500: Feil ved send tilbakekreving saksnummer $saksnummer: ${response.statusCode} ${response.responseMessage}")
                        try {
                            val dto = jacksonObjectMapper()
                                .readValue(response.data, TilbakekrevingErrorDto::class.java)

                            mapErrorSend(dto.code)
                        } catch (e: Exception) {
                            log.error(
                                "Kunne ikke parse Tilbakekrevingerrordto fra response. Returnerer TekniskFeil",
                                e,
                            )
                            KunneIkkeSendeTilbakekrevingsvedtak.UkjentFeil
                        }
                    }
                    else -> {
                        log.error("Feil ved send tilbakekreving saksnummer $saksnummer: ${response.statusCode} data: ${response.data}")
                        KunneIkkeSendeTilbakekrevingsvedtak.UkjentFeil
                    }
                }
                Either.Left(feil)
            },
        )
    }

    /**
     https://confluence.adeo.no/display/OKSY/Detaljer+om+de+enkelte+ID-koder?preview=/178067795/178067800/worddav1549728a4f1bb4ae0651e7017a7cae86.png
     */
    override fun annullerKravgrunnlag(
        annullertAv: NavIdentBruker.Saksbehandler,
        kravgrunnlagSomSkalAnnulleres: Kravgrunnlag,
    ): Either<KunneIkkeAnnullerePåbegynteVedtak, RåTilbakekrevingsvedtakForsendelse> {
        val soapRequest = buildTilbakekrevingAnnulleringSoapRequest(
            eksternVedtakId = kravgrunnlagSomSkalAnnulleres.eksternVedtakId,
            saksbehandletAv = annullertAv.navIdent,
        )
        val saksnummer = kravgrunnlagSomSkalAnnulleres.saksnummer

        val token = bearerToken()
        val (_, response, result) = "${config.url}/tilbakekreving/annuller"
            .httpPost()
            .authentication().bearer(token)
            .header(HttpHeaders.ContentType, "application/xml; charset=utf-8")
            .header(HttpHeaders.Accept, "${ContentType.Application.Xml}, ${ContentType.Application.Json}")
            .header("Nav-Call-Id", getOrCreateCorrelationIdFromThreadLocal())
            .body(soapRequest)
            .responseString()

        return result.fold(
            success = { soapResponse ->
                kontrollerResponse(
                    soapRequest,
                    soapResponse,
                    saksnummer,
                    log = log,
                ).map {
                    mapKontrollertResponse(
                        saksnummer,
                        soapResponse,
                        soapRequest,
                        log = log,
                        clock = clock,
                    )
                }.mapLeft {
                    KunneIkkeAnnullerePåbegynteVedtak.FeilStatusFraOppdrag
                }
            },
            failure = {
                val feil = when (response.statusCode) {
                    500 -> {
                        log.error("500: Feil ved annuller tilbakekreving saksnummer $saksnummer: ${response.statusCode} ${response.responseMessage}")
                        try {
                            val dto = jacksonObjectMapper()
                                .readValue(response.data, TilbakekrevingErrorDto::class.java)

                            mapErrorAnnuller(dto.code)
                        } catch (e: Exception) {
                            log.error(
                                "annuller Kunne ikke parse Tilbakekrevingerrordto fra response. Returnerer TekniskFeil",
                                e,
                            )
                            KunneIkkeAnnullerePåbegynteVedtak.UkjentFeil
                        }
                    }
                    else -> {
                        log.error("Feil ved annuller tilbakekreving saksnummer $saksnummer: ${response.statusCode} data: ${response.data}")
                        KunneIkkeAnnullerePåbegynteVedtak.UkjentFeil
                    }
                }
                Either.Left(feil)
            },
        )
    }
}

internal fun mapKontrollertResponse(
    saksnummer: Saksnummer,
    soapResponse: String?,
    soapRequest: String,
    clock: Clock,
    log: Logger,
): RåTilbakekrevingsvedtakForsendelse {
    log.info("SOAP kall mot tilbakekrevingskomponenten OK for saksnummer $saksnummer. Se sikkerlogg for detaljer.")
    sikkerLogg.info("SOAP kall mot tilbakekrevingskomponenten OK for saksnummer $saksnummer. Response: $soapResponse, Request: $soapRequest.")

    return RåTilbakekrevingsvedtakForsendelse(
        requestXml = soapRequest,
        tidspunkt = Tidspunkt.now(clock),
        responseXml = soapResponse
            ?: "soapResponse var null - dette er sannsynligvis en teksnisk feil, f.eks. ved at http-body er lest mer enn 1 gang.",
    )
}

/**
 * Dersom vi får en alvorlighetsgrad som ikke er OK, så skal vi logge dette og returnere en feil.
 * I andre tilfeller antar vi at alt er OK, men logger error der noe må følges opp manuelt.
 */
internal fun kontrollerResponse(
    request: String,
    response: String?,
    saksnummer: Saksnummer,
    log: Logger,
): Either<KunneIkkeSendeTilbakekrevingsvedtak, Unit> {
    val deserialisert = response?.deserializeTilbakekrevingsvedtakResponse(request) ?: run {
        log.error("Fikk null-response ved sending av tilbakekrevingsvedtak. Antar det var OK. Må følges opp manuelt. Saksnummer $saksnummer, se sikkerlogg for detaljer.")
        sikkerLogg.error("Fikk null-response ved sending av tilbakekrevingsvedtak. Antar det var OK. Må følges opp manuelt. Saksnummer $saksnummer. Request: $request.")
        return Unit.right()
    }
    return deserialisert.fold(
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
                        kontrollerAlvorlighetsgrad(
                            alvorlighetsgrad,
                            saksnummer,
                            response,
                            request,
                            log = log,
                        )
                    },
                )
            }
        },
    )
}

internal fun kontrollerAlvorlighetsgrad(
    alvorlighetsgrad: Alvorlighetsgrad,
    saksnummer: Saksnummer,
    response: String,
    request: String,
    log: Logger,
): Either<KunneIkkeSendeTilbakekrevingsvedtak, Unit> {
    return when (alvorlighetsgrad) {
        Alvorlighetsgrad.OK -> Unit.right()

        Alvorlighetsgrad.OK_MED_VARSEL,
        -> {
            log.error(
                "Fikk et varsel fra tilbakekrevingskomponenten når vi vedtok en tilbakekreving. Saksnummer $saksnummer. Se sikkerlogg for detaljer, og request.",
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
