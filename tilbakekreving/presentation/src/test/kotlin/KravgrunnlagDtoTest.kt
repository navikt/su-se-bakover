package tilbakekreving.presentation.consumer

import io.kotest.matchers.equality.shouldBeEqualToIgnoringFields
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.tid.periode.november
import no.nav.su.se.bakover.common.tid.periode.oktober
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.kravgrunnlag.kravgrunnlagEndringXml
import no.nav.su.se.bakover.test.kravgrunnlag.kravgrunnlagOpphørXml
import org.junit.jupiter.api.Test
import tilbakekreving.domain.kravgrunnlag.Kravgrunnlag
import tilbakekreving.domain.kravgrunnlag.Kravgrunnlagstatus
import tilbakekreving.domain.kravgrunnlag.RåttKravgrunnlag
import java.math.BigDecimal
import java.math.RoundingMode

internal class KravgrunnlagDtoTest {

    @Test
    fun `mapper nyopprettet kravgrunnlag for opphør av ytelse`() {
        val inputXml = kravgrunnlagOpphørXml
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
        KravgrunnlagDtoMapper.toDto(inputXml).getOrFail() shouldBe expected
        KravgrunnlagDtoMapper.toKravgrunnlag(RåttKravgrunnlag(inputXml)).getOrFail().shouldBeEqualToIgnoringFields(
            Kravgrunnlag(
                // Denne blir ignorert siden den blir autogenerert for den gamle tilbakekrevingsrutinen under revurdering.
                hendelseId = HendelseId.generer(),
                saksnummer = Saksnummer(2461),
                eksternKravgrunnlagId = "298604",
                eksternVedtakId = "436204",
                eksternKontrollfelt = "2021-01-01-02.02.03.456789",
                status = Kravgrunnlagstatus.Nytt,
                behandler = "K231B433",
                utbetalingId = UUID30.fromString("268e62fb-3079-4e8d-ab32-ff9fb9"),
                eksternTidspunkt = fixedTidspunkt,
                grunnlagsperioder = listOf(
                    Kravgrunnlag.Grunnlagsperiode(
                        periode = oktober(2021),
                        betaltSkattForYtelsesgruppen = 4395,
                        bruttoTidligereUtbetalt = 9989,
                        bruttoNyUtbetaling = 0,
                        bruttoFeilutbetaling = 9989,
                        skatteProsent = BigDecimal("43.9983").setScale(4, RoundingMode.HALF_UP),

                    ),
                ),
            ),
            Kravgrunnlag::hendelseId,
        )
    }

    @Test
    fun `mapper nyopprettet kravgrunnlag for endring av ytelse`() {
        val inputXml = kravgrunnlagEndringXml
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
        KravgrunnlagDtoMapper.toDto(inputXml).getOrFail() shouldBe expected
        KravgrunnlagDtoMapper.toKravgrunnlag(RåttKravgrunnlag(inputXml)).getOrFail().shouldBeEqualToIgnoringFields(
            Kravgrunnlag(
                // Denne blir ignorert siden den blir autogenerert for den gamle tilbakekrevingsrutinen under revurdering.
                hendelseId = HendelseId.generer(),
                saksnummer = Saksnummer(2463),
                eksternKravgrunnlagId = "298606",
                eksternVedtakId = "436206",
                eksternTidspunkt = tidspunkt,
                status = Kravgrunnlagstatus.Nytt,
                behandler = "K231B433",
                utbetalingId = UUID30.fromString("268e62fb-3079-4e8d-ab32-ff9fb9"),
                eksternKontrollfelt = "2021-01-01-02.02.03.456789",
                grunnlagsperioder = listOf(
                    Kravgrunnlag.Grunnlagsperiode(
                        periode = oktober(2021),
                        betaltSkattForYtelsesgruppen = 5280,
                        bruttoTidligereUtbetalt = 21989,
                        bruttoNyUtbetaling = 9989,
                        bruttoFeilutbetaling = 12000,
                        skatteProsent = BigDecimal("43.9992")
                            .setScale(4, RoundingMode.HALF_UP),

                    ),
                    Kravgrunnlag.Grunnlagsperiode(
                        periode = november(2021),
                        betaltSkattForYtelsesgruppen = 5280,
                        bruttoTidligereUtbetalt = 21989,
                        bruttoNyUtbetaling = 9989,
                        bruttoFeilutbetaling = 12000,
                        skatteProsent = BigDecimal("43.9992")
                            .setScale(4, RoundingMode.HALF_UP),
                    ),
                ),
            ),
            Kravgrunnlag::hendelseId,
        )
    }
}
