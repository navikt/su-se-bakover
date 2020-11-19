package no.nav.su.se.bakover.domain.beregning.fradrag

import io.kotest.matchers.string.shouldContain
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.periode.Periode
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

internal class FradragStrategyTest {
    @Test
    fun `hver delperiode må inneholde nøyaktig ett fradrag for brukers forventede inntekt`() {
        val periode = Periode(1.januar(2020), 31.desember(2020))
        assertThrows<IllegalArgumentException> {
            FradragStrategy.Enslig.beregn(
                fradrag = listOf(
                    lagFradrag(
                        Fradragstype.ForventetInntekt,
                        5000.0,
                        Periode(1.januar(2020), 31.januar(2020))
                    )
                ),
                beregningsperiode = periode
            )
        }.let {
            it.message shouldContain "Hele beregningsperioden må inneholde fradrag for brukers forventede inntekt etter uførhet."
        }
        assertThrows<IllegalArgumentException> {
            FradragStrategy.EpsOver67År.beregn(
                fradrag = listOf(
                    lagFradrag(
                        Fradragstype.ForventetInntekt,
                        5000.0,
                        Periode(1.januar(2020), 31.januar(2020))
                    )
                ),
                beregningsperiode = periode
            )
        }.let {
            it.message shouldContain "Hele beregningsperioden må inneholde fradrag for brukers forventede inntekt etter uførhet."
        }
        assertThrows<IllegalArgumentException> {
            FradragStrategy.EpsUnder67ÅrOgUførFlyktning.beregn(
                fradrag = listOf(
                    lagFradrag(
                        Fradragstype.ForventetInntekt,
                        5000.0,
                        Periode(1.januar(2020), 31.januar(2020))
                    )
                ),
                beregningsperiode = periode
            )
        }.let {
            it.message shouldContain "Hele beregningsperioden må inneholde fradrag for brukers forventede inntekt etter uførhet."
        }
        assertThrows<IllegalArgumentException> {
            FradragStrategy.EpsUnder67År.beregn(
                fradrag = listOf(
                    lagFradrag(
                        Fradragstype.ForventetInntekt,
                        5000.0,
                        Periode(1.januar(2020), 31.januar(2020))
                    )
                ),
                beregningsperiode = periode
            )
        }.let {
            it.message shouldContain "Hele beregningsperioden må inneholde fradrag for brukers forventede inntekt etter uførhet."
        }
    }
}

internal fun lagFradrag(
    type: Fradragstype,
    beløp: Double,
    periode: Periode,
    tilhører: FradragTilhører = FradragTilhører.BRUKER
) = FradragFactory.ny(
    type = type,
    beløp = beløp,
    periode = periode,
    utenlandskInntekt = null,
    tilhører = tilhører
)
