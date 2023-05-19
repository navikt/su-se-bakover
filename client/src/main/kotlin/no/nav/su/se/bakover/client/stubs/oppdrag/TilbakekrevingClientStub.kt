package no.nav.su.se.bakover.client.stubs.oppdrag

import arrow.core.Either
import arrow.core.right
import no.nav.okonomi.tilbakekrevingservice.TilbakekrevingsvedtakResponse
import no.nav.su.se.bakover.client.oppdrag.tilbakekreving.TilbakekrevingSoapClientMapper
import no.nav.su.se.bakover.client.oppdrag.tilbakekreving.mapToTilbakekrevingsvedtakRequest
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.RåTilbakekrevingsvedtakForsendelse
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.TilbakekrevingClient
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.Tilbakekrevingsvedtak
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.TilbakekrevingsvedtakForsendelseFeil
import java.time.Clock

data class TilbakekrevingClientStub(
    val clock: Clock,
) : TilbakekrevingClient {
    override fun sendTilbakekrevingsvedtak(tilbakekrevingsvedtak: Tilbakekrevingsvedtak): Either<TilbakekrevingsvedtakForsendelseFeil, RåTilbakekrevingsvedtakForsendelse> {
        return mapToTilbakekrevingsvedtakRequest(tilbakekrevingsvedtak).let { request ->
            TilbakekrevingSoapClientMapper.toXml(request)

            RåTilbakekrevingsvedtakForsendelse(
                requestXml = TilbakekrevingSoapClientMapper.toXml(request),
                tidspunkt = Tidspunkt.now(clock),
                responseXml = TilbakekrevingSoapClientMapper.toXml(response()),
            )
        }.right()
    }

    private fun response(): TilbakekrevingsvedtakResponse {
        return TilbakekrevingSoapClientMapper.fromXml(dummyOkXml)
    }

    private val dummyOkXml = """
        <?xml version="1.0" encoding="UTF-8"?>
              <tilbakekrevingsvedtakResponse xmlns="http://okonomi.nav.no/tilbakekrevingService/">
                 <mmel xmlns="">
                    <systemId xmlns="urn:no:nav:tilbakekreving:typer:v1"></systemId>
                    <kodeMelding xmlns="urn:no:nav:tilbakekreving:typer:v1"></kodeMelding>
                    <alvorlighetsgrad xmlns="urn:no:nav:tilbakekreving:typer:v1">00</alvorlighetsgrad>
                    <beskrMelding xmlns="urn:no:nav:tilbakekreving:typer:v1"></beskrMelding>
                    <sqlKode xmlns="urn:no:nav:tilbakekreving:typer:v1"/>
                    <sqlState xmlns="urn:no:nav:tilbakekreving:typer:v1"/>
                    <sqlMelding xmlns="urn:no:nav:tilbakekreving:typer:v1"/>
                    <mqCompletionKode xmlns="urn:no:nav:tilbakekreving:typer:v1"/>
                    <mqReasonKode xmlns="urn:no:nav:tilbakekreving:typer:v1"/>
                    <programId xmlns="urn:no:nav:tilbakekreving:typer:v1"></programId>
                    <sectionNavn xmlns="urn:no:nav:tilbakekreving:typer:v1"></sectionNavn>
                 </mmel>
                 <tilbakekrevingsvedtak xmlns="">
                    <kodeAksjon xmlns="urn:no:nav:tilbakekreving:tilbakekrevingsvedtak:vedtak:v1"></kodeAksjon>
                    <vedtakId xmlns="urn:no:nav:tilbakekreving:tilbakekrevingsvedtak:vedtak:v1"></vedtakId>
                    <kodeHjemmel xmlns="urn:no:nav:tilbakekreving:tilbakekrevingsvedtak:vedtak:v1"/>
                    <enhetAnsvarlig xmlns="urn:no:nav:tilbakekreving:tilbakekrevingsvedtak:vedtak:v1"/>
                    <kontrollfelt xmlns="urn:no:nav:tilbakekreving:tilbakekrevingsvedtak:vedtak:v1"/>
                    <saksbehId xmlns="urn:no:nav:tilbakekreving:tilbakekrevingsvedtak:vedtak:v1"/>
                 </tilbakekrevingsvedtak>
              </tilbakekrevingsvedtakResponse>
    """.trimIndent()
}
