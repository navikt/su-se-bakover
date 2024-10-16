@file:Suppress("HttpUrlsUsage")

package tilbakekreving.infrastructure.client

/**
 * https://confluence.adeo.no/display/OKSY/Detaljer+om+de+enkelte+ID-koder
 * https://confluence.adeo.no/display/OKSY/Detaljer+om+de+enkelte+ID-koder?preview=/178067795/178067800/worddav1549728a4f1bb4ae0651e7017a7cae86.png
 */
internal fun buildTilbakekrevingAnnulleringSoapRequest(
    eksternVedtakId: String,
    saksbehandledAv: String,
): String {
    return """
<ns4:tilbakekrevingsvedtakRequest xmlns:ns2="urn:no:nav:tilbakekreving:typer:v1"
                                  xmlns:ns4="http://okonomi.nav.no/tilbakekrevingService/"
                                  xmlns:ns3="urn:no:nav:tilbakekreving:tilbakekrevingsvedtak:vedtak:v1">
  <tilbakekrevingsvedtak>
    <ns3:kodeAksjon>A</ns3:kodeAksjon>
    <ns3:vedtakId>$eksternVedtakId</ns3:vedtakId>
    <ns3:saksbehId>$saksbehandledAv</ns3:saksbehId>
  </tilbakekrevingsvedtak>
</ns4:tilbakekrevingsvedtakRequest>
    """.trimIndent()
}
