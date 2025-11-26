package beregning.domain

import io.kotest.matchers.equality.shouldBeEqualUsingFields
import no.nav.su.se.bakover.common.domain.regelspesifisering.Regelspesifisering
import no.nav.su.se.bakover.common.domain.regelspesifisering.Regelspesifiseringer
import no.nav.su.se.bakover.common.domain.regelspesifisering.RegelspesifisertGrunnlag
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.satsFactoryTestPåDato
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import vilkår.inntekt.domain.grunnlag.FradragFactory
import vilkår.inntekt.domain.grunnlag.FradragTilhører
import vilkår.inntekt.domain.grunnlag.Fradragstype
import java.time.YearMonth
import java.util.UUID

class BeregningRegelspesifiseringTest {

    @Nested
    inner class Uføre {

        @Test
        fun `Bor alene`() {
            val periode = YearMonth.of(2025, 1).let {
                Periode.create(it.atDay(1), it.atEndOfMonth())
            }
            val strategy = BeregningStrategy.BorAlene(satsFactoryTestPåDato(), Sakstype.UFØRE)

            val result = BeregningFactory(clock = fixedClock).ny(
                id = UUID.randomUUID(),
                opprettet = fixedTidspunkt,
                fradrag = listOf(
                    FradragFactory.nyFradragsperiode(
                        fradragstype = Fradragstype.ForventetInntekt,
                        månedsbeløp = 55000.0,
                        utenlandskInntekt = null,
                        periode = periode,
                        tilhører = FradragTilhører.BRUKER,
                    ),
                    FradragFactory.nyFradragsperiode(
                        fradragstype = Fradragstype.Annet("vant på flaxlodd"),
                        månedsbeløp = 1000.0,
                        utenlandskInntekt = null,
                        periode = periode,
                        tilhører = FradragTilhører.BRUKER,
                    ),
                ),
                begrunnelse = "begrunnelse",
                beregningsperioder = listOf(
                    Beregningsperiode(
                        periode = periode,
                        strategy = strategy,
                    ),
                ),
            )
            with(result.getMånedsberegningerMedRegel().single()) {
                val faktisk = benyttetRegel
                val forventetSatsMinusFradrag = forventetRegel(
                    Regelspesifiseringer.REGEL_SATS_MINUS_FRADRAG_AVRUNDET,
                    avhengigeRegler = listOf(
                        forventetRegel(
                            Regelspesifiseringer.REGEL_BEREGN_SATS_UFØRE_MÅNED,
                            listOf(
                                strategy.somBeregningsgrunnlag(),
                                RegelspesifisertGrunnlag.GRUNNLAG_UFØRE_FAKTOR_HØY.benyttGrunnlag(""),
                            ),
                        ),
                        forventetRegel(
                            Regelspesifiseringer.REGEL_SAMLET_FRADRAG,
                            listOf(
                                forventetRegel(
                                    Regelspesifiseringer.REGEL_FRADRAG_MINUS_MINST_ARBEID_OG_FORVENTET,
                                    listOf(
                                        RegelspesifisertGrunnlag.GRUNNLAG_FRADRAG.benyttGrunnlag(""),
                                    ),
                                ),
                            ),
                        ),
                    ),
                )
                val forventetToProsentAvHøySatsUføre = forventetRegel(
                    Regelspesifiseringer.REGEL_TO_PROSENT_AV_HØY_SATS_UFØRE,
                    listOf(
                        RegelspesifisertGrunnlag.GRUNNLAG_UFØRE_FAKTOR_HØY.benyttGrunnlag(""),
                        RegelspesifisertGrunnlag.GRUNNLAG_GRUNNBELØP.benyttGrunnlag(""),
                    ),
                )
                val forventet = forventetRegel(
                    Regelspesifiseringer.REGEL_MÅNEDSBEREGNING,
                    avhengigeRegler = listOf(
                        strategy.somBeregningsgrunnlag(),
                        forventetSatsMinusFradrag,
                        forventetRegel(
                            Regelspesifiseringer.REGEL_SOSIALSTØNAD_UNDER_2_PROSENT,
                            listOf(
                                forventetSatsMinusFradrag,
                                forventetToProsentAvHøySatsUføre,
                            ),
                        ),
                        forventetRegel(
                            Regelspesifiseringer.REGEL_MINDRE_ENN_2_PROSENT,
                            listOf(
                                forventetSatsMinusFradrag,
                                forventetToProsentAvHøySatsUføre,
                            ),
                        ),
                    ),
                )

                sammenlignRegel(faktisk, forventet)
            }
        }

        @Test
        fun `EPS over 67`() {
            val periode = YearMonth.of(2025, 1).let {
                Periode.create(it.atDay(1), it.atEndOfMonth())
            }
            val strategy = BeregningStrategy.Eps67EllerEldre(satsFactoryTestPåDato(), Sakstype.UFØRE)

            val result = BeregningFactory(clock = fixedClock).ny(
                id = UUID.randomUUID(),
                opprettet = fixedTidspunkt,
                fradrag = listOf(
                    FradragFactory.nyFradragsperiode(
                        fradragstype = Fradragstype.ForventetInntekt,
                        månedsbeløp = 55000.0,
                        utenlandskInntekt = null,
                        periode = periode,
                        tilhører = FradragTilhører.BRUKER,
                    ),
                    FradragFactory.nyFradragsperiode(
                        fradragstype = Fradragstype.Annet("vant på flaxlodd"),
                        månedsbeløp = 1000.0,
                        utenlandskInntekt = null,
                        periode = periode,
                        tilhører = FradragTilhører.EPS,
                    ),
                    FradragFactory.nyFradragsperiode(
                        fradragstype = Fradragstype.Annet("vant på flaxlodd"),
                        månedsbeløp = 1000.0,
                        utenlandskInntekt = null,
                        periode = periode,
                        tilhører = FradragTilhører.EPS,
                    ),
                ),
                begrunnelse = "begrunnelse",
                beregningsperioder = listOf(
                    Beregningsperiode(
                        periode = periode,
                        strategy = strategy,
                    ),
                ),
            )

            with(result.getMånedsberegningerMedRegel().single()) {
                val faktisk = benyttetRegel
                val forventet = forventetRegel(
                    Regelspesifiseringer.REGEL_MÅNEDSBEREGNING,
                    listOf(
                        strategy.somBeregningsgrunnlag(),
                        forventetRegel(
                            Regelspesifiseringer.REGEL_BEREGN_SATS_UFØRE_MÅNED,
                            listOf(
                                RegelspesifisertGrunnlag.GRUNNLAG_GRUNNBELØP.benyttGrunnlag(""),
                                RegelspesifisertGrunnlag.GRUNNLAG_UFØRE_FAKTOR_ORDINÆR.benyttGrunnlag(""),
                            ),
                        ),
                        forventetRegel(
                            Regelspesifiseringer.REGEL_SAMLET_FRADRAG,
                            listOf(
                                forventetRegel(
                                    Regelspesifiseringer.REGEL_FRADRAG_EPS_OVER_FRIBELØP,
                                    listOf(
                                        forventetRegel(
                                            Regelspesifiseringer.REGEL_FRADRAG_MINUS_MINST_ARBEID_OG_FORVENTET,
                                            listOf(
                                                RegelspesifisertGrunnlag.GRUNNLAG_FRADRAG.benyttGrunnlag(""),
                                            ),
                                        ),
                                        forventetRegel(
                                            Regelspesifiseringer.REGEL_BEREGN_SATS_ALDER_MÅNED,
                                            listOf(
                                                RegelspesifisertGrunnlag.GRUNNLAG_GARANTPIPENSJON_ORDINÆR.benyttGrunnlag(""),
                                            ),
                                        ),
                                    ),
                                ),
                            ),
                        ),
                        forventetRegel(
                            Regelspesifiseringer.REGEL_SOSIALSTØNAD_UNDER_2_PROSENT,
                            listOf(
                                forventetRegel(
                                    Regelspesifiseringer.REGEL_TO_PROSENT_AV_HØY_SATS_UFØRE,
                                    listOf(
                                        RegelspesifisertGrunnlag.GRUNNLAG_UFØRE_FAKTOR_HØY.benyttGrunnlag(""),
                                        RegelspesifisertGrunnlag.GRUNNLAG_GRUNNBELØP.benyttGrunnlag(""),
                                    ),
                                ),
                            ),
                        ),
                        forventetRegel(
                            Regelspesifiseringer.REGEL_MINDRE_ENN_2_PROSENT,
                            listOf(
                                forventetRegel(
                                    Regelspesifiseringer.REGEL_TO_PROSENT_AV_HØY_SATS_UFØRE,
                                    listOf(
                                        RegelspesifisertGrunnlag.GRUNNLAG_UFØRE_FAKTOR_HØY.benyttGrunnlag(""),
                                        RegelspesifisertGrunnlag.GRUNNLAG_GRUNNBELØP.benyttGrunnlag(""),
                                    ),
                                ),
                            ),
                        ),
                    ),
                )

                sammenlignRegel(faktisk, forventet)
            }
        }

        @Test
        fun `EPS uføre flyktning`() {
            val periode = YearMonth.of(2025, 1).let {
                Periode.create(it.atDay(1), it.atEndOfMonth())
            }
            val strategy = BeregningStrategy.EpsUnder67ÅrOgUførFlyktning(satsFactoryTestPåDato(), Sakstype.UFØRE)

            val result = BeregningFactory(clock = fixedClock).ny(
                id = UUID.randomUUID(),
                opprettet = fixedTidspunkt,
                fradrag = listOf(
                    FradragFactory.nyFradragsperiode(
                        fradragstype = Fradragstype.ForventetInntekt,
                        månedsbeløp = 55000.0,
                        utenlandskInntekt = null,
                        periode = periode,
                        tilhører = FradragTilhører.BRUKER,
                    ),
                    FradragFactory.nyFradragsperiode(
                        fradragstype = Fradragstype.Annet("vant på flaxlodd"),
                        månedsbeløp = 1000.0,
                        utenlandskInntekt = null,
                        periode = periode,
                        tilhører = FradragTilhører.EPS,
                    ),
                    FradragFactory.nyFradragsperiode(
                        fradragstype = Fradragstype.Annet("vant på flaxlodd"),
                        månedsbeløp = 1000.0,
                        utenlandskInntekt = null,
                        periode = periode,
                        tilhører = FradragTilhører.EPS,
                    ),
                ),
                begrunnelse = "begrunnelse",
                beregningsperioder = listOf(
                    Beregningsperiode(
                        periode = periode,
                        strategy = strategy,
                    ),
                ),
            )

            with(result.getMånedsberegningerMedRegel().single()) {
                val faktisk = benyttetRegel
                val forventet = forventetRegel(
                    Regelspesifiseringer.REGEL_MÅNEDSBEREGNING,
                    listOf(
                        forventetRegel(
                            Regelspesifiseringer.REGEL_BEREGN_SATS_UFØRE_MÅNED,
                            listOf(
                                RegelspesifisertGrunnlag.GRUNNLAG_GRUNNBELØP.benyttGrunnlag(""),
                                RegelspesifisertGrunnlag.GRUNNLAG_UFØRE_FAKTOR_ORDINÆR.benyttGrunnlag(""),
                            ),
                        ),
                        forventetRegel(
                            Regelspesifiseringer.REGEL_SAMLET_FRADRAG,
                            listOf(
                                forventetRegel(
                                    Regelspesifiseringer.REGEL_FRADRAG_EPS_OVER_FRIBELØP,
                                    listOf(
                                        forventetRegel(
                                            Regelspesifiseringer.REGEL_FRADRAG_MINUS_MINST_ARBEID_OG_FORVENTET,
                                            listOf(
                                                RegelspesifisertGrunnlag.GRUNNLAG_FRADRAG.benyttGrunnlag(""),
                                            ),
                                        ),
                                        forventetRegel(
                                            Regelspesifiseringer.REGEL_BEREGN_SATS_UFØRE_MÅNED,
                                            listOf(
                                                RegelspesifisertGrunnlag.GRUNNLAG_GRUNNBELØP.benyttGrunnlag(""),
                                                RegelspesifisertGrunnlag.GRUNNLAG_UFØRE_FAKTOR_ORDINÆR.benyttGrunnlag(""),
                                            ),
                                        ),
                                    ),
                                ),
                            ),
                        ),
                        forventetRegel(
                            Regelspesifiseringer.REGEL_SOSIALSTØNAD_UNDER_2_PROSENT,
                            listOf(
                                forventetRegel(
                                    Regelspesifiseringer.REGEL_TO_PROSENT_AV_HØY_SATS_UFØRE,
                                    listOf(
                                        RegelspesifisertGrunnlag.GRUNNLAG_UFØRE_FAKTOR_HØY.benyttGrunnlag(""),
                                        RegelspesifisertGrunnlag.GRUNNLAG_GRUNNBELØP.benyttGrunnlag(""),
                                    ),
                                ),
                            ),
                        ),
                        forventetRegel(
                            Regelspesifiseringer.REGEL_MINDRE_ENN_2_PROSENT,
                            listOf(
                                forventetRegel(
                                    Regelspesifiseringer.REGEL_TO_PROSENT_AV_HØY_SATS_UFØRE,
                                    listOf(
                                        RegelspesifisertGrunnlag.GRUNNLAG_UFØRE_FAKTOR_HØY.benyttGrunnlag(""),
                                        RegelspesifisertGrunnlag.GRUNNLAG_GRUNNBELØP.benyttGrunnlag(""),
                                    ),
                                ),
                            ),
                        ),
                    ),
                )

                sammenlignRegel(faktisk, forventet)
            }
        }
    }

    @Nested
    inner class Alder

// TODO en test for alle permuteringer
// TODO alle botilstander per uføre/alder
// TODO under to prosent uten sosial stønad
// TODO under to prosent med sosial stønad

// TODO egen for sats endringsknekkpunkt? må skille på grunnlag
}

fun forventetRegel(regel: Regelspesifiseringer, avhengigeRegler: List<Regelspesifisering>) =
    regel.benyttRegelspesifisering(
        verdi = "",
        avhengigeRegler = avhengigeRegler,
    )

internal fun sammenlignRegel(forventet: Regelspesifisering, faktisk: Regelspesifisering) {
    faktisk shouldBeEqualUsingFields {
        excludedProperties = setOf(
            Regelspesifisering.Beregning::benyttetTidspunkt,
            Regelspesifisering.Beregning::verdi,
            Regelspesifisering.Grunnlag::benyttetTidspunkt,
            Regelspesifisering.Grunnlag::verdi,
        )
        forventet
    }
}
