@file:Suppress("HttpUrlsUsage")

package tilbakekreving.infrastructure.client

/**
 * https://confluence.adeo.no/display/OKSY/Detaljer+om+de+enkelte+ID-koder
 * https://confluence.adeo.no/display/OKSY/Detaljer+om+de+enkelte+ID-koder?preview=/178067795/178067800/worddav1549728a4f1bb4ae0651e7017a7cae86.png
 */
internal fun buildTilbakekrevingAnnulleringSoapRequest(
    eksternVedtakId: String,
    saksbehandletAv: String,
): String {
    return """
    <ns1:annullerKravgrunnlagRequest xmlns:ns1="urn:no:nav:tilbakekreving:kravgrunnlag:annuller:v1">
        <ns1:annullerKravgrunnlag>
            <ns1:kodeAksjon>A</ns1:kodeAksjon>
            <ns1:vedtakId>$eksternVedtakId</ns1:vedtakId>
            <ns1:saksbehId>$saksbehandletAv</ns1:saksbehId>
        </ns1:annullerKravgrunnlag>
    </ns1:annullerKravgrunnlagRequest>
    """.trimIndent()
}
