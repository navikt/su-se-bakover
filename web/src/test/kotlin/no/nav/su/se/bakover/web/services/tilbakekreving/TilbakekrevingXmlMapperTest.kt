package no.nav.su.se.bakover.web.services.tilbakekreving

import arrow.core.right
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

internal class TilbakekrevingXmlMapperTest {

    @Test
    fun `eksempel fra Emil 1`() {
        // TODO jah: XMLen er lånt fra Permittering i preprod - bytt ut med data som passer bedre med supstonad (vi vet mer etter vi har fått første melding på køen)
        val inputXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <urn:detaljertKravgrunnlagMelding xmlns:urn="urn:no:nav:tilbakekreving:kravgrunnlag:detalj:v1" xmlns:mmel="urn:no:nav:tilbakekreving:typer:v1">
              <urn:detaljertKravgrunnlag>
                <urn:kravgrunnlagId>250227</urn:kravgrunnlagId>
                <urn:vedtakId>368827</urn:vedtakId>
                <urn:kodeStatusKrav>NY</urn:kodeStatusKrav>
                <urn:kodeFagomraade>SUUFORE</urn:kodeFagomraade>
                <urn:fagsystemId>PE28026748277_973404202</urn:fagsystemId>
                <urn:vedtakIdOmgjort>0</urn:vedtakIdOmgjort>
                <urn:vedtakGjelderId>12345678910</urn:vedtakGjelderId>
                <urn:typeGjelderId>PERSON</urn:typeGjelderId>
                <urn:utbetalesTilId>12345678910</urn:utbetalesTilId>
                <urn:typeUtbetId>PERSON</urn:typeUtbetId>
                <urn:enhetAnsvarlig>8020</urn:enhetAnsvarlig>
                <urn:enhetBosted>8020</urn:enhetBosted>
                <urn:enhetBehandl>8020</urn:enhetBehandl>
                <urn:kontrollfelt>2021-07-14-10.54.31.820373</urn:kontrollfelt>
                <urn:saksbehId>K231B433</urn:saksbehId>
                <urn:referanse>01F8Z2D20PM3R319YXYSV92EE6</urn:referanse>
                <urn:tilbakekrevingsPeriode>
                  <urn:periode>
                    <mmel:fom>2020-03-25</mmel:fom>
                    <mmel:tom>2020-04-13</mmel:tom>
                  </urn:periode>
                  <urn:belopSkattMnd>1027.00</urn:belopSkattMnd>
                  <urn:tilbakekrevingsBelop>
                    <urn:kodeKlasse>KL_KODE_FEIL_INNT</urn:kodeKlasse>
                    <urn:typeKlasse>FEIL</urn:typeKlasse>
                    <urn:belopOpprUtbet>0.00</urn:belopOpprUtbet>
                    <urn:belopNy>2282.00</urn:belopNy>
                    <urn:belopTilbakekreves>0.00</urn:belopTilbakekreves>
                    <urn:belopUinnkrevd>0.00</urn:belopUinnkrevd>
                    <urn:skattProsent>0.0000</urn:skattProsent>
                  </urn:tilbakekrevingsBelop>
                  <urn:tilbakekrevingsBelop>
                    <urn:kodeKlasse>SUUFORE</urn:kodeKlasse>
                    <urn:typeKlasse>YTEL</urn:typeKlasse>
                    <urn:belopOpprUtbet>15209.00</urn:belopOpprUtbet>
                    <urn:belopNy>12927.00</urn:belopNy>
                    <urn:belopTilbakekreves>2282.00</urn:belopTilbakekreves>
                    <urn:belopUinnkrevd>0.00</urn:belopUinnkrevd>
                    <urn:skattProsent>44.9996</urn:skattProsent>
                  </urn:tilbakekrevingsBelop>
                </urn:tilbakekrevingsPeriode>
              </urn:detaljertKravgrunnlag>
            </urn:detaljertKravgrunnlagMelding>
        """.trimIndent()
        TilbakekrevingXmlMapper.toDto(inputXml) shouldBe KravmeldingDto(
            kravgrunnlagId = "250227",
            vedtakId = "368827",
            kodeStatusKrav = "NY",
            kodeFagområde = "SUUFORE",
            fagsystemId = "PE28026748277_973404202",
            datoVedtakFagsystem = null,
            vedtakIdOmgjort = "0",
            vedtakGjelderId = "12345678910",
            idTypeGjelder = "PERSON",
            utbetalesTilId = "12345678910",
            idTypeUtbet = "PERSON",
            kodeHjemmel = null,
            renterBeregnes = null,
            enhetAnsvarlig = "8020",
            enhetBosted = "8020",
            enhetBehandl = "8020",
            kontrollfelt = "2021-07-14-10.54.31.820373",
            saksbehId = "K231B433",
            referanse = "01F8Z2D20PM3R319YXYSV92EE6",
            tilbakekrevingsperioder = listOf(
                KravmeldingDto.Tilbakekrevingsperiode(
                    periode = KravmeldingDto.Tilbakekrevingsperiode.Periode(
                        fraOgMed = "2020-03-25",
                        tilOgMed = "2020-04-13",
                    ),
                    skattebeløpPerMåned = "1027.00",
                    tilbakekrevingsbeløp = listOf(
                        KravmeldingDto.Tilbakekrevingsperiode.Tilbakekrevingsbeløp(
                            kodeKlasse = "KL_KODE_FEIL_INNT",
                            typeKlasse = "FEIL",
                            belopOpprUtbet = "0.00",
                            belopNy = "2282.00",
                            belopTilbakekreves = "0.00",
                            belopUinnkrevd = "0.00",
                            skattProsent = "0.0000",
                        ),
                        KravmeldingDto.Tilbakekrevingsperiode.Tilbakekrevingsbeløp(
                            kodeKlasse = "SUUFORE",
                            typeKlasse = "YTEL",
                            belopOpprUtbet = "15209.00",
                            belopNy = "12927.00",
                            belopTilbakekreves = "2282.00",
                            belopUinnkrevd = "0.00",
                            skattProsent = "44.9996",
                        ),
                    ),

                ),
            ),
        ).right()
    }

    @Test
    fun `Eksempel fra Emil 2`() {
        // TODO jah: Fullfør test
        @Suppress("UNUSED_VARIABLE") val xmlInput = """
            <?xml version="1.0" encoding="UTF-8"?>
            <urn:detaljertKravgrunnlagMelding xmlns:urn="urn:no:nav:tilbakekreving:kravgrunnlag:detalj:v1" xmlns:mmel="urn:no:nav:tilbakekreving:typer:v1">
              <urn:detaljertKravgrunnlag>
                <urn:kravgrunnlagId>250241</urn:kravgrunnlagId>
                <urn:vedtakId>368841</urn:vedtakId>
                <urn:kodeStatusKrav>NY</urn:kodeStatusKrav>
                <urn:kodeFagomraade>SUUFORE</urn:kodeFagomraade>
                <urn:fagsystemId>PE03058432309_914985218</urn:fagsystemId>
                <urn:vedtakIdOmgjort>0</urn:vedtakIdOmgjort>
                <urn:vedtakGjelderId>12345678910</urn:vedtakGjelderId>
                <urn:typeGjelderId>PERSON</urn:typeGjelderId>
                <urn:utbetalesTilId>12345678910</urn:utbetalesTilId>
                <urn:typeUtbetId>PERSON</urn:typeUtbetId>
                <urn:enhetAnsvarlig>8020</urn:enhetAnsvarlig>
                <urn:enhetBosted>8020</urn:enhetBosted>
                <urn:enhetBehandl>8020</urn:enhetBehandl>
                <urn:kontrollfelt>2021-07-14-10.54.33.478908</urn:kontrollfelt>
                <urn:saksbehId>K231B433</urn:saksbehId>
                <urn:referanse>01F8YESK5VCV9KDXRXDQN027JB</urn:referanse>
                <urn:tilbakekrevingsPeriode>
                  <urn:periode>
                    <mmel:fom>2020-04-13</mmel:fom>
                    <mmel:tom>2020-05-04</mmel:tom>
                  </urn:periode>
                  <urn:belopSkattMnd>382.00</urn:belopSkattMnd>
                  <urn:tilbakekrevingsBelop>
                    <urn:kodeKlasse>KL_KODE_FEIL_INNT</urn:kodeKlasse>
                    <urn:typeKlasse>FEIL</urn:typeKlasse>
                    <urn:belopOpprUtbet>0.00</urn:belopOpprUtbet>
                    <urn:belopNy>1532.00</urn:belopNy>
                    <urn:belopTilbakekreves>0.00</urn:belopTilbakekreves>
                    <urn:belopUinnkrevd>0.00</urn:belopUinnkrevd>
                    <urn:skattProsent>0.0000</urn:skattProsent>
                  </urn:tilbakekrevingsBelop>
                  <urn:tilbakekrevingsBelop>
                    <urn:kodeKlasse>SUUFORE</urn:kodeKlasse>
                    <urn:typeKlasse>YTEL</urn:typeKlasse>
                    <urn:belopOpprUtbet>24513.00</urn:belopOpprUtbet>
                    <urn:belopNy>22981.00</urn:belopNy>
                    <urn:belopTilbakekreves>1532.00</urn:belopTilbakekreves>
                    <urn:belopUinnkrevd>0.00</urn:belopUinnkrevd>
                    <urn:skattProsent>24.9051</urn:skattProsent>
                  </urn:tilbakekrevingsBelop>
                </urn:tilbakekrevingsPeriode>
              </urn:detaljertKravgrunnlag>
            </urn:detaljertKravgrunnlagMelding>
        """.trimIndent()
    }

    @Test
    fun `Eksempel fra Emil 3`() {
        // TODO jah: Fullfør test
        @Suppress("UNUSED_VARIABLE") val xmlInput = """
            <?xml version="1.0" encoding="UTF-8"?>
            <urn:detaljertKravgrunnlagMelding xmlns:urn="urn:no:nav:tilbakekreving:kravgrunnlag:detalj:v1" xmlns:mmel="urn:no:nav:tilbakekreving:typer:v1">
              <urn:detaljertKravgrunnlag>
                <urn:kravgrunnlagId>8631869768112589191</urn:kravgrunnlagId>
                <urn:vedtakId>348005</urn:vedtakId>
                <urn:kodeStatusKrav>NY</urn:kodeStatusKrav>
                <urn:kodeFagomraade>SUUFORE</urn:kodeFagomraade>
                <urn:fagsystemId>PE01097802196_839942907</urn:fagsystemId>
                <urn:vedtakIdOmgjort>0</urn:vedtakIdOmgjort>
                <urn:vedtakGjelderId>12345678910</urn:vedtakGjelderId>
                <urn:typeGjelderId>PERSON</urn:typeGjelderId>
                <urn:utbetalesTilId>12345678910</urn:utbetalesTilId>
                <urn:typeUtbetId>PERSON</urn:typeUtbetId>
                <urn:enhetAnsvarlig>8020</urn:enhetAnsvarlig>
                <urn:enhetBosted>8020</urn:enhetBosted>
                <urn:enhetBehandl>8020</urn:enhetBehandl>
                <urn:kontrollfelt>2021-04-27-18.51.06.913218</urn:kontrollfelt>
                <urn:saksbehId>K231B433</urn:saksbehId>
                <urn:referanse>01F49912SX9SRRVGT0J5R4WYFR</urn:referanse>
                <urn:tilbakekrevingsPeriode>
                  <urn:periode>
                    <mmel:fom>2020-03-24</mmel:fom>
                    <mmel:tom>2020-04-16</mmel:tom>
                  </urn:periode>
                  <urn:belopSkattMnd>1584.00</urn:belopSkattMnd>
                  <urn:tilbakekrevingsBelop>
                    <urn:kodeKlasse>KL_KODE_FEIL_INNT</urn:kodeKlasse>
                    <urn:typeKlasse>FEIL</urn:typeKlasse>
                    <urn:belopOpprUtbet>0.00</urn:belopOpprUtbet>
                    <urn:belopNy>3600.00</urn:belopNy>
                    <urn:belopTilbakekreves>0.00</urn:belopTilbakekreves>
                    <urn:belopUinnkrevd>0.00</urn:belopUinnkrevd>
                    <urn:skattProsent>0.0000</urn:skattProsent>
                  </urn:tilbakekrevingsBelop>
                  <urn:tilbakekrevingsBelop>
                    <urn:kodeKlasse>SUUFORE</urn:kodeKlasse>
                    <urn:typeKlasse>YTEL</urn:typeKlasse>
                    <urn:belopOpprUtbet>18000.00</urn:belopOpprUtbet>
                    <urn:belopNy>14400.00</urn:belopNy>
                    <urn:belopTilbakekreves>3600.00</urn:belopTilbakekreves>
                    <urn:belopUinnkrevd>0.00</urn:belopUinnkrevd>
                    <urn:skattProsent>44.0000</urn:skattProsent>
                  </urn:tilbakekrevingsBelop>
                </urn:tilbakekrevingsPeriode>
              </urn:detaljertKravgrunnlag>
            </urn:detaljertKravgrunnlagMelding>
        """.trimIndent()
    }
}
