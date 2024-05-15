package no.nav.su.se.bakover.service.regulering

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.tid.april
import no.nav.su.se.bakover.common.domain.tid.desember
import no.nav.su.se.bakover.common.domain.tid.januar
import no.nav.su.se.bakover.common.domain.tid.mai
import no.nav.su.se.bakover.common.domain.tid.periode.PeriodeMedOptionalTilOgMed
import no.nav.su.se.bakover.common.domain.tid.periode.Perioder
import no.nav.su.se.bakover.common.tid.periode.april
import no.nav.su.se.bakover.common.tid.periode.desember
import no.nav.su.se.bakover.common.tid.periode.januar
import no.nav.su.se.bakover.common.tid.periode.mai
import no.nav.su.se.bakover.domain.regulering.Reguleringstype
import no.nav.su.se.bakover.domain.regulering.ÅrsakTilManuellRegulering
import no.nav.su.se.bakover.domain.regulering.ÅrsakTilManuellReguleringKategori
import no.nav.su.se.bakover.test.nyEksternSupplementRegulering
import no.nav.su.se.bakover.test.nyEksternvedtakEndring
import no.nav.su.se.bakover.test.nyReguleringssupplementFor
import no.nav.su.se.bakover.test.nyReguleringssupplementInnholdPerType
import no.nav.su.se.bakover.test.nyÅrsakAutomatiskSendingTilUtbetalingFeilet
import no.nav.su.se.bakover.test.nyÅrsakBrukerManglerSupplement
import no.nav.su.se.bakover.test.nyÅrsakDelvisOpphør
import no.nav.su.se.bakover.test.nyÅrsakDifferanseEtterRegulering
import no.nav.su.se.bakover.test.nyÅrsakDifferanseFørRegulering
import no.nav.su.se.bakover.test.nyÅrsakFantIkkeVedtakForApril
import no.nav.su.se.bakover.test.nyÅrsakFinnesFlerePerioderAvFradrag
import no.nav.su.se.bakover.test.nyÅrsakForventetInntektErStørreEnn0
import no.nav.su.se.bakover.test.nyÅrsakFradragErUtenlandsinntekt
import no.nav.su.se.bakover.test.nyÅrsakSupplementHarFlereVedtaksperioderForFradrag
import no.nav.su.se.bakover.test.nyÅrsakSupplementInneholderIkkeFradraget
import no.nav.su.se.bakover.test.nyÅrsakVedtakstidslinjeErIkkeSammenhengende
import no.nav.su.se.bakover.test.nyÅrsakYtelseErMidlertidigStanset
import no.nav.su.se.bakover.test.opprettetRegulering
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import vilkår.inntekt.domain.grunnlag.FradragTilhører
import vilkår.inntekt.domain.grunnlag.Fradragstype
import java.math.BigDecimal

class ReguleringsloggKtTest {

