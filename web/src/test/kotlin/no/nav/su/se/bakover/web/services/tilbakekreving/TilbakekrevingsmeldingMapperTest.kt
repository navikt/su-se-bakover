package no.nav.su.se.bakover.web.services.tilbakekreving

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.NavIdentBruker
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.periode.november
import no.nav.su.se.bakover.common.periode.oktober
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.oppdrag.simulering.KlasseKode
import no.nav.su.se.bakover.domain.oppdrag.simulering.KlasseType
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.Kravgrunnlag
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.RåttKravgrunnlag
import no.nav.su.se.bakover.test.getOrFail
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.math.RoundingMode

internal class TilbakekrevingsmeldingMapperTest {

    @Test
    fun `mapper nyopprettet kravgrunnlag for opphør av ytelse`() {
        val inputXml = javaClass.getResource("/tilbakekreving/kravgrunnlag_opphør.xml").readText()
        val expected = KravgrunnlagRootDto(
            KravgrunnlagDto(
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
                utbetalingId = "268e62fb-3079-4e8d-ab32-ff9fb9",
                tilbakekrevingsperioder = listOf(
                    KravgrunnlagDto.Tilbakekrevingsperiode(
                        periode = KravgrunnlagDto.Tilbakekrevingsperiode.Periode(
                            fraOgMed = "2021-10-01",
                            tilOgMed = "2021-10-31",
                        ),
                        skattebeløpPerMåned = "4395.00",
                        tilbakekrevingsbeløp = listOf(
                            KravgrunnlagDto.Tilbakekrevingsperiode.Tilbakekrevingsbeløp(
                                kodeKlasse = "KL_KODE_FEIL_INNT",
                                typeKlasse = "FEIL",
                                belopOpprUtbet = "0.00",
                                belopNy = "9989.00",
                                belopTilbakekreves = "0.00",
                                belopUinnkrevd = "0.00",
                                skattProsent = "0.0000",
                            ),
                            KravgrunnlagDto.Tilbakekrevingsperiode.Tilbakekrevingsbeløp(
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
            ),
        )

        TilbakekrevingsmeldingMapper.toDto(inputXml).getOrFail() shouldBe expected
        TilbakekrevingsmeldingMapper.toKravgrunnlg(RåttKravgrunnlag(inputXml)).getOrFail() shouldBe Kravgrunnlag(
            saksnummer = Saksnummer(2461),
            kravgrunnlagId = "298604",
            vedtakId = "436204",
            kontrollfelt = "2022-02-07-18.39.46.586953",
            status = Kravgrunnlag.KravgrunnlagStatus.NY,
            behandler = NavIdentBruker.Saksbehandler("K231B433"),
            utbetalingId = UUID30.fromString("268e62fb-3079-4e8d-ab32-ff9fb9"),
            grunnlagsperioder = listOf(
                Kravgrunnlag.Grunnlagsperiode(
                    periode = oktober(2021),
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
                            skatteProsent = BigDecimal("43.9983")
                                .setScale(4, RoundingMode.HALF_UP),
                        ),
                    ),
                ),
            ),
        )
    }

    @Test
    fun `mapper nyopprettet kravgrunnlag for endring av ytelse`() {
        val inputXml = javaClass.getResource("/tilbakekreving/kravgrunnlag_endring.xml").readText()
        val expected = KravgrunnlagRootDto(
            KravgrunnlagDto(
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
                utbetalingId = "268e62fb-3079-4e8d-ab32-ff9fb9",
                tilbakekrevingsperioder = listOf(
                    KravgrunnlagDto.Tilbakekrevingsperiode(
                        periode = KravgrunnlagDto.Tilbakekrevingsperiode.Periode(
                            fraOgMed = "2021-10-01",
                            tilOgMed = "2021-10-31",
                        ),
                        skattebeløpPerMåned = "5280.00",
                        tilbakekrevingsbeløp = listOf(
                            KravgrunnlagDto.Tilbakekrevingsperiode.Tilbakekrevingsbeløp(
                                kodeKlasse = "KL_KODE_FEIL_INNT",
                                typeKlasse = "FEIL",
                                belopOpprUtbet = "0.00",
                                belopNy = "12000.00",
                                belopTilbakekreves = "0.00",
                                belopUinnkrevd = "0.00",
                                skattProsent = "0.0000",
                            ),
                            KravgrunnlagDto.Tilbakekrevingsperiode.Tilbakekrevingsbeløp(
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
                    KravgrunnlagDto.Tilbakekrevingsperiode(
                        periode = KravgrunnlagDto.Tilbakekrevingsperiode.Periode(
                            fraOgMed = "2021-11-01",
                            tilOgMed = "2021-11-30",
                        ),
                        skattebeløpPerMåned = "5280.00",
                        tilbakekrevingsbeløp = listOf(
                            KravgrunnlagDto.Tilbakekrevingsperiode.Tilbakekrevingsbeløp(
                                kodeKlasse = "KL_KODE_FEIL_INNT",
                                typeKlasse = "FEIL",
                                belopOpprUtbet = "0.00",
                                belopNy = "12000.00",
                                belopTilbakekreves = "0.00",
                                belopUinnkrevd = "0.00",
                                skattProsent = "0.0000",
                            ),
                            KravgrunnlagDto.Tilbakekrevingsperiode.Tilbakekrevingsbeløp(
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
            ),
        )

        TilbakekrevingsmeldingMapper.toDto(inputXml).getOrFail() shouldBe expected
        TilbakekrevingsmeldingMapper.toKravgrunnlg(RåttKravgrunnlag(inputXml)).getOrFail() shouldBe Kravgrunnlag(
            saksnummer = Saksnummer(2463),
            kravgrunnlagId = "298606",
            vedtakId = "436206",
            kontrollfelt = "2022-02-07-18.39.47.693011",
            status = Kravgrunnlag.KravgrunnlagStatus.NY,
            behandler = NavIdentBruker.Saksbehandler("K231B433"),
            utbetalingId = UUID30.fromString("268e62fb-3079-4e8d-ab32-ff9fb9"),
            grunnlagsperioder = listOf(
                Kravgrunnlag.Grunnlagsperiode(
                    periode = oktober(2021),
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
                            skatteProsent = BigDecimal("43.9992")
                                .setScale(4, RoundingMode.HALF_UP),
                        ),
                    ),
                ),
                Kravgrunnlag.Grunnlagsperiode(
                    periode = november(2021),
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
                            skatteProsent = BigDecimal("43.9992")
                                .setScale(4, RoundingMode.HALF_UP),
                        ),
                    ),
                ),
            ),
        )
    }

    @Test
    fun `mapper melding om statusendring på åpent kravgrunnlag`() {
        val inputXml = javaClass.getResource("/tilbakekreving/kravgrunnlag_statusendring.xml").readText()
        val expected = KravgrunnlagStatusendringRootDto(
            endringKravOgVedtakstatus = KravgrunnlagStatusendringDto(
                vedtakId = "436206",
                kodeStatusKrav = "SPER",
                kodeFagområde = "SUUFORE",
                fagsystemId = "2463",
                vedtakGjelderId = "18108619852",
                idTypeGjelder = "PERSON",
            ),
        )
        TilbakekrevingsmeldingMapper.toDto(inputXml).getOrFail() shouldBe expected
    }
}
