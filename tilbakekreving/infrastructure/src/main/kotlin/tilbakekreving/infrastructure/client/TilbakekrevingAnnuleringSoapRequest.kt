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
    <ns1:kravgrunnlagAnnulerRequest xmlns:ns1=“http://okonomi.nav.no/tilbakekrevingService/”  xmlns:ns2=“urn:no:nav:tilbakekreving:kravgrunnlag:annuller:v1">
        <ns1:annullerkravgrunnlag>
            <ns2:kodeAksjon>A</ns2:kodeAksjon>
            <ns2:vedtakId>$eksternVedtakId</ns2:vedtakId>
            <ns2:saksbehId>$saksbehandletAv</ns2:saksbehId>
        </ns1:annullerkravgrunnlag>
    </ns1:kravgrunnlagAnnulerRequest>
    """.trimIndent()
}
