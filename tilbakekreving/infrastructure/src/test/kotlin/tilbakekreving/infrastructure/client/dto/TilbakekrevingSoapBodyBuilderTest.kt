package tilbakekreving.infrastructure.client.dto

import arrow.core.nonEmptyListOf
import no.nav.su.se.bakover.test.attestant
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.kravgrunnlag.grunnlagsperiode
import no.nav.su.se.bakover.test.kravgrunnlag.kravgrunnlag
import no.nav.su.se.bakover.test.nyVurderinger
import no.nav.su.se.bakover.test.vurderingerMedKrav
import no.nav.su.se.bakover.test.xml.shouldBeSimilarXmlTo
import org.junit.jupiter.api.Test
import tilbakekreving.domain.kravgrunnlag.Kravgrunnlag
import tilbakekreving.domain.vurdering.Vurdering
import tilbakekreving.domain.vurdering.Vurderinger
import tilbakekreving.domain.vurdering.VurderingerMedKrav
import tilbakekreving.infrastructure.client.buildTilbakekrevingSoapRequest
import økonomi.domain.Fagområde
import økonomi.domain.KlasseKode
import java.math.BigDecimal

internal class TilbakekrevingSoapBodyBuilderTest {
    @Test
    fun `kan mappe fra domenemodell til soap xml for ufore`() {
        buildTilbakekrevingSoapRequest(
            vurderingerMedKrav = vurderingerMedKrav(),
            attestertAv = attestant,
            fagområde = Fagområde.SUUFORE,
        ).getOrFail().shouldBeSimilarXmlTo(
            expectedXml(
                ytelseKlassekode = Fagområde.SUUFORE.name,
                feilKlassekode = KlasseKode.KL_KODE_FEIL_INNT.name,
            ),
            true,
        )
    }

    @Test
    fun `kan mappe fra domenemodell til soap xml for alder`() {
        buildTilbakekrevingSoapRequest(
            vurderingerMedKrav = vurderingerMedKrav(),
            attestertAv = attestant,
            fagområde = Fagområde.SUALDER,
        ).getOrFail().shouldBeSimilarXmlTo(
            expectedXml(
                ytelseKlassekode = Fagområde.SUALDER.name,
                feilKlassekode = KlasseKode.KL_KODE_FEIL.name,
            ),
            true,
        )
    }

    @Test
    fun `sender trekk med samme vedtaksfelt som ytelse ved full tilbakekreving`() {
        buildTilbakekrevingSoapRequest(
            vurderingerMedKrav = vurderingerMedTrekk(Vurdering.SkalTilbakekreve),
            attestertAv = attestant,
            fagområde = Fagområde.SUUFORE,
        ).getOrFail().shouldBeSimilarXmlTo(
            expectedXmlMedTrekk(
                beløpTilbakekreves = "1000.00",
                beløpUinnkrevd = "0.00",
                beløpSkatt = "500.00",
                kodeResultat = Tilbakekrevingsresultat.FULL_TILBAKEKREV,
                kodeSkyld = Skyld.BRUKER,
            ),
            true,
        )
    }

    @Test
    fun `sender trekk med samme vedtaksfelt som ytelse når beløpet ikke tilbakekreves`() {
        buildTilbakekrevingSoapRequest(
            vurderingerMedKrav = vurderingerMedTrekk(Vurdering.SkalIkkeTilbakekreve),
            attestertAv = attestant,
            fagområde = Fagområde.SUUFORE,
        ).getOrFail().shouldBeSimilarXmlTo(
            expectedXmlMedTrekk(
                beløpTilbakekreves = "0.00",
                beløpUinnkrevd = "1000.00",
                beløpSkatt = "0.00",
                kodeResultat = Tilbakekrevingsresultat.INGEN_TILBAKEKREV,
                kodeSkyld = Skyld.IKKE_FORDELT,
            ),
            true,
        )
    }

    private fun vurderingerMedTrekk(vurdering: Vurdering): VurderingerMedKrav {
        val trekk = Kravgrunnlag.Grunnlagsperiode.Trekk(
            kodeKlasse = KlasseKode.KREDKRED.name,
            beløpOpprinnelig = -530,
            beløpNytt = 0,
            beløpTilbakekreves = 0,
            beløpUinnkrevd = 0,
            skatteProsent = BigDecimal.ZERO,
        )
        val kravgrunnlag = kravgrunnlag(
            grunnlagsperioder = nonEmptyListOf(
                grunnlagsperiode(
                    bruttoTidligereUtbetalt = 2000,
                    bruttoNyUtbetaling = 470,
                    bruttoFeilutbetaling = 1000,
                    trekk = listOf(trekk),
                ),
            ),
        )
        val vurderinger = nyVurderinger(
            perioderVurderinger = nonEmptyListOf(
                Vurderinger.Periodevurdering(
                    periode = kravgrunnlag.grunnlagsperioder.single().periode,
                    vurdering = vurdering,
                ),
            ),
        )
        return VurderingerMedKrav.utledFra(vurderinger, kravgrunnlag).getOrFail()
    }

    private fun expectedXmlMedTrekk(
        beløpTilbakekreves: String,
        beløpUinnkrevd: String,
        beløpSkatt: String,
        kodeResultat: Tilbakekrevingsresultat,
        kodeSkyld: Skyld,
    ): String {
        return """
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
        <ns3:belopNy>470.00</ns3:belopNy>
        <ns3:belopTilbakekreves>$beløpTilbakekreves</ns3:belopTilbakekreves>
        <ns3:belopUinnkrevd>$beløpUinnkrevd</ns3:belopUinnkrevd>
        <ns3:belopSkatt>$beløpSkatt</ns3:belopSkatt>
        <ns3:kodeResultat>$kodeResultat</ns3:kodeResultat>
        <ns3:kodeAarsak>ANNET</ns3:kodeAarsak>
        <ns3:kodeSkyld>$kodeSkyld</ns3:kodeSkyld>
      </ns3:tilbakekrevingsbelop>
      <ns3:tilbakekrevingsbelop>
        <ns3:kodeKlasse>KREDKRED</ns3:kodeKlasse>
        <ns3:belopOpprUtbet>-530.00</ns3:belopOpprUtbet>
        <ns3:belopNy>0.00</ns3:belopNy>
        <ns3:belopTilbakekreves>0.00</ns3:belopTilbakekreves>
        <ns3:belopUinnkrevd>0.00</ns3:belopUinnkrevd>
        <ns3:belopSkatt>$beløpSkatt</ns3:belopSkatt>
        <ns3:kodeResultat>$kodeResultat</ns3:kodeResultat>
        <ns3:kodeAarsak>ANNET</ns3:kodeAarsak>
        <ns3:kodeSkyld>$kodeSkyld</ns3:kodeSkyld>
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
    }

    private fun expectedXml(
        ytelseKlassekode: String,
        feilKlassekode: String,
    ): String {
        return """
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
        <ns3:kodeKlasse>$ytelseKlassekode</ns3:kodeKlasse>
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
        <ns3:kodeKlasse>$feilKlassekode</ns3:kodeKlasse>
        <ns3:belopOpprUtbet>0.00</ns3:belopOpprUtbet>
        <ns3:belopNy>1000.00</ns3:belopNy>
        <ns3:belopTilbakekreves>0.00</ns3:belopTilbakekreves>
        <ns3:belopUinnkrevd>0.00</ns3:belopUinnkrevd>
      </ns3:tilbakekrevingsbelop>
    </ns3:tilbakekrevingsperiode>
  </tilbakekrevingsvedtak>
</ns4:tilbakekrevingsvedtakRequest>
        """.trimIndent()
    }
}
