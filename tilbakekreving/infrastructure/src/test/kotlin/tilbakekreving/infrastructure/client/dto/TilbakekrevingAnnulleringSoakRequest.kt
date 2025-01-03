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
    <ns1:annullerKravgrunnlagRequest xmlns:ns1="urn:no:nav:tilbakekreving:kravgrunnlag:annuller:v1">
        <ns1:annullerKravgrunnlag>
            <ns1:kodeAksjon>A</ns1:kodeAksjon>
            <ns1:vedtakId>$eksternVedtakId</ns1:vedtakId>
            <ns1:saksbehId>$saksbehandletAv</ns1:saksbehId>
        </ns1:annullerKravgrunnlag>
    </ns1:annullerKravgrunnlagRequest>
        """.trimIndent()
    }
}
