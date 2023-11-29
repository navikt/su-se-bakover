package no.nav.su.se.bakover.domain.beregning.fradrag

import beregning.domain.fradrag.FradragFactory
import beregning.domain.fradrag.FradragForMåned
import beregning.domain.fradrag.FradragTilhører
import beregning.domain.fradrag.Fradragstype
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.januar
import no.nav.su.se.bakover.common.tid.periode.år
import no.nav.su.se.bakover.test.satsFactoryTestPåDato
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

internal class FradragStrategyTest {

    private val expectedError = "Hver måned i beregningsperioden må inneholde nøyaktig ett fradrag for brukers forventede inntekt"

    @Test
    fun `hver måned må inneholde nøyaktig ett fradrag for brukers forventede inntekt`() {
        val periode = år(2020)
        assertThrows<IllegalArgumentException> {
            FradragStrategy.Uføre.Enslig.beregn(
                fradrag = listOf(
                    lagFradrag(
                        Fradragstype.ForventetInntekt,
                        5000.0,
                        januar(2020),
                    ),
                ),
                beregningsperiode = periode,
            )
        }.let {
            it.message shouldContain expectedError
        }

        assertThrows<IllegalArgumentException> {
            FradragStrategy.Uføre.Enslig.beregn(
                fradrag = listOf(
                    lagFradrag(
                        Fradragstype.ForventetInntekt,
                        12_000.0,
                        periode,
                    ),
                    lagFradrag(
                        Fradragstype.ForventetInntekt,
                        24_000.0,
                        januar(2020),
                    ),
                ),
                beregningsperiode = periode,
            )
        }.let {
            it.message shouldContain expectedError
        }

        assertThrows<IllegalArgumentException> {
            FradragStrategy.Uføre.EpsOver67År(satsFactoryTestPåDato()).beregn(
                fradrag = listOf(
                    lagFradrag(
                        Fradragstype.ForventetInntekt,
                        5000.0,
                        januar(2020),
                    ),
                ),
                beregningsperiode = periode,
            )
        }.let {
            it.message shouldContain expectedError
        }

        assertThrows<IllegalArgumentException> {
            FradragStrategy.Uføre.EpsOver67År(satsFactoryTestPåDato()).beregn(
                fradrag = listOf(
                    lagFradrag(
                        Fradragstype.ForventetInntekt,
                        12_000.0,
                        periode,
                    ),
                    lagFradrag(
                        Fradragstype.ForventetInntekt,
                        24_000.0,
                        januar(2020),
                    ),
                ),
                beregningsperiode = periode,
            )
        }.let {
            it.message shouldContain expectedError
        }

        assertThrows<IllegalArgumentException> {
            FradragStrategy.Uføre.EpsUnder67ÅrOgUførFlyktning(satsFactoryTestPåDato()).beregn(
                fradrag = listOf(
                    lagFradrag(
                        Fradragstype.ForventetInntekt,
                        5000.0,
                        januar(2020),
                    ),
                ),
                beregningsperiode = periode,
            )
        }.let {
            it.message shouldContain expectedError
        }

        assertThrows<IllegalArgumentException> {
            FradragStrategy.Uføre.EpsUnder67ÅrOgUførFlyktning(satsFactoryTestPåDato()).beregn(
                fradrag = listOf(
                    lagFradrag(
                        Fradragstype.ForventetInntekt,
                        12_000.0,
                        periode,
                    ),
                    lagFradrag(
                        Fradragstype.ForventetInntekt,
                        24_000.0,
                        januar(2020),
                    ),
                ),
                beregningsperiode = periode,
            )
        }.let {
            it.message shouldContain expectedError
        }

        assertThrows<IllegalArgumentException> {
            FradragStrategy.Uføre.EpsUnder67År.beregn(
                fradrag = listOf(
                    lagFradrag(
                        Fradragstype.ForventetInntekt,
                        5000.0,
                        januar(2020),
                    ),
                ),
                beregningsperiode = periode,
            )
        }.let {
            it.message shouldContain expectedError
        }

        assertThrows<IllegalArgumentException> {
            FradragStrategy.Uføre.EpsUnder67År.beregn(
                fradrag = listOf(
                    lagFradrag(
                        Fradragstype.ForventetInntekt,
                        12_000.0,
                        periode,
                    ),
                    lagFradrag(
                        Fradragstype.ForventetInntekt,
                        24_000.0,
                        januar(2020),
                    ),
                ),
                beregningsperiode = periode,
            )
        }.let {
            it.message shouldContain expectedError
        }
    }

    @Nested
    inner class `fribeløp EPS` {
        val periode = januar(2020)

        @Test
        fun `EPS over 67 år bruker garantipensjonsnivå`() {
            FradragStrategy.Uføre.EpsOver67År(satsFactoryTestPåDato())
                .getEpsFribeløp(periode) shouldBe 14674.9166666.plusOrMinus(0.5)
        }

        @Test
        fun `EPS under 67 år ufør flyktning bruker ordinær SU-sats`() {
            FradragStrategy.Uføre.EpsUnder67ÅrOgUførFlyktning(satsFactoryTestPåDato())
                .getEpsFribeløp(periode) shouldBe 18973.0.plusOrMinus(0.5)
        }

        @Test
        fun `Enslig gir ikke fribeløp EPS`() {
            FradragStrategy.Uføre.Enslig
                .getEpsFribeløp(periode) shouldBe 0.0
        }

        @Test
        fun `EPS under 67 ikke ufør flyktning gir ikke fribeløp EPS`() {
            FradragStrategy.Uføre.EpsUnder67År
                .getEpsFribeløp(periode) shouldBe 0.0
        }
    }
}

internal fun lagFradrag(
    type: Fradragstype,
    beløp: Double,
    periode: Periode,
    tilhører: FradragTilhører = FradragTilhører.BRUKER,
) = FradragFactory.nyFradragsperiode(
    fradragstype = type,
    månedsbeløp = beløp,
    periode = periode,
    utenlandskInntekt = null,
    tilhører = tilhører,
)

internal fun lagPeriodisertFradrag(
    type: Fradragstype,
    beløp: Double,
    måned: Måned,
    tilhører: FradragTilhører = FradragTilhører.BRUKER,
) = FradragForMåned(
    fradragstype = type,
    månedsbeløp = beløp,
    måned = måned,
    utenlandskInntekt = null,
    tilhører = tilhører,
)
