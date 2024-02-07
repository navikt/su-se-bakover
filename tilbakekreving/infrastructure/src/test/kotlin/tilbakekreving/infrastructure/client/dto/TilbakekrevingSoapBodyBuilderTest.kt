package tilbakekreving.infrastructure.client.dto

import no.nav.su.se.bakover.test.attestant
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.vurderingerMedKrav
import no.nav.su.se.bakover.test.xml.shouldBeSimilarXmlTo
import org.junit.jupiter.api.Test
import tilbakekreving.infrastructure.client.buildTilbakekrevingSoapRequest

internal class TilbakekrevingSoapBodyBuilderTest {
    @Test
    fun `kan mappe fra domenemodell til soap xml`() {
        val vurderingerMedKrav = vurderingerMedKrav()
        val expected = """
<ns4:tilbakekrevingsvedtakRequest xmlns:ns4="http://okonomi.nav.no/tilbakekrevingService/" xmlns:ns2="urn:no:nav:tilbakekreving:typer:v1" xmlns:ns3="urn:no:nav:tilbakekreving:tilbakekrevingsvedtak:vedtak:v1">
  <tilbakekrevingsvedtak>
    <ns3:kodeAksjon>8</ns3:kodeAksjon>
    <ns3:vedtakId>789-101</ns3:vedtakId>
    <ns3:kodeHjemmel>SUL_13</ns3:kodeHjemmel>
    <ns3:renterBeregnes>N</ns3:renterBeregnes>
    <ns3:enhetAnsvarlig>8020</ns3:enhetAnsvarlig>
    <ns3:kontrollfelt>2021-01-01-02.02.03.456789</ns3:kontrollfelt>
    <ns3:saksbehId>attestant</ns3:saksbehId>
    <ns3:tilbakekrevingsperiode>
      <ns3:periode>
        <ns2:fom>2021-01-01</ns2:fom>
        <ns2:tom>2021-01-31</ns2:tom>
      </ns3:periode>
      <ns3:renterBeregnes>N</ns3:renterBeregnes>
      <ns3:belopRenter>0.00</ns3:belopRenter>
      <ns3:tilbakekrevingsbelop>
        <ns3:kodeKlasse>SUUFORE</ns3:kodeKlasse>
        <ns3:belopOpprUtbet>2000.00</ns3:belopOpprUtbet>
        <ns3:belopNy>1000.00</ns3:belopNy>
        <ns3:belopTilbakekreves>1000.00</ns3:belopTilbakekreves>
        <ns3:belopUinnkrevd>0.00</ns3:belopUinnkrevd>
        <ns3:belopSkatt>500.00</ns3:belopSkatt>
        <ns3:kodeResultat>FULL_TILBAKEKREV</ns3:kodeResultat>
        <ns3:kodeAarsak>ANNET</ns3:kodeAarsak>
        <ns3:kodeSkyld>BRUKER</ns3:kodeSkyld>
      </ns3:tilbakekrevingsbelop>
      <ns3:tilbakekrevingsbelop>
        <ns3:kodeKlasse>KL_KODE_FEIL_INNT</ns3:kodeKlasse>
        <ns3:belopOpprUtbet>0.00</ns3:belopOpprUtbet>
        <ns3:belopNy>1000.00</ns3:belopNy>
        <ns3:belopTilbakekreves>0.00</ns3:belopTilbakekreves>
        <ns3:belopUinnkrevd>0.00</ns3:belopUinnkrevd>
      </ns3:tilbakekrevingsbelop>
    </ns3:tilbakekrevingsperiode>
  </tilbakekrevingsvedtak>
</ns4:tilbakekrevingsvedtakRequest>
        """.trimIndent()
        buildTilbakekrevingSoapRequest(
            vurderingerMedKrav = vurderingerMedKrav,
            attestertAv = attestant,
        ).getOrFail().shouldBeSimilarXmlTo(expected, true)
    }
}
