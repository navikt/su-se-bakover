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
    <ns1:kravgrunnlagAnnulerRequest xmlns:ns1=“http://okonomi.nav.no/tilbakekrevingService/”  xmlns:ns2=“urn:no:nav:tilbakekreving:kravgrunnlag:annuller:v1">
        <ns1:annullerkravgrunnlag>
            <ns2:kodeAksjon>A</ns2:kodeAksjon>
            <ns2:vedtakId>$eksternVedtakId</ns2:vedtakId>
            <ns2:saksbehId>$saksbehandletAv</ns2:saksbehId>
        </ns1:annullerkravgrunnlag>
    </ns1:kravgrunnlagAnnulerRequest>
        """.trimIndent()
    }
}
