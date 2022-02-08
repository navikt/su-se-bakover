package no.nav.su.se.bakover.web.services.tilbakekreving

import arrow.core.right
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

internal class TilbakekrevingXmlMapperTest {

    @Test
    fun `mapper nyopprettet kravgrunnlag for opphør av ytelse`() {
        val inputXml = """
            <?xml version="1.0" encoding="utf-8"?>
            <urn:detaljertKravgrunnlagMelding xmlns:mmel="urn:no:nav:tilbakekreving:typer:v1"
                                              xmlns:urn="urn:no:nav:tilbakekreving:kravgrunnlag:detalj:v1">
                <urn:detaljertKravgrunnlag>
                    <urn:kravgrunnlagId>298604</urn:kravgrunnlagId>
                    <urn:vedtakId>436204</urn:vedtakId>
                    <urn:kodeStatusKrav>NY</urn:kodeStatusKrav>
                    <urn:kodeFagomraade>SUUFORE</urn:kodeFagomraade>
                    <urn:fagsystemId>2461</urn:fagsystemId>
                    <urn:vedtakIdOmgjort>0</urn:vedtakIdOmgjort>
                    <urn:vedtakGjelderId>25077622783</urn:vedtakGjelderId>
                    <urn:typeGjelderId>PERSON</urn:typeGjelderId>
                    <urn:utbetalesTilId>25077622783</urn:utbetalesTilId>
                    <urn:typeUtbetId>PERSON</urn:typeUtbetId>
                    <urn:enhetAnsvarlig>8020</urn:enhetAnsvarlig>
                    <urn:enhetBosted>8020</urn:enhetBosted>
                    <urn:enhetBehandl>8020</urn:enhetBehandl>
                    <urn:kontrollfelt>2022-02-07-18.39.46.586953</urn:kontrollfelt>
                    <urn:saksbehId>K231B433</urn:saksbehId>
                    <urn:tilbakekrevingsPeriode>
                        <urn:periode>
                            <mmel:fom>2021-10-01</mmel:fom>
                            <mmel:tom>2021-10-31</mmel:tom>
                        </urn:periode>
                        <urn:belopSkattMnd>4395.00</urn:belopSkattMnd>
                        <urn:tilbakekrevingsBelop>
                            <urn:kodeKlasse>KL_KODE_FEIL_INNT</urn:kodeKlasse>
                            <urn:typeKlasse>FEIL</urn:typeKlasse>
                            <urn:belopOpprUtbet>0.00</urn:belopOpprUtbet>
                            <urn:belopNy>9989.00</urn:belopNy>
                            <urn:belopTilbakekreves>0.00</urn:belopTilbakekreves>
                            <urn:belopUinnkrevd>0.00</urn:belopUinnkrevd>
                            <urn:skattProsent>0.0000</urn:skattProsent>
                        </urn:tilbakekrevingsBelop>
                        <urn:tilbakekrevingsBelop>
                            <urn:kodeKlasse>SUUFORE</urn:kodeKlasse>
                            <urn:typeKlasse>YTEL</urn:typeKlasse>
                            <urn:belopOpprUtbet>9989.00</urn:belopOpprUtbet>
                            <urn:belopNy>0.00</urn:belopNy>
                            <urn:belopTilbakekreves>9989.00</urn:belopTilbakekreves>
                            <urn:belopUinnkrevd>0.00</urn:belopUinnkrevd>
                            <urn:skattProsent>43.9983</urn:skattProsent>
                        </urn:tilbakekrevingsBelop>
                    </urn:tilbakekrevingsPeriode>
                </urn:detaljertKravgrunnlag>
            </urn:detaljertKravgrunnlagMelding>
        """.trimIndent()
        TilbakekrevingXmlMapper.toDto(inputXml) shouldBe KravmeldingDto(
            kravgrunnlagId = "298604",
            vedtakId = "436204",
            kodeStatusKrav = "NY",
            kodeFagområde = "SUUFORE",
            fagsystemId = "2461",
            datoVedtakFagsystem = null,
            vedtakIdOmgjort = "0",
            vedtakGjelderId = "25077622783",
            idTypeGjelder = "PERSON",
            utbetalesTilId = "25077622783",
            idTypeUtbet = "PERSON",
            kodeHjemmel = null,
            renterBeregnes = null,
            enhetAnsvarlig = "8020",
            enhetBosted = "8020",
            enhetBehandl = "8020",
            kontrollfelt = "2022-02-07-18.39.46.586953",
            saksbehId = "K231B433",
            tilbakekrevingsperioder = listOf(
                KravmeldingDto.Tilbakekrevingsperiode(
                    periode = KravmeldingDto.Tilbakekrevingsperiode.Periode(
                        fraOgMed = "2021-10-01",
                        tilOgMed = "2021-10-31",
                    ),
                    skattebeløpPerMåned = "4395.00",
                    tilbakekrevingsbeløp = listOf(
                        KravmeldingDto.Tilbakekrevingsperiode.Tilbakekrevingsbeløp(
                            kodeKlasse = "KL_KODE_FEIL_INNT",
                            typeKlasse = "FEIL",
                            belopOpprUtbet = "0.00",
                            belopNy = "9989.00",
                            belopTilbakekreves = "0.00",
                            belopUinnkrevd = "0.00",
                            skattProsent = "0.0000",
                        ),
                        KravmeldingDto.Tilbakekrevingsperiode.Tilbakekrevingsbeløp(
                            kodeKlasse = "SUUFORE",
                            typeKlasse = "YTEL",
                            belopOpprUtbet = "9989.00",
                            belopNy = "0.00",
                            belopTilbakekreves = "9989.00",
                            belopUinnkrevd = "0.00",
                            skattProsent = "43.9983",
                        ),
                    ),
                ),
            ),
        ).right()
    }