    @Test
    fun `liste av differanseEtterRegulering printes som forventet`() {
        listOf(
            opprettetRegulering(
                eksternSupplementRegulering = nyEksternSupplementRegulering(
                    bruker = nyReguleringssupplementFor(
                        innhold = arrayOf(
                            nyReguleringssupplementInnholdPerType(
                                kategori = Fradragstype.Kategori.Uføretrygd,
                                vedtak = listOf(nyEksternvedtakEndring(beløp = 400)),
                            ),
                        ),
                    ),
                    eps = listOf(
                        nyReguleringssupplementFor(
                            innhold = arrayOf(
                                nyReguleringssupplementInnholdPerType(
                                    kategori = Fradragstype.Kategori.Alderspensjon,
                                    vedtak = listOf(nyEksternvedtakEndring(beløp = 500)),
                                ),
                            ),
                        ),
                    ),
                ),
                reguleringstype = Reguleringstype.MANUELL(
                    setOf(
                        nyÅrsakDifferanseEtterRegulering(
                            forventetBeløpEtterRegulering = BigDecimal(600),
                            eksterntBeløpEtterRegulering = BigDecimal(700),
                        ),
                        nyÅrsakDifferanseEtterRegulering(
                            forventetBeløpEtterRegulering = BigDecimal(800),
                            eksterntBeløpEtterRegulering = BigDecimal(900),
                            fradragskategori = Fradragstype.Kategori.Alderspensjon,
                            fradragTilhører = FradragTilhører.EPS,
                        ),
                    ),
                ),
            ),
            opprettetRegulering(
                saksnummer = Saksnummer(2022),
                eksternSupplementRegulering = nyEksternSupplementRegulering(
                    bruker = nyReguleringssupplementFor(
                        innhold = arrayOf(
                            nyReguleringssupplementInnholdPerType(
                                kategori = Fradragstype.Kategori.Uføretrygd,
                                vedtak = listOf(nyEksternvedtakEndring(beløp = 1000)),
                            ),
                        ),
                    ),
                ),
                reguleringstype = Reguleringstype.MANUELL(
                    nyÅrsakDifferanseEtterRegulering(
                        forventetBeløpEtterRegulering = BigDecimal(1100),
                        eksterntBeløpEtterRegulering = BigDecimal(1200),
                    ),
                ),
            ),
            opprettetRegulering(
                saksnummer = Saksnummer(2023),
                eksternSupplementRegulering = nyEksternSupplementRegulering(
                    eps = listOf(
                        nyReguleringssupplementFor(
                            innhold = arrayOf(
                                nyReguleringssupplementInnholdPerType(
                                    kategori = Fradragstype.Kategori.Uføretrygd,
                                    vedtak = listOf(nyEksternvedtakEndring(beløp = 1300)),
                                ),
                            ),
                        ),
                    ),
                ),
                reguleringstype = Reguleringstype.MANUELL(
                    nyÅrsakDifferanseEtterRegulering(
                        forventetBeløpEtterRegulering = BigDecimal(1400),
                        eksterntBeløpEtterRegulering = BigDecimal(1500),
                        fradragTilhører = FradragTilhører.EPS,
                    ),
                ),
            ),
        ).toCSVLoggableString() shouldBe
            mapOf(
                ÅrsakTilManuellReguleringKategori.DifferanseEtterRegulering to """
            saksnummer;beløpFraAprilVedtak;forventetBeløpEtterRegulering;eksterntBeløpEtterRegulering;differanse;fradragskategori;fradragTilhører
            2021;400;600;700;100;Uføretrygd;BRUKER
            2021;500;800;900;100;Alderspensjon;EPS
            2022;1000;1100;1200;100;Uføretrygd;BRUKER
            2023;1300;1400;1500;100;Uføretrygd;EPS
                """.trimIndent(),
            )
    }

    @Test
    fun `liste av DifferanseFørRegulering printes som forventet`() {
        listOf(
            opprettetRegulering(reguleringstype = Reguleringstype.MANUELL(setOf(nyÅrsakDifferanseFørRegulering()))),
            opprettetRegulering(
                saksnummer = Saksnummer(2022),
                reguleringstype = Reguleringstype.MANUELL(
                    nyÅrsakDifferanseFørRegulering(
                        vårtBeløpFørRegulering = BigDecimal(1200),
                        eksterntBeløpFørRegulering = BigDecimal(1300),
                    ),
                ),
            ),
        ).toCSVLoggableString() shouldBe
            mapOf(
                ÅrsakTilManuellReguleringKategori.DifferanseFørRegulering to """
            saksnummer;vårtBeløpFørRegulering;eksterntBeløpFørRegulering;differanse;fradragskategori;fradragTilhører
            2021;1000;1100;100;Uføretrygd;BRUKER
            2022;1200;1300;100;Uføretrygd;BRUKER
                """.trimIndent(),
            )
    }

    @Test
    fun `liste av FantIkkeVedtakForApril printes ut som forventet`() {
        listOf(
            opprettetRegulering(
                reguleringstype = Reguleringstype.MANUELL(nyÅrsakFantIkkeVedtakForApril()),
            ),
            opprettetRegulering(
                saksnummer = Saksnummer(2022),
                reguleringstype = Reguleringstype.MANUELL(nyÅrsakFantIkkeVedtakForApril()),
            ),
        ).toCSVLoggableString() shouldBe
            mapOf(
                ÅrsakTilManuellReguleringKategori.FantIkkeVedtakForApril to """
            saksnummer;fradragskategori;fradragTilhører
            2021;Uføretrygd;BRUKER
            2022;Uføretrygd;BRUKER
                """.trimIndent(),
            )
    }

