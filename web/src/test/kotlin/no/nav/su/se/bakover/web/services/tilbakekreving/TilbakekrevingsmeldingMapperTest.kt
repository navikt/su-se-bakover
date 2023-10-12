package no.nav.su.se.bakover.web.services.tilbakekreving

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.client.oppdrag.toOppdragTimestamp
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.tid.periode.november
import no.nav.su.se.bakover.common.tid.periode.oktober
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.getOrFail
import org.junit.jupiter.api.Test
import tilbakekreving.domain.kravgrunnlag.Kravgrunnlag
import tilbakekreving.domain.kravgrunnlag.RåttKravgrunnlag
import tilbakekreving.presentation.consumer.KravgrunnlagDto
import tilbakekreving.presentation.consumer.KravgrunnlagRootDto
import tilbakekreving.presentation.consumer.KravgrunnlagStatusendringDto
import tilbakekreving.presentation.consumer.KravgrunnlagStatusendringRootDto
import tilbakekreving.presentation.consumer.TilbakekrevingsmeldingMapper
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
                kontrollfelt = "2021-01-01-02.02.03.456789",
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
        TilbakekrevingsmeldingMapper.toKravgrunnlag(RåttKravgrunnlag(inputXml)).getOrFail() shouldBe Kravgrunnlag(
            saksnummer = Saksnummer(2461),
            eksternKravgrunnlagId = "298604",
            eksternVedtakId = "436204",
            eksternKontrollfelt = "2021-01-01-02.02.03.456789",
            status = Kravgrunnlag.KravgrunnlagStatus.Nytt,
            behandler = "K231B433",
            utbetalingId = UUID30.fromString("268e62fb-3079-4e8d-ab32-ff9fb9"),
            eksternTidspunkt = fixedTidspunkt,
            grunnlagsmåneder = listOf(
                Kravgrunnlag.Grunnlagsmåned(
                    måned = oktober(2021),
                    betaltSkattForYtelsesgruppen = BigDecimal(4395).setScale(2),
                    ytelse = Kravgrunnlag.Grunnlagsmåned.Ytelse(
                        beløpTidligereUtbetaling = 9989,
                        beløpNyUtbetaling = 0,
                        beløpSkalTilbakekreves = 9989,
                        beløpSkalIkkeTilbakekreves = 0,
                        skatteProsent = BigDecimal("43.9983").setScale(4, RoundingMode.HALF_UP),
                    ),
                    feilutbetaling = Kravgrunnlag.Grunnlagsmåned.Feilutbetaling(
                        beløpTidligereUtbetaling = 0,
                        beløpNyUtbetaling = 9989,
                        beløpSkalTilbakekreves = 0,
                        beløpSkalIkkeTilbakekreves = 0,
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
                kontrollfelt = "2021-01-01-02.02.03.456789",
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
        val tidspunkt = fixedTidspunkt
        TilbakekrevingsmeldingMapper.toDto(inputXml).getOrFail() shouldBe expected
        TilbakekrevingsmeldingMapper.toKravgrunnlag(RåttKravgrunnlag(inputXml)).getOrFail() shouldBe Kravgrunnlag(
            saksnummer = Saksnummer(2463),
            eksternKravgrunnlagId = "298606",
            eksternVedtakId = "436206",
            eksternTidspunkt = tidspunkt,
            status = Kravgrunnlag.KravgrunnlagStatus.Nytt,
            behandler = "K231B433",
            utbetalingId = UUID30.fromString("268e62fb-3079-4e8d-ab32-ff9fb9"),
            eksternKontrollfelt = tidspunkt.toOppdragTimestamp(),
            grunnlagsmåneder = listOf(
                Kravgrunnlag.Grunnlagsmåned(
                    måned = oktober(2021),
                    betaltSkattForYtelsesgruppen = BigDecimal(5280).setScale(2),
                    ytelse = Kravgrunnlag.Grunnlagsmåned.Ytelse(
                        beløpTidligereUtbetaling = 21989,
                        beløpNyUtbetaling = 9989,
                        beløpSkalTilbakekreves = 12000,
                        beløpSkalIkkeTilbakekreves = 0,
                        skatteProsent = BigDecimal("43.9992")
                            .setScale(4, RoundingMode.HALF_UP),
                    ),
                    feilutbetaling = Kravgrunnlag.Grunnlagsmåned.Feilutbetaling(
                        beløpTidligereUtbetaling = 0,
                        beløpNyUtbetaling = 12000,
                        beløpSkalTilbakekreves = 0,
                        beløpSkalIkkeTilbakekreves = 0,
                    ),

                ),
                Kravgrunnlag.Grunnlagsmåned(
                    måned = november(2021),
                    betaltSkattForYtelsesgruppen = BigDecimal(5280).setScale(2),
                    ytelse = Kravgrunnlag.Grunnlagsmåned.Ytelse(
                        beløpTidligereUtbetaling = 21989,
                        beløpNyUtbetaling = 9989,
                        beløpSkalTilbakekreves = 12000,
                        beløpSkalIkkeTilbakekreves = 0,
                        skatteProsent = BigDecimal("43.9992")
                            .setScale(4, RoundingMode.HALF_UP),
                    ),
                    feilutbetaling = Kravgrunnlag.Grunnlagsmåned.Feilutbetaling(
                        beløpTidligereUtbetaling = 0,
                        beløpNyUtbetaling = 12000,
                        beløpSkalTilbakekreves = 0,
                        beløpSkalIkkeTilbakekreves = 0,
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
