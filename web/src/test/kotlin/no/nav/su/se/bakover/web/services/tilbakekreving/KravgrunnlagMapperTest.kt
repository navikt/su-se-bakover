package no.nav.su.se.bakover.web.services.tilbakekreving

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.november
import no.nav.su.se.bakover.common.oktober
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.oppdrag.simulering.KlasseKode
import no.nav.su.se.bakover.domain.oppdrag.simulering.KlasseType
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.Kravgrunnlag
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.RåttKravgrunnlag
import no.nav.su.se.bakover.test.getOrFail
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.math.RoundingMode

internal class KravgrunnlagMapperTest {

    @Test
    fun `mapper nyopprettet kravgrunnlag for opphør av ytelse`() {
        //language=XML
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
        val expected = KravmeldingDto(
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
        )

        KravgrunnlagMapper.toDto(inputXml).getOrFail() shouldBe expected
        KravgrunnlagMapper.toKravgrunnlg(RåttKravgrunnlag(inputXml)).getOrFail() shouldBe Kravgrunnlag(
            saksnummer = Saksnummer(2461),
            kravgrunnlagId = "298604",
            vedtakId = "436204",
            kontrollfelt = "2022-02-07-18.39.46.586953",
            status = Kravgrunnlag.KravgrunnlagStatus.NY,
            behandler = NavIdentBruker.Saksbehandler("K231B433"),
            grunnlagsperioder = listOf(
                Kravgrunnlag.Grunnlagsperiode(
                    periode = Periode.create(
                        fraOgMed = 1.oktober(2021),
                        tilOgMed = 31.oktober(2021),
                    ),
                    beløpSkattMnd = BigDecimal(4395).setScale(2),
                    grunnlagsbeløp = listOf(
                        Kravgrunnlag.Grunnlagsperiode.Grunnlagsbeløp(
                            kode = KlasseKode.KL_KODE_FEIL_INNT,
                            type = KlasseType.FEIL,
                            beløpTidligereUtbetaling = BigDecimal.ZERO.setScale(2),
                            beløpNyUtbetaling = BigDecimal(9989).setScale(2),
                            beløpSkalTilbakekreves = BigDecimal.ZERO.setScale(2),
                            beløpSkalIkkeTilbakekreves = BigDecimal.ZERO.setScale(2),
                            skatteProsent = BigDecimal.ZERO.setScale(4),
                        ),
                        Kravgrunnlag.Grunnlagsperiode.Grunnlagsbeløp(
                            kode = KlasseKode.SUUFORE,
                            type = KlasseType.YTEL,
                            beløpTidligereUtbetaling = BigDecimal(9989).setScale(2),
                            beløpNyUtbetaling = BigDecimal.ZERO.setScale(2),
                            beløpSkalTilbakekreves = BigDecimal(9989).setScale(2),
                            beløpSkalIkkeTilbakekreves = BigDecimal.ZERO.setScale(2),
                            skatteProsent = BigDecimal(43.9983)
                                .setScale(4, RoundingMode.HALF_UP),
                        ),
                    ),
                ),
            ),
        )
    }

    @Test
    fun `mapper nyopprettet kravgrunnlag for endring av ytelse`() {
        //language=XML
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

        val expected = KravmeldingDto(
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
        )

        KravgrunnlagMapper.toDto(inputXml).getOrFail() shouldBe expected
        KravgrunnlagMapper.toKravgrunnlg(RåttKravgrunnlag(inputXml)).getOrFail() shouldBe Kravgrunnlag(
            saksnummer = Saksnummer(2463),
            kravgrunnlagId = "298606",
            vedtakId = "436206",
            kontrollfelt = "2022-02-07-18.39.47.693011",
            status = Kravgrunnlag.KravgrunnlagStatus.NY,
            behandler = NavIdentBruker.Saksbehandler("K231B433"),
            grunnlagsperioder = listOf(
                Kravgrunnlag.Grunnlagsperiode(
                    periode = Periode.create(
                        fraOgMed = 1.oktober(2021),
                        tilOgMed = 31.oktober(2021),
                    ),
                    beløpSkattMnd = BigDecimal(5280).setScale(2),
                    grunnlagsbeløp = listOf(
                        Kravgrunnlag.Grunnlagsperiode.Grunnlagsbeløp(
                            kode = KlasseKode.KL_KODE_FEIL_INNT,
                            type = KlasseType.FEIL,
                            beløpTidligereUtbetaling = BigDecimal.ZERO.setScale(2),
                            beløpNyUtbetaling = BigDecimal(12000).setScale(2),
                            beløpSkalTilbakekreves = BigDecimal.ZERO.setScale(2),
                            beløpSkalIkkeTilbakekreves = BigDecimal.ZERO.setScale(2),
                            skatteProsent = BigDecimal.ZERO.setScale(4),
                        ),
                        Kravgrunnlag.Grunnlagsperiode.Grunnlagsbeløp(
                            kode = KlasseKode.SUUFORE,
                            type = KlasseType.YTEL,
                            beløpTidligereUtbetaling = BigDecimal(21989).setScale(2),
                            beløpNyUtbetaling = BigDecimal(9989).setScale(2),
                            beløpSkalTilbakekreves = BigDecimal(12000).setScale(2),
                            beløpSkalIkkeTilbakekreves = BigDecimal.ZERO.setScale(2),
                            skatteProsent = BigDecimal(43.9992)
                                .setScale(4, RoundingMode.HALF_UP),
                        ),
                    ),
                ),
                Kravgrunnlag.Grunnlagsperiode(
                    periode = Periode.create(
                        fraOgMed = 1.november(2021),
                        tilOgMed = 30.november(2021),
                    ),
                    beløpSkattMnd = BigDecimal(5280).setScale(2),
                    grunnlagsbeløp = listOf(
                        Kravgrunnlag.Grunnlagsperiode.Grunnlagsbeløp(
                            kode = KlasseKode.KL_KODE_FEIL_INNT,
                            type = KlasseType.FEIL,
                            beløpTidligereUtbetaling = BigDecimal.ZERO.setScale(2),
                            beløpNyUtbetaling = BigDecimal(12000).setScale(2),
                            beløpSkalTilbakekreves = BigDecimal.ZERO.setScale(2),
                            beløpSkalIkkeTilbakekreves = BigDecimal.ZERO.setScale(2),
                            skatteProsent = BigDecimal.ZERO.setScale(4),
                        ),
                        Kravgrunnlag.Grunnlagsperiode.Grunnlagsbeløp(
                            kode = KlasseKode.SUUFORE,
                            type = KlasseType.YTEL,
                            beløpTidligereUtbetaling = BigDecimal(21989).setScale(2),
                            beløpNyUtbetaling = BigDecimal(9989).setScale(2),
                            beløpSkalTilbakekreves = BigDecimal(12000).setScale(2),
                            beløpSkalIkkeTilbakekreves = BigDecimal.ZERO.setScale(2),
                            skatteProsent = BigDecimal(43.9992)
                                .setScale(4, RoundingMode.HALF_UP),
                        ),
                    ),
                ),
            ),
        )
    }
}
