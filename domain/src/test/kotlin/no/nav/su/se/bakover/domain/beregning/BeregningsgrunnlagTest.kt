package no.nav.su.se.bakover.domain.beregning

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.april
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.juli
import no.nav.su.se.bakover.common.mars
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
            forventetInntektPrÅr = forventetInntektPrÅr
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
            forventetInntektPrÅr = forventetInntektPrÅr
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
            forventetInntektPrÅr = forventetInntektPrÅr
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
            forventetInntektPrÅr = forventetInntektPrÅr
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

    @Test
    fun `oppjusterer fradragsbeløp i forhold til hvor mange måneder fradraget gjelder for`() {
        val tolvMndPeriode = Periode(fraOgMed = 1.januar(2020), tilOgMed = 31.desember(2020))
        val tolvMnd = Beregningsgrunnlag(
            beregningsperiode = tolvMndPeriode,
            fraSaksbehandler = listOf(
                FradragFactory.ny(
                    type = Fradragstype.Kapitalinntekt,
                    beløp = 10_000.0,
                    periode = tolvMndPeriode,
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER
                )
            ),
            forventetInntektPrÅr = 0.0
        )

        tolvMnd.fradrag.filterNot { it.getFradragstype() == Fradragstype.ForventetInntekt } shouldBe listOf(
            FradragFactory.ny(
                type = Fradragstype.Kapitalinntekt,
                beløp = 120_000.0,
                periode = tolvMndPeriode,
                utenlandskInntekt = null,
                tilhører = FradragTilhører.BRUKER
            )
        )

        val enMndPeriode = Periode(fraOgMed = 1.januar(2020), tilOgMed = 31.januar(2020))
        val enMnd = Beregningsgrunnlag(
            beregningsperiode = enMndPeriode,
            fraSaksbehandler = listOf(
                FradragFactory.ny(
                    type = Fradragstype.Kapitalinntekt,
                    beløp = 10_000.0,
                    periode = enMndPeriode,
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER
                )
            ),
            forventetInntektPrÅr = 0.0
        )

        enMnd.fradrag.filterNot { it.getFradragstype() == Fradragstype.ForventetInntekt } shouldBe listOf(
            FradragFactory.ny(
                type = Fradragstype.Kapitalinntekt,
                beløp = 10_000.0,
                periode = enMndPeriode,
                utenlandskInntekt = null,
                tilhører = FradragTilhører.BRUKER
            )
        )
    }

    @Test
    fun `oppjusterer beløp for fradrag basert på fradragets periode`() {
        val beregningsgrunnlag = Beregningsgrunnlag(
            beregningsperiode = Periode(1.januar(2020), 31.desember(2020)),
            fraSaksbehandler = listOf(
                FradragFactory.ny(
                    type = Fradragstype.Kapitalinntekt,
                    beløp = 10_000.0,
                    periode = Periode(1.januar(2020), 31.januar(2020)),
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER
                ),
                FradragFactory.ny(
                    type = Fradragstype.PrivatPensjon,
                    beløp = 5_000.0,
                    periode = Periode(1.mars(2020), 31.juli(2020)),
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER
                )
            ),
            forventetInntektPrÅr = 0.0
        )

        beregningsgrunnlag.fradrag
            .filter { it.getFradragstype() == Fradragstype.Kapitalinntekt } shouldBe listOf(
            FradragFactory.ny(
                type = Fradragstype.Kapitalinntekt,
                beløp = 10_000.0,
                periode = Periode(1.januar(2020), 31.januar(2020)),
                utenlandskInntekt = null,
                tilhører = FradragTilhører.BRUKER
            )
        )

        beregningsgrunnlag.fradrag
            .filter { it.getFradragstype() == Fradragstype.PrivatPensjon } shouldBe listOf(
            FradragFactory.ny(
                type = Fradragstype.PrivatPensjon,
                beløp = 25_000.0,
                periode = Periode(1.mars(2020), 31.juli(2020)),
                utenlandskInntekt = null,
                tilhører = FradragTilhører.BRUKER
            )
        )
    }
}