    @Test
    fun `liste av BrukerManglerSupplement printes ut som forventet`() {
        listOf(
            opprettetRegulering(
                reguleringstype = Reguleringstype.MANUELL(nyÅrsakBrukerManglerSupplement()),
            ),
            opprettetRegulering(
                saksnummer = Saksnummer(2022),
                reguleringstype = Reguleringstype.MANUELL(nyÅrsakBrukerManglerSupplement(fradragTilhører = FradragTilhører.EPS)),
            ),
        ).toCSVLoggableString() shouldBe
            mapOf(
                ÅrsakTilManuellReguleringKategori.BrukerManglerSupplement to """
            saksnummer;fradragskategori;fradragTilhører
            2021;Uføretrygd;BRUKER
            2022;Uføretrygd;EPS
                """.trimIndent(),
            )
    }

    @Test
    fun `liste av FinnesFlerePerioderAvFradrag printes ut som forventet`() {
        listOf(
            opprettetRegulering(reguleringstype = Reguleringstype.MANUELL(nyÅrsakFinnesFlerePerioderAvFradrag())),
            opprettetRegulering(
                saksnummer = Saksnummer(2022),
                reguleringstype = Reguleringstype.MANUELL(nyÅrsakFinnesFlerePerioderAvFradrag(fradragTilhører = FradragTilhører.EPS)),
            ),
        ).toCSVLoggableString() shouldBe
            mapOf(
                ÅrsakTilManuellReguleringKategori.FinnesFlerePerioderAvFradrag to """
            saksnummer;fradragskategori;fradragTilhører
            2021;Uføretrygd;BRUKER
            2022;Uføretrygd;EPS
                """.trimIndent(),
            )
    }

    @Test
    fun `liste av fradragErUtenlandsk printes ut som forventet`() {
        listOf(
            opprettetRegulering(
                reguleringstype = Reguleringstype.MANUELL(nyÅrsakFradragErUtenlandsinntekt()),
            ),
            opprettetRegulering(
                saksnummer = Saksnummer(2022),
                reguleringstype = Reguleringstype.MANUELL(nyÅrsakFradragErUtenlandsinntekt(fradragTilhører = FradragTilhører.EPS)),
            ),
        ).toCSVLoggableString() shouldBe
            mapOf(
                ÅrsakTilManuellReguleringKategori.FradragErUtenlandsinntekt to """
            saksnummer;fradragskategori;fradragTilhører
            2021;Uføretrygd;BRUKER
            2022;Uføretrygd;EPS
                """.trimIndent(),
            )
    }

    @Test
    fun `liste av SupplementHarFlereVedtaksperioderForFradrag printes ut som forventet`() {
        listOf(
            opprettetRegulering(
                reguleringstype = Reguleringstype.MANUELL(
                    nyÅrsakSupplementHarFlereVedtaksperioderForFradrag(),
                ),
            ),
            opprettetRegulering(
                saksnummer = Saksnummer(2022),
                reguleringstype = Reguleringstype.MANUELL(
                    nyÅrsakSupplementHarFlereVedtaksperioderForFradrag(
                        fradragTilhører = FradragTilhører.EPS,
                        eksterneReguleringsvedtakperioder = listOf(
                            PeriodeMedOptionalTilOgMed(1.januar(2021), 30.april(2021)),
                            PeriodeMedOptionalTilOgMed(1.mai(2021), 31.desember(2021)),
                        ),
                    ),
                ),
            ),
        ).toCSVLoggableString() shouldBe mapOf(
            ÅrsakTilManuellReguleringKategori.SupplementHarFlereVedtaksperioderForFradrag to """
                saksnummer;perioder;fradragskategori;fradragTilhører
                2021;(2021-05-01 - null);Uføretrygd;BRUKER
                2022;(2021-01-01 - 2021-04-30),(2021-05-01 - 2021-12-31);Uføretrygd;EPS
            """.trimIndent(),
        )
    }