    @Test
    fun `mapper nyopprettet kravgrunnlag for endring av ytelse`() {
        val inputXml = """
            <?xml version="1.0" encoding="utf-8"?>
            <urn:detaljertKravgrunnlagMelding xmlns:mmel="urn:no:nav:tilbakekreving:typer:v1"
                                              xmlns:urn="urn:no:nav:tilbakekreving:kravgrunnlag:detalj:v1">
                <urn:detaljertKravgrunnlag>
                    <urn:kravgrunnlagId>298606</urn:kravgrunnlagId>
                    <urn:vedtakId>436206</urn:vedtakId>
                    <urn:kodeStatusKrav>NY</urn:kodeStatusKrav>
                    <urn:kodeFagomraade>SUUFORE</urn:kodeFagomraade>
                    <urn:fagsystemId>2463</urn:fagsystemId>
                    <urn:vedtakIdOmgjort>0</urn:vedtakIdOmgjort>
                    <urn:vedtakGjelderId>18108619852</urn:vedtakGjelderId>
                    <urn:typeGjelderId>PERSON</urn:typeGjelderId>
                    <urn:utbetalesTilId>18108619852</urn:utbetalesTilId>
                    <urn:typeUtbetId>PERSON</urn:typeUtbetId>
                    <urn:enhetAnsvarlig>8020</urn:enhetAnsvarlig>
                    <urn:enhetBosted>8020</urn:enhetBosted>
                    <urn:enhetBehandl>8020</urn:enhetBehandl>
                    <urn:kontrollfelt>2022-02-07-18.39.47.693011</urn:kontrollfelt>
                    <urn:saksbehId>K231B433</urn:saksbehId>
                    <urn:tilbakekrevingsPeriode>
                        <urn:periode>
                            <mmel:fom>2021-10-01</mmel:fom>
                            <mmel:tom>2021-10-31</mmel:tom>
                        </urn:periode>
                        <urn:belopSkattMnd>5280.00</urn:belopSkattMnd>
                        <urn:tilbakekrevingsBelop>
                            <urn:kodeKlasse>KL_KODE_FEIL_INNT</urn:kodeKlasse>
                            <urn:typeKlasse>FEIL</urn:typeKlasse>
                            <urn:belopOpprUtbet>0.00</urn:belopOpprUtbet>
                            <urn:belopNy>12000.00</urn:belopNy>
                            <urn:belopTilbakekreves>0.00</urn:belopTilbakekreves>
                            <urn:belopUinnkrevd>0.00</urn:belopUinnkrevd>
                            <urn:skattProsent>0.0000</urn:skattProsent>
                        </urn:tilbakekrevingsBelop>
                        <urn:tilbakekrevingsBelop>
                            <urn:kodeKlasse>SUUFORE</urn:kodeKlasse>
                            <urn:typeKlasse>YTEL</urn:typeKlasse>
                            <urn:belopOpprUtbet>21989.00</urn:belopOpprUtbet>
                            <urn:belopNy>9989.00</urn:belopNy>
                            <urn:belopTilbakekreves>12000.00</urn:belopTilbakekreves>
                            <urn:belopUinnkrevd>0.00</urn:belopUinnkrevd>
                            <urn:skattProsent>43.9992</urn:skattProsent>
                        </urn:tilbakekrevingsBelop>
                    </urn:tilbakekrevingsPeriode>
                    <urn:tilbakekrevingsPeriode>
                        <urn:periode>
                            <mmel:fom>2021-11-01</mmel:fom>
                            <mmel:tom>2021-11-30</mmel:tom>
                        </urn:periode>
                        <urn:belopSkattMnd>5280.00</urn:belopSkattMnd>
                        <urn:tilbakekrevingsBelop>
                            <urn:kodeKlasse>KL_KODE_FEIL_INNT</urn:kodeKlasse>
                            <urn:typeKlasse>FEIL</urn:typeKlasse>
                            <urn:belopOpprUtbet>0.00</urn:belopOpprUtbet>
                            <urn:belopNy>12000.00</urn:belopNy>
                            <urn:belopTilbakekreves>0.00</urn:belopTilbakekreves>
                            <urn:belopUinnkrevd>0.00</urn:belopUinnkrevd>
                            <urn:skattProsent>0.0000</urn:skattProsent>
                        </urn:tilbakekrevingsBelop>
                        <urn:tilbakekrevingsBelop>
                            <urn:kodeKlasse>SUUFORE</urn:kodeKlasse>
                            <urn:typeKlasse>YTEL</urn:typeKlasse>
                            <urn:belopOpprUtbet>21989.00</urn:belopOpprUtbet>
                            <urn:belopNy>9989.00</urn:belopNy>
                            <urn:belopTilbakekreves>12000.00</urn:belopTilbakekreves>
                            <urn:belopUinnkrevd>0.00</urn:belopUinnkrevd>
                            <urn:skattProsent>43.9992</urn:skattProsent>
                        </urn:tilbakekrevingsBelop>
                    </urn:tilbakekrevingsPeriode>
                </urn:detaljertKravgrunnlag>
            </urn:detaljertKravgrunnlagMelding>
        """.trimIndent()

        TilbakekrevingXmlMapper.toDto(inputXml) shouldBe KravmeldingDto(
            kravgrunnlagId = "298606",
            vedtakId = "436206",
            kodeStatusKrav = "NY",
            kodeFagområde = "SUUFORE",
            fagsystemId = "2463",
            datoVedtakFagsystem = null,
            vedtakIdOmgjort = "0",
            vedtakGjelderId = "18108619852",
            idTypeGjelder = "PERSON",
            utbetalesTilId = "18108619852",
            idTypeUtbet = "PERSON",
            kodeHjemmel = null,
            renterBeregnes = null,
            enhetAnsvarlig = "8020",
            enhetBosted = "8020",
            enhetBehandl = "8020",
            kontrollfelt = "2022-02-07-18.39.47.693011",
            saksbehId = "K231B433",
            tilbakekrevingsperioder = listOf(
                KravmeldingDto.Tilbakekrevingsperiode(
                    periode = KravmeldingDto.Tilbakekrevingsperiode.Periode(
                        fraOgMed = "2021-10-01",
                        tilOgMed = "2021-10-31",
                    ),
                    skattebeløpPerMåned = "5280.00",
                    tilbakekrevingsbeløp = listOf(
                        KravmeldingDto.Tilbakekrevingsperiode.Tilbakekrevingsbeløp(
                            kodeKlasse = "KL_KODE_FEIL_INNT",
                            typeKlasse = "FEIL",
                            belopOpprUtbet = "0.00",
                            belopNy = "12000.00",
                            belopTilbakekreves = "0.00",
                            belopUinnkrevd = "0.00",
                            skattProsent = "0.0000",
                        ),
                        KravmeldingDto.Tilbakekrevingsperiode.Tilbakekrevingsbeløp(
                            kodeKlasse = "SUUFORE",
                            typeKlasse = "YTEL",
                            belopOpprUtbet = "21989.00",
                            belopNy = "9989.00",
                            belopTilbakekreves = "12000.00",
                            belopUinnkrevd = "0.00",
                            skattProsent = "43.9992",
                        ),
                    ),
                ),
                KravmeldingDto.Tilbakekrevingsperiode(
                    periode = KravmeldingDto.Tilbakekrevingsperiode.Periode(
                        fraOgMed = "2021-11-01",
                        tilOgMed = "2021-11-30",
                    ),
                    skattebeløpPerMåned = "5280.00",
                    tilbakekrevingsbeløp = listOf(
                        KravmeldingDto.Tilbakekrevingsperiode.Tilbakekrevingsbeløp(
                            kodeKlasse = "KL_KODE_FEIL_INNT",
                            typeKlasse = "FEIL",
                            belopOpprUtbet = "0.00",
                            belopNy = "12000.00",
                            belopTilbakekreves = "0.00",
                            belopUinnkrevd = "0.00",
                            skattProsent = "0.0000",
                        ),
                        KravmeldingDto.Tilbakekrevingsperiode.Tilbakekrevingsbeløp(
                            kodeKlasse = "SUUFORE",
                            typeKlasse = "YTEL",
                            belopOpprUtbet = "21989.00",
                            belopNy = "9989.00",
                            belopTilbakekreves = "12000.00",
                            belopUinnkrevd = "0.00",
                            skattProsent = "43.9992",
                        ),
                    ),
                ),
            ),
        ).right()
    }
}
