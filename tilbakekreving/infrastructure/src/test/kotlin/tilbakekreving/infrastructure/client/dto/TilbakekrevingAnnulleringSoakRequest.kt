package tilbakekreving.infrastructure.client.dto

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tilbakekreving.infrastructure.client.buildTilbakekrevingAnnulleringSoapRequest

class TilbakekrevingAnnulleringSoakRequest {

    @Test
    fun `lager soap body`() {
        val eksternVedtakId = "123"
        val saksbehandletAv = "saksbehandletAv"

        buildTilbakekrevingAnnulleringSoapRequest(
            eksternVedtakId,
            saksbehandletAv,
        ) shouldBe """
            <ns4:tilbakekrevingsvedtakRequest xmlns:ns2="urn:no:nav:tilbakekreving:typer:v1"
                                              xmlns:ns4="http://okonomi.nav.no/tilbakekrevingService/"
                                              xmlns:ns3="urn:no:nav:tilbakekreving:tilbakekrevingsvedtak:vedtak:v1">
              <tilbakekrevingsvedtak>
                <ns3:kodeAksjon>A</ns3:kodeAksjon>
                <ns3:vedtakId>$eksternVedtakId</ns3:vedtakId>
                <ns3:saksbehId>$saksbehandletAv</ns3:saksbehId>
              </tilbakekrevingsvedtak>
            </ns4:tilbakekrevingsvedtakRequest>
        """.trimIndent()
    }
}
