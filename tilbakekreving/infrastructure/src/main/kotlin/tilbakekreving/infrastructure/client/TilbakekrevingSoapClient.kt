package tilbakekreving.infrastructure.client

import arrow.core.Either
import arrow.core.flatten
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import no.nav.okonomi.tilbakekrevingservice.TilbakekrevingPortType
import no.nav.okonomi.tilbakekrevingservice.TilbakekrevingsvedtakResponse
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.sikkerLogg
import no.nav.su.se.bakover.common.tid.Tidspunkt
import org.slf4j.LoggerFactory
import tilbakekreving.domain.kravgrunnlag.rått.RåTilbakekrevingsvedtakForsendelse
import tilbakekreving.domain.vedtak.KunneIkkeSendeTilbakekrevingsvedtak
import tilbakekreving.domain.vedtak.Tilbakekrevingsklient
import tilbakekreving.domain.vurdering.VurderingerMedKrav
import java.time.Clock

class TilbakekrevingSoapClient(
    private val tilbakekrevingPortType: TilbakekrevingPortType,
    private val clock: Clock,
) : Tilbakekrevingsklient {

    private val log = LoggerFactory.getLogger(this::class.java)

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
            val request = mapToTilbakekrevingsvedtakRequest(
                vurderingerMedKrav = vurderingerMedKrav,
                attestertAv = attestertAv,
            )
            val response = tilbakekrevingPortType.tilbakekrevingsvedtak(request)

            val requestXmlString = TilbakekrevingSoapClientMapper.toXml(request)
            val responseXmlString = mapErrorResponseTilXmlString(response)
            kontrollerResponse(response, saksnummer)
                .map {
                    log.info(
                        "SOAP kall mot tilbakekrevingskomponenten OK for saksnummer $saksnummer. Se sikkerlogg for detaljer.",
                    )
                    sikkerLogg.info(
                        "SOAP kall mot tilbakekrevingskomponenten OK for saksnummer $saksnummer. Request: $requestXmlString. Response: $responseXmlString",
                    )

                    RåTilbakekrevingsvedtakForsendelse(
                        requestXml = requestXmlString,
                        tidspunkt = Tidspunkt.now(clock),
                        responseXml = responseXmlString,
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
            KunneIkkeSendeTilbakekrevingsvedtak
        }.flatten()
    }

    private fun kontrollerResponse(
        response: TilbakekrevingsvedtakResponse,
        saksnummer: Saksnummer,
    ): Either<KunneIkkeSendeTilbakekrevingsvedtak, Unit> {
        return response.let {
            Alvorlighetsgrad.fromString(it.mmel.alvorlighetsgrad).let { alvorlighetsgrad ->
                when (alvorlighetsgrad) {
                    Alvorlighetsgrad.OK -> Unit.right()

                    Alvorlighetsgrad.OK_MED_VARSEL,
                    -> {
                        log.error(
                            "Fikk et varsel fra tilbakekrevingskomponenten når vi vedtok en tilbakekreving. For saksnummer $saksnummer. Se sikkerlogg for detaljer.",
                            RuntimeException("Legger på stacktrace for enklere debug"),
                        )
                        sikkerLogg.error(
                            "Fikk et varsel fra tilbakekrevingskomponenten når vi vedtok en tilbakekreving. Den er fremdeles sendt OK. For saksnummer $saksnummer. Response ${
                                mapErrorResponseTilXmlString(response)
                            }",
                        )
                        Unit.right()
                    }

                    Alvorlighetsgrad.ALVORLIG_FEIL,
                    Alvorlighetsgrad.SQL_FEIL,
                    -> {
                        log.error(
                            "Fikk $alvorlighetsgrad fra tilbakekrevingskomponenten når vi vedtok en tilbakekreving. For saksnummer $saksnummer. Se sikkerlogg for detaljer.",
                            RuntimeException("Legger på stacktrace for enklere debug"),
                        )
                        sikkerLogg.error(
                            "Fikk et varsel fra tilbakekrevingskomponenten når vi vedtok en tilbakekreving. For saksnummer $saksnummer. Response ${
                                mapErrorResponseTilXmlString(
                                    response,
                                )
                            }",
                        )
                        KunneIkkeSendeTilbakekrevingsvedtak.left()
                    }
                }
            }
        }
    }

    private fun mapErrorResponseTilXmlString(response: TilbakekrevingsvedtakResponse): String {
        return Either.catch {
            TilbakekrevingSoapClientMapper.toXml(
                response,
            )
        }.getOrElse {
            "Kunne ikke serialisere response til xml. Feilmelding: ${it.message}"
        }
    }

    enum class Alvorlighetsgrad(val value: String) {
        OK("00"),

        /** En varselmelding følger med */
        OK_MED_VARSEL("04"),

        /** Alvorlig feil som logges og stopper behandling av aktuelt tilfelle*/
        ALVORLIG_FEIL("08"),
        SQL_FEIL("12"),
        ;

        override fun toString() = value

        companion object {
            fun fromString(string: String): Alvorlighetsgrad {
                return entries.first { it.value == string }
            }
        }
    }
}
