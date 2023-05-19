package no.nav.su.se.bakover.client.oppdrag.tilbakekreving

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.left
import arrow.core.right
import no.nav.okonomi.tilbakekrevingservice.TilbakekrevingPortType
import no.nav.okonomi.tilbakekrevingservice.TilbakekrevingsvedtakResponse
import no.nav.su.se.bakover.common.sikkerLogg
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.RåTilbakekrevingsvedtakForsendelse
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.TilbakekrevingClient
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.Tilbakekrevingsvedtak
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.TilbakekrevingsvedtakForsendelseFeil
import org.slf4j.LoggerFactory
import java.time.Clock

class TilbakekrevingSoapClient(
    private val tilbakekrevingPortType: TilbakekrevingPortType,
    private val clock: Clock,
) : TilbakekrevingClient {

    private val log = LoggerFactory.getLogger(this::class.java)

    /**
     * Sender informasjon til oppdrag hvordan vi vil avgjøre om vi vil kreve tilbake eller ikke.
     */
    override fun sendTilbakekrevingsvedtak(tilbakekrevingsvedtak: Tilbakekrevingsvedtak): Either<TilbakekrevingsvedtakForsendelseFeil, RåTilbakekrevingsvedtakForsendelse> {
        return Either.catch {
            val request = mapToTilbakekrevingsvedtakRequest(tilbakekrevingsvedtak)
            val response = tilbakekrevingPortType.tilbakekrevingsvedtak(request)

            kontrollerResponse(response)
                .mapLeft {
                    it
                }
                .map {
                    RåTilbakekrevingsvedtakForsendelse(
                        requestXml = TilbakekrevingSoapClientMapper.toXml(request),
                        tidspunkt = Tidspunkt.now(clock),
                        responseXml = TilbakekrevingSoapClientMapper.toXml(response),
                    )
                }
        }.mapLeft {
            log.error("SOAP kall mot tilbakekreving feilet:", it)
            TilbakekrevingsvedtakForsendelseFeil
        }.flatMap {
            it
        }
    }

    private fun kontrollerResponse(response: TilbakekrevingsvedtakResponse): Either<TilbakekrevingsvedtakForsendelseFeil, Unit> {
        return response.let {
            Alvorlighetsgrad.fromString(it.mmel.alvorlighetsgrad).let { alvorlighetsgrad ->
                when (alvorlighetsgrad) {
                    Alvorlighetsgrad.OK,
                    Alvorlighetsgrad.OK_MED_VARSEL,
                    -> {
                        Unit.right()
                    }
                    Alvorlighetsgrad.ALVORLIG_FEIL,
                    Alvorlighetsgrad.SQL_FEIL,
                    -> {
                        log.error("SOAP kall mot tilbakekreving feilet, status:$alvorlighetsgrad. Se sikkerlogg for detaljer.")
                        sikkerLogg.error(TilbakekrevingSoapClientMapper.toXml(response))
                        TilbakekrevingsvedtakForsendelseFeil.left()
                    }
                }
            }
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
                return values().first { it.value == string }
            }
        }
    }
}
