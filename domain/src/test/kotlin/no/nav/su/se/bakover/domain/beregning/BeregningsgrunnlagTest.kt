package no.nav.su.se.bakover.domain.beregning

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.april
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragFactory
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import org.junit.jupiter.api.Test

internal class BeregningsgrunnlagTest {
    @Test
    fun `justerer fradrag for forventet inntekt i fohold til antall måneder som skal beregnes`() {
        val forventetInntektPrÅr = 120_000.0
        val tolvMnd = Beregningsgrunnlag(
            beregningsperiode = Periode(fraOgMed = 1.januar(2020), tilOgMed = 31.desember(2020)),
            fraSaksbehandler = listOf(),
            forventetInntekt = forventetInntektPrÅr
        )
        tolvMnd.fradrag shouldBe listOf(
            FradragFactory.ny(
                type = Fradragstype.ForventetInntekt,
                beløp = 120_000.0,
                periode = Periode(fraOgMed = 1.januar(2020), tilOgMed = 31.desember(2020)),
                utenlandskInntekt = null,
                tilhører = FradragTilhører.BRUKER
            )
        )

        val enMnd = Beregningsgrunnlag(
            beregningsperiode = Periode(fraOgMed = 1.januar(2020), tilOgMed = 31.januar(2020)),
            fraSaksbehandler = listOf(),
            forventetInntekt = forventetInntektPrÅr
        )
        enMnd.fradrag shouldBe listOf(
            FradragFactory.ny(
                type = Fradragstype.ForventetInntekt,
                beløp = 10_000.0,
                periode = Periode(fraOgMed = 1.januar(2020), tilOgMed = 31.januar(2020)),
                utenlandskInntekt = null,
                tilhører = FradragTilhører.BRUKER
            )
        )

        val fireMnd = Beregningsgrunnlag(
            beregningsperiode = Periode(fraOgMed = 1.januar(2020), tilOgMed = 30.april(2020)),
            fraSaksbehandler = listOf(),
            forventetInntekt = forventetInntektPrÅr
        )
        fireMnd.fradrag shouldBe listOf(
            FradragFactory.ny(
                type = Fradragstype.ForventetInntekt,
                beløp = 40_000.0,
                periode = Periode(fraOgMed = 1.januar(2020), tilOgMed = 30.april(2020)),
                utenlandskInntekt = null,
                tilhører = FradragTilhører.BRUKER
            )
        )
    }

    @Test
    fun `håndterer at forventet inntekt er 0`() {
        val forventetInntektPrÅr = 0.0
        val tolvMnd = Beregningsgrunnlag(
            beregningsperiode = Periode(fraOgMed = 1.januar(2020), tilOgMed = 31.desember(2020)),
            fraSaksbehandler = listOf(),
            forventetInntekt = forventetInntektPrÅr
        )
        tolvMnd.fradrag shouldBe listOf(
            FradragFactory.ny(
                type = Fradragstype.ForventetInntekt,
                beløp = 0.0,
                periode = Periode(fraOgMed = 1.januar(2020), tilOgMed = 31.desember(2020)),
                utenlandskInntekt = null,
                tilhører = FradragTilhører.BRUKER
            )
        )
    }
}
