package no.nav.su.se.bakover.domain.beregning.fradrag

import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import no.nav.su.se.bakover.common.periode.Måned
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.periode.januar
import no.nav.su.se.bakover.common.periode.år
import no.nav.su.se.bakover.test.satsFactoryTest
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

internal class FradragStrategyTest {

    private val expectedError = "Hver måned i beregningsperioden må inneholde nøyaktig ett fradrag for brukers forventede inntekt"

    @Test
    fun `hver måned må inneholde nøyaktig ett fradrag for brukers forventede inntekt`() {
        val periode = år(2020)
        assertThrows<IllegalArgumentException> {
            FradragStrategy.Enslig.beregn(
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
            FradragStrategy.Enslig.beregn(
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
            FradragStrategy.EpsOver67År.beregn(
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
            FradragStrategy.EpsOver67År.beregn(
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
            FradragStrategy.EpsUnder67ÅrOgUførFlyktning(satsFactoryTest).beregn(
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
            FradragStrategy.EpsUnder67ÅrOgUførFlyktning(satsFactoryTest).beregn(
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
            FradragStrategy.EpsUnder67År.beregn(
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
            FradragStrategy.EpsUnder67År.beregn(
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
            FradragStrategy.EpsOver67År
                .getEpsFribeløp(periode) shouldBe 14674.9166666.plusOrMinus(0.5)
        }

        @Test
        fun `EPS under 67 år ufør flyktning bruker ordinær SU-sats`() {
            FradragStrategy.EpsUnder67ÅrOgUførFlyktning(satsFactoryTest)
                .getEpsFribeløp(periode) shouldBe 18973.0.plusOrMinus(0.5)
        }

        @Test
        fun `Enslig gir ikke fribeløp EPS`() {
            FradragStrategy.Enslig
                .getEpsFribeløp(periode) shouldBe 0.0
        }

        @Test
        fun `EPS under 67 ikke ufør flyktning gir ikke fribeløp EPS`() {
            FradragStrategy.EpsUnder67År
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
