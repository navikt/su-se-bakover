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
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.infrastructure.config.ApplicationConfig.ClientsConfig.SuProxyConfig
import no.nav.su.se.bakover.common.infrastructure.correlation.getOrCreateCorrelationIdFromThreadLocal
import no.nav.su.se.bakover.common.infrastructure.token.JwtToken
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.domain.oppdrag.avstemming.toFagområde
import org.slf4j.LoggerFactory
import tilbakekreving.domain.kravgrunnlag.Kravgrunnlag
import tilbakekreving.domain.kravgrunnlag.rått.RåTilbakekrevingsvedtakForsendelse
import tilbakekreving.domain.vedtak.KunneIkkeAnnullerePåbegynteVedtak
import tilbakekreving.domain.vedtak.KunneIkkeSendeTilbakekrevingsvedtak
import tilbakekreving.domain.vedtak.Tilbakekrevingsklient
import tilbakekreving.domain.vurdering.VurderingerMedKrav
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

private fun mapError(code: TilbakekrevingErrorCode): KunneIkkeSendeTilbakekrevingsvedtak =
    when (code) {
        TilbakekrevingErrorCode.FeilStatusFraOppdrag -> KunneIkkeSendeTilbakekrevingsvedtak.FeilStatusFraOppdrag
        TilbakekrevingErrorCode.KlarteIkkeHenteSamlToken -> KunneIkkeSendeTilbakekrevingsvedtak.KlarteIkkeHenteSamlToken
        TilbakekrevingErrorCode.NullRespons -> KunneIkkeSendeTilbakekrevingsvedtak.UkjentFeil
        TilbakekrevingErrorCode.UkjentFeil -> KunneIkkeSendeTilbakekrevingsvedtak.UkjentFeil
    }

class TilbakekrevingProxyClient(
    private val config: SuProxyConfig,
    private val azureAd: AzureAd,
    private val clock: Clock,
) : Tilbakekrevingsklient {
    private val log = LoggerFactory.getLogger(this::class.java)

    private fun bearerToken(): String {
        val brukerToken = JwtToken.BrukerToken.fraCoroutineContextOrNull() ?: throw IllegalStateException("Må være person som prøver handlingen")
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
            .header(HttpHeaders.Accept, ContentType.Application.Xml.toString())
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

                            mapError(dto.code)
                        } catch (e: Exception) {
                            log.error(
                                "Kunne ikke parse Tilbakekrevingerrordto fra response. Returnerer TekniskFeil",
                                e,
                            )
                            KunneIkkeSendeTilbakekrevingsvedtak.UkjentFeil
                        }
                    }
                    else -> {
                        log.error("Feil ved simulering saksnummer $saksnummer: ${response.statusCode} ${response.responseMessage}")
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
    ): Either<KunneIkkeAnnullerePåbegynteVedtak, RåTilbakekrevingsvedtakForsendelse> =
        RåTilbakekrevingsvedtakForsendelse(
            requestXml = "{\"requestJson\": \"stubbed\"}",
            tidspunkt = Tidspunkt.now(clock),
            responseXml = "{\"responseJson\": \"stubbed\"}",
        ).right()
}
