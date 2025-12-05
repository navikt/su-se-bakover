package beregning.domain

import beregning.domain.BeregningRegelspesifiseringTest.Companion.periode
import beregning.domain.ForventetRegelspesifisering.beregning
import beregning.domain.ForventetRegelspesifisering.grunnlag
import io.kotest.matchers.equality.shouldBeEqualUsingFields
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
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
import vilkår.inntekt.domain.grunnlag.Fradrag
import vilkår.inntekt.domain.grunnlag.FradragFactory
import vilkår.inntekt.domain.grunnlag.FradragTilhører
import vilkår.inntekt.domain.grunnlag.Fradragstype
import java.time.YearMonth
import java.util.UUID

class BeregningRegelspesifiseringTest {

    companion object {
        val periode = YearMonth.of(2025, 1).let {
            Periode.create(it.atDay(1), it.atEndOfMonth())
        }
    }

    @Nested
    inner class Uføre {

        @Test
        fun `Bor alene`() {
            val strategi = BeregningStrategy.BorAlene(satsFactoryTestPåDato(), Sakstype.UFØRE)
            with(månedsBeregning(strategi)) {
                val faktisk = benyttetRegel
                val forventetSatsMinusFradrag = beregning(
                    Regelspesifiseringer.REGEL_SATS_MINUS_FRADRAG_AVRUNDET,
                    avhengigeRegler = listOf(
                        beregning(
                            Regelspesifiseringer.REGEL_BEREGN_SATS_UFØRE_MÅNED,
                            listOf(
                                grunnlag(RegelspesifisertGrunnlag.GRUNNLAG_GRUNNBELØP),
                                grunnlag(RegelspesifisertGrunnlag.GRUNNLAG_UFØRE_FAKTOR_HØY),
                            ),
                        ),
                        beregning(
                            Regelspesifiseringer.REGEL_SAMLET_FRADRAG,
                            listOf(
                                beregning(
                                    Regelspesifiseringer.REGEL_FRADRAG_MINUS_MINST_ARBEID_OG_FORVENTET,
                                    listOf(
                                        RegelspesifisertGrunnlag.GRUNNLAG_FRADRAG.benyttGrunnlag(""),
                                    ),
                                ),
                            ),
                        ),
                    ),
                )
                val forventetToProsentAvHøySatsUføre = beregning(
                    Regelspesifiseringer.REGEL_TO_PROSENT_AV_HØY_SATS_UFØRE,
                    listOf(
                        RegelspesifisertGrunnlag.GRUNNLAG_UFØRE_FAKTOR_HØY.benyttGrunnlag(""),
                        RegelspesifisertGrunnlag.GRUNNLAG_GRUNNBELØP.benyttGrunnlag(""),
                    ),
                )
                val forventet = beregning(
                    Regelspesifiseringer.REGEL_MÅNEDSBEREGNING,
                    avhengigeRegler = listOf(
                        RegelspesifisertGrunnlag.GRUNNLAG_BOSITUASJON.benyttGrunnlag(strategi.satsgrunn().name),
                        forventetSatsMinusFradrag,
                        beregning(
                            Regelspesifiseringer.REGEL_SOSIALSTØNAD_UNDER_2_PROSENT,
                            listOf(
                                forventetSatsMinusFradrag,
                                forventetToProsentAvHøySatsUføre,
                            ),
                        ),
                        beregning(
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
        fun `Bor med andre voksne`() {
            val strategi = BeregningStrategy.BorMedVoksne(satsFactoryTestPåDato(), Sakstype.UFØRE)
            with(månedsBeregning(strategi = strategi, eps = true)) {
                val faktisk = benyttetRegel
                val forventetSatsMinusFradrag = beregning(
                    Regelspesifiseringer.REGEL_SATS_MINUS_FRADRAG_AVRUNDET,
                    avhengigeRegler = listOf(
                        beregning(
                            Regelspesifiseringer.REGEL_BEREGN_SATS_UFØRE_MÅNED,
                            listOf(
                                RegelspesifisertGrunnlag.GRUNNLAG_GRUNNBELØP.benyttGrunnlag(""),
                                RegelspesifisertGrunnlag.GRUNNLAG_UFØRE_FAKTOR_ORDINÆR.benyttGrunnlag(""),
                            ),
                        ),
                        beregning(
                            Regelspesifiseringer.REGEL_SAMLET_FRADRAG,
                            listOf(
                                beregning(
                                    Regelspesifiseringer.REGEL_FRADRAG_MINUS_MINST_ARBEID_OG_FORVENTET,
                                    listOf(
                                        RegelspesifisertGrunnlag.GRUNNLAG_FRADRAG.benyttGrunnlag(""),
                                    ),
                                ),
                            ),
                        ),
                    ),
                )
                val forventetToProsentAvHøySatsUføre = beregning(
                    Regelspesifiseringer.REGEL_TO_PROSENT_AV_HØY_SATS_UFØRE,
                    listOf(
                        RegelspesifisertGrunnlag.GRUNNLAG_UFØRE_FAKTOR_HØY.benyttGrunnlag(""),
                        RegelspesifisertGrunnlag.GRUNNLAG_GRUNNBELØP.benyttGrunnlag(""),
                    ),
                )
                val forventet = beregning(
                    Regelspesifiseringer.REGEL_MÅNEDSBEREGNING,
                    listOf(
                        RegelspesifisertGrunnlag.GRUNNLAG_BOSITUASJON.benyttGrunnlag(strategi.satsgrunn().name),
                        forventetSatsMinusFradrag,
                        beregning(
                            Regelspesifiseringer.REGEL_SOSIALSTØNAD_UNDER_2_PROSENT,
                            listOf(
                                forventetSatsMinusFradrag,
                                forventetToProsentAvHøySatsUføre,
                            ),
                        ),
                        beregning(
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
        fun `EPS under 67`() {
            val strategi = BeregningStrategy.EpsUnder67År(satsFactoryTestPåDato(), Sakstype.UFØRE)
            with(månedsBeregning(strategi = strategi, eps = true)) {
                val faktisk = benyttetRegel

                val forventetSatsMinusFradrag = beregning(
                    Regelspesifiseringer.REGEL_SATS_MINUS_FRADRAG_AVRUNDET,
                    avhengigeRegler = listOf(
                        beregning(
                            Regelspesifiseringer.REGEL_BEREGN_SATS_UFØRE_MÅNED,
                            listOf(
                                RegelspesifisertGrunnlag.GRUNNLAG_GRUNNBELØP.benyttGrunnlag(""),
                                RegelspesifisertGrunnlag.GRUNNLAG_UFØRE_FAKTOR_HØY.benyttGrunnlag(""),
                            ),
                        ),
                        beregning(
                            Regelspesifiseringer.REGEL_SAMLET_FRADRAG,
                            listOf(
                                beregning(
                                    Regelspesifiseringer.REGEL_FRADRAG_EPS_OVER_FRIBELØP,
                                    listOf(
                                        beregning(
                                            Regelspesifiseringer.REGEL_FRADRAG_MINUS_MINST_ARBEID_OG_FORVENTET,
                                            listOf(
                                                RegelspesifisertGrunnlag.GRUNNLAG_FRADRAG.benyttGrunnlag(""),
                                            ),
                                        ),
                                    ),
                                ),
                            ),
                        ),
                    ),
                )
                val forventetToProsentAvHøySatsUføre = beregning(
                    Regelspesifiseringer.REGEL_TO_PROSENT_AV_HØY_SATS_UFØRE,
                    listOf(
                        RegelspesifisertGrunnlag.GRUNNLAG_UFØRE_FAKTOR_HØY.benyttGrunnlag(""),
                        RegelspesifisertGrunnlag.GRUNNLAG_GRUNNBELØP.benyttGrunnlag(""),
                    ),
                )
                val forventet = beregning(
                    Regelspesifiseringer.REGEL_MÅNEDSBEREGNING,
                    listOf(
                        RegelspesifisertGrunnlag.GRUNNLAG_BOSITUASJON.benyttGrunnlag(strategi.satsgrunn().name),
                        forventetSatsMinusFradrag,
                        beregning(
                            Regelspesifiseringer.REGEL_SOSIALSTØNAD_UNDER_2_PROSENT,
                            listOf(
                                forventetSatsMinusFradrag,
                                forventetToProsentAvHøySatsUføre,
                            ),
                        ),
                        beregning(
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
            val strategi = BeregningStrategy.Eps67EllerEldre(satsFactoryTestPåDato(), Sakstype.UFØRE)
            with(månedsBeregning(strategi = strategi, eps = true)) {
                val faktisk = benyttetRegel

                val forventetSatsMinusFradrag = beregning(
                    Regelspesifiseringer.REGEL_SATS_MINUS_FRADRAG_AVRUNDET,
                    avhengigeRegler = listOf(
                        beregning(
                            Regelspesifiseringer.REGEL_BEREGN_SATS_UFØRE_MÅNED,
                            listOf(
                                RegelspesifisertGrunnlag.GRUNNLAG_GRUNNBELØP.benyttGrunnlag(""),
                                RegelspesifisertGrunnlag.GRUNNLAG_UFØRE_FAKTOR_ORDINÆR.benyttGrunnlag(""),
                            ),
                        ),
                        beregning(
                            Regelspesifiseringer.REGEL_SAMLET_FRADRAG,
                            listOf(
                                beregning(
                                    Regelspesifiseringer.REGEL_FRADRAG_EPS_OVER_FRIBELØP,
                                    listOf(
                                        beregning(
                                            Regelspesifiseringer.REGEL_FRADRAG_MINUS_MINST_ARBEID_OG_FORVENTET,
                                            listOf(
                                                RegelspesifisertGrunnlag.GRUNNLAG_FRADRAG.benyttGrunnlag(""),
                                            ),
                                        ),
                                        beregning(
                                            Regelspesifiseringer.REGEL_BEREGN_SATS_ALDER_MÅNED,
                                            listOf(
                                                RegelspesifisertGrunnlag.GRUNNLAG_GARANTIPENSJON_ORDINÆR.benyttGrunnlag(
                                                    "",
                                                ),
                                            ),
                                        ),
                                    ),
                                ),
                            ),
                        ),
                    ),
                )
                val forventetToProsentAvHøySatsUføre = beregning(
                    Regelspesifiseringer.REGEL_TO_PROSENT_AV_HØY_SATS_UFØRE,
                    listOf(
                        RegelspesifisertGrunnlag.GRUNNLAG_UFØRE_FAKTOR_HØY.benyttGrunnlag(""),
                        RegelspesifisertGrunnlag.GRUNNLAG_GRUNNBELØP.benyttGrunnlag(""),
                    ),
                )
                val forventet = beregning(
                    Regelspesifiseringer.REGEL_MÅNEDSBEREGNING,
                    listOf(
                        RegelspesifisertGrunnlag.GRUNNLAG_BOSITUASJON.benyttGrunnlag(strategi.satsgrunn().name),
                        forventetSatsMinusFradrag,
                        beregning(
                            Regelspesifiseringer.REGEL_SOSIALSTØNAD_UNDER_2_PROSENT,
                            listOf(
                                forventetSatsMinusFradrag,
                                forventetToProsentAvHøySatsUføre,
                            ),
                        ),
                        beregning(
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
        fun `EPS uføre flyktning`() {
            val strategi = BeregningStrategy.EpsUnder67ÅrOgUførFlyktning(satsFactoryTestPåDato(), Sakstype.UFØRE)
            with(månedsBeregning(strategi = strategi, eps = true)) {
                val faktisk = benyttetRegel
                val forventetSatsMinusFradrag = beregning(
                    Regelspesifiseringer.REGEL_SATS_MINUS_FRADRAG_AVRUNDET,
                    avhengigeRegler = listOf(
                        beregning(
                            Regelspesifiseringer.REGEL_BEREGN_SATS_UFØRE_MÅNED,
                            listOf(
                                RegelspesifisertGrunnlag.GRUNNLAG_GRUNNBELØP.benyttGrunnlag(""),
                                RegelspesifisertGrunnlag.GRUNNLAG_UFØRE_FAKTOR_ORDINÆR.benyttGrunnlag(""),
                            ),
                        ),
                        beregning(
                            Regelspesifiseringer.REGEL_SAMLET_FRADRAG,
                            avhengigeRegler = listOf(
                                beregning(
                                    Regelspesifiseringer.REGEL_FRADRAG_EPS_OVER_FRIBELØP,
                                    avhengigeRegler = listOf(
                                        beregning(
                                            Regelspesifiseringer.REGEL_FRADRAG_MINUS_MINST_ARBEID_OG_FORVENTET,
                                            listOf(
                                                RegelspesifisertGrunnlag.GRUNNLAG_FRADRAG.benyttGrunnlag(""),
                                            ),
                                        ),
                                        beregning(
                                            Regelspesifiseringer.REGEL_BEREGN_SATS_UFØRE_MÅNED,
                                            listOf(
                                                RegelspesifisertGrunnlag.GRUNNLAG_GRUNNBELØP.benyttGrunnlag(""),
                                                RegelspesifisertGrunnlag.GRUNNLAG_UFØRE_FAKTOR_ORDINÆR.benyttGrunnlag(
                                                    "",
                                                ),
                                            ),
                                        ),
                                    ),
                                ),
                            ),
                        ),
                    ),
                )
                val forventetToProsentAvHøySatsUføre = beregning(
                    Regelspesifiseringer.REGEL_TO_PROSENT_AV_HØY_SATS_UFØRE,
                    listOf(
                        RegelspesifisertGrunnlag.GRUNNLAG_UFØRE_FAKTOR_HØY.benyttGrunnlag(""),
                        RegelspesifisertGrunnlag.GRUNNLAG_GRUNNBELØP.benyttGrunnlag(""),
                    ),
                )
                val forventet = beregning(
                    Regelspesifiseringer.REGEL_MÅNEDSBEREGNING,
                    listOf(
                        RegelspesifisertGrunnlag.GRUNNLAG_BOSITUASJON.benyttGrunnlag(strategi.satsgrunn().name),
                        forventetSatsMinusFradrag,
                        beregning(
                            Regelspesifiseringer.REGEL_SOSIALSTØNAD_UNDER_2_PROSENT,
                            listOf(
                                forventetSatsMinusFradrag,
                                forventetToProsentAvHøySatsUføre,
                            ),
                        ),
                        beregning(
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
    }

    @Nested
    inner class Alder {

        @Test
        fun `Bor alene`() {
            val strategi = BeregningStrategy.BorAlene(satsFactoryTestPåDato(), Sakstype.ALDER)
            with(månedsBeregning(strategi)) {
                val faktisk = benyttetRegel
                val forventetSatsMinusFradrag = beregning(
                    Regelspesifiseringer.REGEL_SATS_MINUS_FRADRAG_AVRUNDET,
                    avhengigeRegler = listOf(
                        beregning(
                            Regelspesifiseringer.REGEL_BEREGN_SATS_ALDER_MÅNED,
                            listOf(
                                RegelspesifisertGrunnlag.GRUNNLAG_GARANTIPENSJON_HØY.benyttGrunnlag(""),
                            ),
                        ),
                        beregning(
                            Regelspesifiseringer.REGEL_SAMLET_FRADRAG,
                            listOf(
                                RegelspesifisertGrunnlag.GRUNNLAG_FRADRAG.benyttGrunnlag(""),
                            ),
                        ),
                    ),
                )
                val forventetToProsentAvHøySatsUføre = beregning(
                    Regelspesifiseringer.REGEL_TO_PROSENT_AV_HØY_SATS_ALDER,
                    listOf(
                        RegelspesifisertGrunnlag.GRUNNLAG_GARANTIPENSJON_HØY.benyttGrunnlag(""),
                    ),
                )
                val forventet = beregning(
                    Regelspesifiseringer.REGEL_MÅNEDSBEREGNING,
                    avhengigeRegler = listOf(
                        RegelspesifisertGrunnlag.GRUNNLAG_BOSITUASJON.benyttGrunnlag(strategi.satsgrunn().name),
                        forventetSatsMinusFradrag,
                        beregning(
                            Regelspesifiseringer.REGEL_SOSIALSTØNAD_UNDER_2_PROSENT,
                            listOf(
                                forventetSatsMinusFradrag,
                                forventetToProsentAvHøySatsUføre,
                            ),
                        ),
                        beregning(
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
        fun `Bor med andre voksne`() {
            val strategi = BeregningStrategy.BorMedVoksne(satsFactoryTestPåDato(), Sakstype.ALDER)
            with(månedsBeregning(strategi = strategi, eps = true)) {
                val faktisk = benyttetRegel
                val forventetSatsMinusFradrag = beregning(
                    Regelspesifiseringer.REGEL_SATS_MINUS_FRADRAG_AVRUNDET,
                    avhengigeRegler = listOf(
                        beregning(
                            Regelspesifiseringer.REGEL_BEREGN_SATS_ALDER_MÅNED,
                            listOf(
                                RegelspesifisertGrunnlag.GRUNNLAG_GARANTIPENSJON_ORDINÆR.benyttGrunnlag(""),
                            ),
                        ),
                        beregning(
                            Regelspesifiseringer.REGEL_SAMLET_FRADRAG,
                            listOf(
                                RegelspesifisertGrunnlag.GRUNNLAG_FRADRAG.benyttGrunnlag(""),
                            ),
                        ),
                    ),
                )
                val forventetToProsentAvHøySatsUføre = beregning(
                    Regelspesifiseringer.REGEL_TO_PROSENT_AV_HØY_SATS_ALDER,
                    listOf(
                        RegelspesifisertGrunnlag.GRUNNLAG_GARANTIPENSJON_HØY.benyttGrunnlag(""),
                    ),
                )
                val forventet = beregning(
                    Regelspesifiseringer.REGEL_MÅNEDSBEREGNING,
                    listOf(
                        RegelspesifisertGrunnlag.GRUNNLAG_BOSITUASJON.benyttGrunnlag(strategi.satsgrunn().name),
                        forventetSatsMinusFradrag,
                        beregning(
                            Regelspesifiseringer.REGEL_SOSIALSTØNAD_UNDER_2_PROSENT,
                            listOf(
                                forventetSatsMinusFradrag,
                                forventetToProsentAvHøySatsUføre,
                            ),
                        ),
                        beregning(
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
        fun `EPS under 67`() {
            val strategi = BeregningStrategy.EpsUnder67År(satsFactoryTestPåDato(), Sakstype.ALDER)
            with(månedsBeregning(strategi = strategi, eps = true)) {
                val faktisk = benyttetRegel

                val forventetSatsMinusFradrag = beregning(
                    Regelspesifiseringer.REGEL_SATS_MINUS_FRADRAG_AVRUNDET,
                    avhengigeRegler = listOf(
                        beregning(
                            Regelspesifiseringer.REGEL_BEREGN_SATS_ALDER_MÅNED,
                            listOf(
                                RegelspesifisertGrunnlag.GRUNNLAG_GARANTIPENSJON_HØY.benyttGrunnlag(""),
                            ),
                        ),
                        beregning(
                            Regelspesifiseringer.REGEL_SAMLET_FRADRAG,
                            listOf(
                                beregning(
                                    Regelspesifiseringer.REGEL_FRADRAG_EPS_OVER_FRIBELØP,
                                    avhengigeRegler = listOf(
                                        RegelspesifisertGrunnlag.GRUNNLAG_FRADRAG.benyttGrunnlag(""),
                                    ),
                                ),
                            ),
                        ),
                    ),
                )
                val forventetToProsentAvHøySatsUføre = beregning(
                    Regelspesifiseringer.REGEL_TO_PROSENT_AV_HØY_SATS_ALDER,
                    listOf(
                        RegelspesifisertGrunnlag.GRUNNLAG_GARANTIPENSJON_HØY.benyttGrunnlag(""),
                    ),
                )
                val forventet = beregning(
                    Regelspesifiseringer.REGEL_MÅNEDSBEREGNING,
                    listOf(
                        RegelspesifisertGrunnlag.GRUNNLAG_BOSITUASJON.benyttGrunnlag(strategi.satsgrunn().name),
                        forventetSatsMinusFradrag,
                        beregning(
                            Regelspesifiseringer.REGEL_SOSIALSTØNAD_UNDER_2_PROSENT,
                            listOf(
                                forventetSatsMinusFradrag,
                                forventetToProsentAvHøySatsUføre,
                            ),
                        ),
                        beregning(
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
            val strategi = BeregningStrategy.Eps67EllerEldre(satsFactoryTestPåDato(), Sakstype.ALDER)
            with(månedsBeregning(strategi = strategi, eps = true)) {
                val faktisk = benyttetRegel

                val forventetSatsMinusFradrag = beregning(
                    Regelspesifiseringer.REGEL_SATS_MINUS_FRADRAG_AVRUNDET,
                    avhengigeRegler = listOf(
                        beregning(
                            Regelspesifiseringer.REGEL_BEREGN_SATS_ALDER_MÅNED,
                            listOf(
                                RegelspesifisertGrunnlag.GRUNNLAG_GARANTIPENSJON_ORDINÆR.benyttGrunnlag(""),
                            ),
                        ),
                        beregning(
                            Regelspesifiseringer.REGEL_SAMLET_FRADRAG,
                            listOf(
                                beregning(
                                    Regelspesifiseringer.REGEL_FRADRAG_EPS_OVER_FRIBELØP,
                                    listOf(
                                        RegelspesifisertGrunnlag.GRUNNLAG_FRADRAG.benyttGrunnlag(""),
                                        beregning(
                                            Regelspesifiseringer.REGEL_BEREGN_SATS_ALDER_MÅNED,
                                            listOf(
                                                RegelspesifisertGrunnlag.GRUNNLAG_GARANTIPENSJON_ORDINÆR.benyttGrunnlag(
                                                    "",
                                                ),
                                            ),
                                        ),
                                    ),
                                ),
                            ),
                        ),
                    ),
                )
                val forventetToProsentAvHøySatsUføre = beregning(
                    Regelspesifiseringer.REGEL_TO_PROSENT_AV_HØY_SATS_ALDER,
                    listOf(
                        RegelspesifisertGrunnlag.GRUNNLAG_GARANTIPENSJON_HØY.benyttGrunnlag(""),
                    ),
                )
                val forventet = beregning(
                    Regelspesifiseringer.REGEL_MÅNEDSBEREGNING,
                    listOf(
                        RegelspesifisertGrunnlag.GRUNNLAG_BOSITUASJON.benyttGrunnlag(strategi.satsgrunn().name),
                        forventetSatsMinusFradrag,
                        beregning(
                            Regelspesifiseringer.REGEL_SOSIALSTØNAD_UNDER_2_PROSENT,
                            listOf(
                                forventetSatsMinusFradrag,
                                forventetToProsentAvHøySatsUføre,
                            ),
                        ),
                        beregning(
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
        fun `EPS uføre flyktning`() {
            val strategi = BeregningStrategy.EpsUnder67ÅrOgUførFlyktning(satsFactoryTestPåDato(), Sakstype.ALDER)
            with(månedsBeregning(strategi = strategi, eps = true)) {
                val faktisk = benyttetRegel
                val forventetSatsMinusFradrag = beregning(
                    Regelspesifiseringer.REGEL_SATS_MINUS_FRADRAG_AVRUNDET,
                    avhengigeRegler = listOf(
                        beregning(
                            Regelspesifiseringer.REGEL_BEREGN_SATS_ALDER_MÅNED,
                            listOf(
                                RegelspesifisertGrunnlag.GRUNNLAG_GARANTIPENSJON_ORDINÆR.benyttGrunnlag(""),
                            ),
                        ),
                        beregning(
                            Regelspesifiseringer.REGEL_SAMLET_FRADRAG,
                            avhengigeRegler = listOf(
                                beregning(
                                    Regelspesifiseringer.REGEL_FRADRAG_EPS_OVER_FRIBELØP,
                                    avhengigeRegler = listOf(
                                        RegelspesifisertGrunnlag.GRUNNLAG_FRADRAG.benyttGrunnlag(""),
                                        beregning(
                                            Regelspesifiseringer.REGEL_BEREGN_SATS_UFØRE_MÅNED,
                                            listOf(
                                                RegelspesifisertGrunnlag.GRUNNLAG_GRUNNBELØP.benyttGrunnlag(""),
                                                RegelspesifisertGrunnlag.GRUNNLAG_UFØRE_FAKTOR_ORDINÆR.benyttGrunnlag(
                                                    "",
                                                ),
                                            ),
                                        ),
                                    ),
                                ),
                            ),
                        ),
                    ),
                )
                val forventetToProsentAvHøySatsUføre = beregning(
                    Regelspesifiseringer.REGEL_TO_PROSENT_AV_HØY_SATS_ALDER,
                    listOf(
                        RegelspesifisertGrunnlag.GRUNNLAG_GARANTIPENSJON_HØY.benyttGrunnlag(""),
                    ),
                )
                val forventet = beregning(
                    Regelspesifiseringer.REGEL_MÅNEDSBEREGNING,
                    listOf(
                        RegelspesifisertGrunnlag.GRUNNLAG_BOSITUASJON.benyttGrunnlag(strategi.satsgrunn().name),
                        forventetSatsMinusFradrag,
                        beregning(
                            Regelspesifiseringer.REGEL_SOSIALSTØNAD_UNDER_2_PROSENT,
                            listOf(
                                forventetSatsMinusFradrag,
                                forventetToProsentAvHøySatsUføre,
                            ),
                        ),
                        beregning(
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
    }

    @Nested
    inner class UnderToProsent {

        @Test
        fun `beregning blir to prosent under høy sats`() {
            val strategi = BeregningStrategy.BorAlene(satsFactoryTestPåDato(), Sakstype.ALDER)
            with(månedsBeregning(strategi, underToProsent = true)) {
                val faktisk = benyttetRegel
                val forventetSatsMinusFradrag = beregning(
                    Regelspesifiseringer.REGEL_SATS_MINUS_FRADRAG_AVRUNDET,
                    avhengigeRegler = listOf(
                        beregning(
                            Regelspesifiseringer.REGEL_BEREGN_SATS_ALDER_MÅNED,
                            listOf(
                                RegelspesifisertGrunnlag.GRUNNLAG_GARANTIPENSJON_HØY.benyttGrunnlag(""),
                            ),
                        ),
                        beregning(
                            Regelspesifiseringer.REGEL_SAMLET_FRADRAG,
                            listOf(
                                RegelspesifisertGrunnlag.GRUNNLAG_FRADRAG.benyttGrunnlag(""),
                            ),
                        ),
                    ),
                )
                val forventetToProsentAvHøySatsUføre = beregning(
                    Regelspesifiseringer.REGEL_TO_PROSENT_AV_HØY_SATS_ALDER,
                    listOf(
                        RegelspesifisertGrunnlag.GRUNNLAG_GARANTIPENSJON_HØY.benyttGrunnlag(""),
                    ),
                )
                val forventet = beregning(
                    Regelspesifiseringer.REGEL_MÅNEDSBEREGNING,
                    avhengigeRegler = listOf(
                        RegelspesifisertGrunnlag.GRUNNLAG_BOSITUASJON.benyttGrunnlag(strategi.satsgrunn().name),
                        forventetSatsMinusFradrag,
                        beregning(
                            Regelspesifiseringer.REGEL_SOSIALSTØNAD_UNDER_2_PROSENT,
                            listOf(
                                // Dette vil være to ulike beregninger men, vi verifiserer ikke resultat til beregninger, kun anvendte regler.
                                forventetSatsMinusFradrag,
                                forventetSatsMinusFradrag,
                                forventetToProsentAvHøySatsUføre,
                            ),
                        ),
                        beregning(
                            Regelspesifiseringer.REGEL_MINDRE_ENN_2_PROSENT,
                            listOf(
                                forventetSatsMinusFradrag,
                                forventetToProsentAvHøySatsUføre,
                            ),
                        ),
                    ),
                )

                val gjeldendeMånedsberegning = (faktisk as Regelspesifisering.Beregning).avhengigeRegler[1]
                val månedsberegningFørUnder2ProsentRegel =
                    (faktisk.avhengigeRegler[3] as Regelspesifisering.Beregning).avhengigeRegler[0]
                gjeldendeMånedsberegning shouldNotBe månedsberegningFørUnder2ProsentRegel

                sammenlignRegel(faktisk, forventet)
            }
        }

        @Test
        fun `beregning blir to prosent under høy sats på grunn av sosialstønad`() {
            val strategi = BeregningStrategy.BorAlene(satsFactoryTestPåDato(), Sakstype.ALDER)
            with(månedsBeregning(strategi, underToProsentSosialstønad = true)) {
                val faktisk = benyttetRegel
                val forventetSatsMinusFradrag = beregning(
                    Regelspesifiseringer.REGEL_SATS_MINUS_FRADRAG_AVRUNDET,
                    avhengigeRegler = listOf(
                        beregning(
                            Regelspesifiseringer.REGEL_BEREGN_SATS_ALDER_MÅNED,
                            listOf(
                                RegelspesifisertGrunnlag.GRUNNLAG_GARANTIPENSJON_HØY.benyttGrunnlag(""),
                            ),
                        ),
                        beregning(
                            Regelspesifiseringer.REGEL_SAMLET_FRADRAG,
                            listOf(
                                RegelspesifisertGrunnlag.GRUNNLAG_FRADRAG.benyttGrunnlag(""),
                            ),
                        ),
                    ),
                )
                val forventetToProsentAvHøySatsUføre = beregning(
                    Regelspesifiseringer.REGEL_TO_PROSENT_AV_HØY_SATS_ALDER,
                    listOf(
                        RegelspesifisertGrunnlag.GRUNNLAG_GARANTIPENSJON_HØY.benyttGrunnlag(""),
                    ),
                )
                val forventet = beregning(
                    Regelspesifiseringer.REGEL_MÅNEDSBEREGNING,
                    avhengigeRegler = listOf(
                        RegelspesifisertGrunnlag.GRUNNLAG_BOSITUASJON.benyttGrunnlag(strategi.satsgrunn().name),
                        forventetSatsMinusFradrag,
                        beregning(
                            Regelspesifiseringer.REGEL_SOSIALSTØNAD_UNDER_2_PROSENT,
                            listOf(
                                // Dette vil være to ulike beregninger men, vi verifiserer ikke resultat til beregninger, kun anvendte regler.
                                forventetSatsMinusFradrag,
                                forventetSatsMinusFradrag,
                                forventetToProsentAvHøySatsUføre,
                            ),
                        ),
                        beregning(
                            Regelspesifiseringer.REGEL_MINDRE_ENN_2_PROSENT,
                            listOf(
                                forventetSatsMinusFradrag,
                                forventetToProsentAvHøySatsUføre,
                            ),
                        ),
                    ),
                )

                val gjeldendeMånedsberegning = (faktisk as Regelspesifisering.Beregning).avhengigeRegler[1]
                val månedsberegningFørUnder2ProsentRegel =
                    (faktisk.avhengigeRegler[3] as Regelspesifisering.Beregning).avhengigeRegler[0]
                gjeldendeMånedsberegning shouldBe månedsberegningFørUnder2ProsentRegel

                sammenlignRegel(faktisk, forventet)
            }
        }
    }
}

// Innhold i verdi testes ikke. Det er kun en dump av beregningen som brukes og testes andre steder.
object ForventetRegelspesifisering {
    // frb = forventet regelspesifisert beregning
    fun beregning(regel: Regelspesifiseringer, avhengigeRegler: List<Regelspesifisering>) =
        regel.benyttRegelspesifisering(
            verdi = "",
            avhengigeRegler = avhengigeRegler,
        )

    // frg =  forventet regelspesifisert grunnlag
    fun grunnlag(grunnlag: RegelspesifisertGrunnlag) = grunnlag.benyttGrunnlag("")
}

internal fun månedsBeregning(
    strategi: BeregningStrategy,
    eps: Boolean = false,
    underToProsent: Boolean = false,
    underToProsentSosialstønad: Boolean = false,
): BeregningForMånedRegelspesifisert = månedsBeregning(
    strategi,
    when {
        eps -> listOf(
            FradragFactory.nyFradragsperiode(
                fradragstype = Fradragstype.ForventetInntekt,
                månedsbeløp = 1000.0,
                utenlandskInntekt = null,
                periode = periode,
                tilhører = FradragTilhører.BRUKER,
            ),
            FradragFactory.nyFradragsperiode(
                fradragstype = Fradragstype.ForventetInntekt,
                månedsbeløp = 1000.0,
                utenlandskInntekt = null,
                periode = periode,
                tilhører = FradragTilhører.EPS,
            ),
            FradragFactory.nyFradragsperiode(
                fradragstype = Fradragstype.Alderspensjon,
                månedsbeløp = 1000.0,
                utenlandskInntekt = null,
                periode = periode,
                tilhører = FradragTilhører.EPS,
            ),
        )

        underToProsent -> listOf(
            FradragFactory.nyFradragsperiode(
                fradragstype = Fradragstype.ForventetInntekt,
                månedsbeløp = 1000.0,
                utenlandskInntekt = null,
                periode = periode,
                tilhører = FradragTilhører.BRUKER,
            ),
            FradragFactory.nyFradragsperiode(
                fradragstype = Fradragstype.Annet("Noe for å få ytelse til å bli under 2%"),
                månedsbeløp = 14800.0,
                utenlandskInntekt = null,
                periode = periode,
                tilhører = FradragTilhører.BRUKER,
            ),
        )

        underToProsentSosialstønad -> listOf(
            FradragFactory.nyFradragsperiode(
                fradragstype = Fradragstype.ForventetInntekt,
                månedsbeløp = 1000.0,
                utenlandskInntekt = null,
                periode = periode,
                tilhører = FradragTilhører.BRUKER,
            ),
            FradragFactory.nyFradragsperiode(
                fradragstype = Fradragstype.Sosialstønad,
                månedsbeløp = 14800.0,
                utenlandskInntekt = null,
                periode = periode,
                tilhører = FradragTilhører.BRUKER,
            ),
        )

        else -> listOf(
            FradragFactory.nyFradragsperiode(
                fradragstype = Fradragstype.ForventetInntekt,
                månedsbeløp = 1000.0,
                utenlandskInntekt = null,
                periode = periode,
                tilhører = FradragTilhører.BRUKER,
            ),
        )
    },
)

internal fun månedsBeregning(
    strategi: BeregningStrategy,
    fradrag: List<Fradrag>,
): BeregningForMånedRegelspesifisert {
    val result = BeregningFactory(clock = fixedClock).ny(
        id = UUID.randomUUID(),
        opprettet = fixedTidspunkt,
        fradrag = fradrag,
        begrunnelse = "begrunnelse",
        beregningsperioder = listOf(
            Beregningsperiode(
                periode = periode,
                strategy = strategi,
            ),
        ),
    )
    return result.getMånedsberegningerMedRegel().single()
}

internal fun sammenlignRegel(forventet: Regelspesifisering, faktisk: Regelspesifisering) {
    forventet shouldBeEqualUsingFields {
        excludedProperties = setOf(
            Regelspesifisering.Beregning::benyttetTidspunkt,
            Regelspesifisering.Beregning::verdi,
            Regelspesifisering.Grunnlag::benyttetTidspunkt,
            Regelspesifisering.Grunnlag::verdi,
        )
        faktisk
    }
}
