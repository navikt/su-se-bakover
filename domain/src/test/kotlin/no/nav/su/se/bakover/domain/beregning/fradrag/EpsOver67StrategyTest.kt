package no.nav.su.se.bakover.domain.beregning.fradrag

import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.oktober
import no.nav.su.se.bakover.domain.Minstepensjonsnivå
import org.junit.jupiter.api.Test

internal class EpsOver67StrategyTest {
    @Test
    fun `EPS sin inntekt skal ikke regnes dersom det er under ordinært minstepensjonsnivå`() {
        val forventetInntekt = lagFradrag(Fradragstype.ForventetInntekt, 10.0, tilhører = FradragTilhører.BRUKER)
        val epsArbeidsinntekt = lagFradrag(Fradragstype.Arbeidsinntekt, 10.0, tilhører = FradragTilhører.EPS)
        val epsKontantstøtte = lagFradrag(Fradragstype.Kontantstøtte, 10.0, tilhører = FradragTilhører.EPS)
        val epsForventetInntekt = lagFradrag(Fradragstype.ForventetInntekt, 10.0, tilhører = FradragTilhører.EPS)

        FradragStrategy.EpsOver67År.beregn(
            fradrag = listOf(forventetInntekt, epsArbeidsinntekt, epsKontantstøtte, epsForventetInntekt)
        ).let {
            it shouldBe listOf(forventetInntekt)
        }
    }

    @Test
    fun `EPS sin inntekt skal regnes dersom det er over ordinært minstepensjonsnivå`() {
        val beløpExceedingMinstepensjonsnivå = 100000.0

        val forventetInntekt = lagFradrag(Fradragstype.ForventetInntekt, 10.0, tilhører = FradragTilhører.BRUKER)
        val epsArbeidsinntekt = lagFradrag(
            Fradragstype.Arbeidsinntekt,
            Minstepensjonsnivå.Ordinær.forDato(1.oktober(2020)) + beløpExceedingMinstepensjonsnivå,
            tilhører = FradragTilhører.EPS
        )

        val expectedEpsArbeidsinntekt = lagFradrag(
            Fradragstype.Arbeidsinntekt,
            beløpExceedingMinstepensjonsnivå,
            tilhører = FradragTilhører.EPS
        )

        FradragStrategy.EpsOver67År.beregn(
            fradrag = listOf(forventetInntekt, epsArbeidsinntekt)
        ).let {
            it shouldContainAll listOf(forventetInntekt, expectedEpsArbeidsinntekt)
        }
    }
}