    @Test
    fun `liste av SupplementInneholderIkkeFradraget printes ut som forventet`() {
        listOf(
            opprettetRegulering(reguleringstype = Reguleringstype.MANUELL(nyÅrsakSupplementInneholderIkkeFradraget())),
            opprettetRegulering(
                saksnummer = Saksnummer(2022),
                reguleringstype = Reguleringstype.MANUELL(nyÅrsakSupplementInneholderIkkeFradraget(fradragTilhører = FradragTilhører.EPS)),
            ),
        ).toCSVLoggableString() shouldBe mapOf(
            ÅrsakTilManuellReguleringKategori.SupplementInneholderIkkeFradraget to """
                saksnummer;fradragskategori;fradragTilhører
                2021;Uføretrygd;BRUKER
                2022;Uføretrygd;EPS
            """.trimIndent(),
        )
    }

    @Test
    fun `liste av AutomatiskSendingTilUtbetalingFeilet printes ut som forventet`() {
        listOf(
            opprettetRegulering(
                reguleringstype = Reguleringstype.MANUELL(nyÅrsakAutomatiskSendingTilUtbetalingFeilet()),
            ),
            opprettetRegulering(
                saksnummer = Saksnummer(2022),
                reguleringstype = Reguleringstype.MANUELL(nyÅrsakAutomatiskSendingTilUtbetalingFeilet()),
            ),
        ).toCSVLoggableString() shouldBe mapOf(
            ÅrsakTilManuellReguleringKategori.AutomatiskSendingTilUtbetalingFeilet to """
                saksnummer
                2021
                2022
            """.trimIndent(),
        )
    }

    @Test
    fun `liste av DelvisOpphør printes ut som forventet`() {
        listOf(
            opprettetRegulering(
                reguleringstype = Reguleringstype.MANUELL(nyÅrsakDelvisOpphør()),
            ),
            opprettetRegulering(
                saksnummer = Saksnummer(2022),
                reguleringstype = Reguleringstype.MANUELL(
                    nyÅrsakDelvisOpphør(
                        opphørsperioder = Perioder.create(listOf(januar(2021)..april(2021), mai(2021)..desember(2021))),
                    ),
                ),
            ),
        ).toCSVLoggableString() shouldBe mapOf(
            ÅrsakTilManuellReguleringKategori.DelvisOpphør to """
                saksnummer;opphørsperioder
                2021;(2021-01-01 - 2021-12-31)
                2022;(2021-01-01 - 2021-04-30),(2021-05-01 - 2021-12-31)
            """.trimIndent(),
        )
    }

    @Test
    fun `liste av ForventetInntektErStørreEnn0 printes ut som forventet`() {
        listOf(
            opprettetRegulering(
                reguleringstype = Reguleringstype.MANUELL(nyÅrsakForventetInntektErStørreEnn0()),
            ),
            opprettetRegulering(
                saksnummer = Saksnummer(2022),
                reguleringstype = Reguleringstype.MANUELL(nyÅrsakForventetInntektErStørreEnn0()),
            ),
        ).toCSVLoggableString() shouldBe mapOf(
            ÅrsakTilManuellReguleringKategori.ForventetInntektErStørreEnn0 to """
                saksnummer
                2021
                2022
            """.trimIndent(),
        )
    }

    @Test
    fun `liste av VedtakstidslinjeErIkkeSammenhengende printes ut som forventet`() {
        listOf(
            opprettetRegulering(
                reguleringstype = Reguleringstype.MANUELL(nyÅrsakVedtakstidslinjeErIkkeSammenhengende()),
            ),
            opprettetRegulering(
                saksnummer = Saksnummer(2022),
                reguleringstype = Reguleringstype.MANUELL(nyÅrsakVedtakstidslinjeErIkkeSammenhengende()),
            ),
        ).toCSVLoggableString() shouldBe mapOf(
            ÅrsakTilManuellReguleringKategori.VedtakstidslinjeErIkkeSammenhengende to """
                saksnummer
                2021
                2022
            """.trimIndent(),
        )
    }

    @Test
    fun `liste av YtelseErMidlertidigStanset printes ut som forventet`() {
        listOf(
            opprettetRegulering(
                reguleringstype = Reguleringstype.MANUELL(nyÅrsakYtelseErMidlertidigStanset()),
            ),
            opprettetRegulering(
                saksnummer = Saksnummer(2022),
                reguleringstype = Reguleringstype.MANUELL(nyÅrsakYtelseErMidlertidigStanset()),
            ),
        ).toCSVLoggableString() shouldBe mapOf(
            ÅrsakTilManuellReguleringKategori.YtelseErMidlertidigStanset to """
                saksnummer
                2021
                2022
            """.trimIndent(),
        )
    }

