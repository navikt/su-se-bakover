package no.nav.su.se.bakover.domain.beregning.fradrag

import io.kotest.matchers.string.shouldContain
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.periode.Periode
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

internal class FradragStrategyTest {
    @Test
    fun `alle beregninger av fradrag må inneholde brukers forventede inntekt etter uførhet`() {
        assertThrows<IllegalArgumentException> {
            FradragStrategy.Enslig.beregn(
                fradrag = listOf(lagFradrag(Fradragstype.Kontantstøtte, 5000.0))
            )
        }.let {
            it.message shouldContain "Fradrag må inneholde brukers forventede inntekt etter uførhet."
        }
        assertThrows<IllegalArgumentException> {
            FradragStrategy.EpsOver67År.beregn(
                fradrag = listOf(lagFradrag(Fradragstype.Kontantstøtte, 5000.0))
            )
        }.let {
            it.message shouldContain "Fradrag må inneholde brukers forventede inntekt etter uførhet."
        }
        assertThrows<IllegalArgumentException> {
            FradragStrategy.EpsUnder67ÅrOgUførFlyktning.beregn(
                fradrag = listOf(lagFradrag(Fradragstype.Kontantstøtte, 5000.0))
            )
        }.let {
            it.message shouldContain "Fradrag må inneholde brukers forventede inntekt etter uførhet."
        }
        assertThrows<IllegalArgumentException> {
            FradragStrategy.EpsUnder67År.beregn(
                fradrag = listOf(lagFradrag(Fradragstype.Kontantstøtte, 5000.0))
            )
        }.let {
            it.message shouldContain "Fradrag må inneholde brukers forventede inntekt etter uførhet."
        }
    }
}

internal fun lagFradrag(
    type: Fradragstype,
    beløp: Double,
    periode: Periode = Periode(1.januar(2020), 31.desember(2020)),
    tilhører: FradragTilhører = FradragTilhører.BRUKER
) = FradragFactory.ny(
    type = type,
    beløp = beløp,
    periode = periode,
    utenlandskInntekt = null,
    tilhører = tilhører
)