    @Test
    fun `en liste av forskjellige årsaker grupperer alle like typer`() {
        listOf(
            opprettetRegulering(
                eksternSupplementRegulering = nyEksternSupplementRegulering(
                    bruker = nyReguleringssupplementFor(
                        innhold = arrayOf(
                            nyReguleringssupplementInnholdPerType(
                                kategori = Fradragstype.Kategori.Uføretrygd,
                                vedtak = listOf(nyEksternvedtakEndring(beløp = 100)),
                            ),
                        ),
                    ),
                ),
                reguleringstype = Reguleringstype.MANUELL(
                    nyÅrsakDifferanseEtterRegulering(
                        forventetBeløpEtterRegulering = BigDecimal(200),
                        eksterntBeløpEtterRegulering = BigDecimal(300),
                    ),
                ),
            ),
            opprettetRegulering(
                saksnummer = Saksnummer(2022),
                eksternSupplementRegulering = nyEksternSupplementRegulering(
                    bruker = nyReguleringssupplementFor(
                        innhold = arrayOf(nyReguleringssupplementInnholdPerType(kategori = Fradragstype.Kategori.Uføretrygd)),
                    ),
                ),
                reguleringstype = Reguleringstype.MANUELL(
                    nyÅrsakDifferanseFørRegulering(
                        vårtBeløpFørRegulering = BigDecimal(500),
                        eksterntBeløpFørRegulering = BigDecimal(600),
                    ),
                ),
            ),
            opprettetRegulering(
                saksnummer = Saksnummer(2023),
                eksternSupplementRegulering = nyEksternSupplementRegulering(
                    bruker = nyReguleringssupplementFor(
                        innhold = arrayOf(nyReguleringssupplementInnholdPerType(kategori = Fradragstype.Kategori.Uføretrygd)),
                    ),
                ),
                reguleringstype = Reguleringstype.MANUELL(nyÅrsakFantIkkeVedtakForApril()),
            ),
        ).toCSVLoggableString() shouldBe mapOf(
            ÅrsakTilManuellReguleringKategori.DifferanseEtterRegulering to """
                saksnummer;beløpFraAprilVedtak;forventetBeløpEtterRegulering;eksterntBeløpEtterRegulering;differanse;fradragskategori;fradragTilhører
                2021;100;200;300;100;Uføretrygd;BRUKER
            """.trimIndent(),
            ÅrsakTilManuellReguleringKategori.DifferanseFørRegulering to """
                saksnummer;vårtBeløpFørRegulering;eksterntBeløpFørRegulering;differanse;fradragskategori;fradragTilhører
                2022;500;600;100;Uføretrygd;BRUKER
            """.trimIndent(),
            ÅrsakTilManuellReguleringKategori.FantIkkeVedtakForApril to """
                saksnummer;fradragskategori;fradragTilhører
                2023;Uføretrygd;BRUKER
            """.trimIndent(),
        )
    }

    @Test
    fun `kaster exception hvis man prøver å printe ut med en historisk årsak`() {
        assertThrows<IllegalStateException> {
            listOf(opprettetRegulering(reguleringstype = Reguleringstype.MANUELL(ÅrsakTilManuellRegulering.Historisk.FradragMåHåndteresManuelt))).toCSVLoggableString()
        }
        assertThrows<IllegalStateException> {
            listOf(opprettetRegulering(reguleringstype = Reguleringstype.MANUELL(ÅrsakTilManuellRegulering.Historisk.UtbetalingFeilet))).toCSVLoggableString()
        }
        assertThrows<IllegalStateException> {
            listOf(opprettetRegulering(reguleringstype = Reguleringstype.MANUELL(ÅrsakTilManuellRegulering.Historisk.ForventetInntektErStørreEnn0))).toCSVLoggableString()
        }
        assertThrows<IllegalStateException> {
            listOf(opprettetRegulering(reguleringstype = Reguleringstype.MANUELL(ÅrsakTilManuellRegulering.Historisk.YtelseErMidlertidigStanset))).toCSVLoggableString()
        }
    }
}
