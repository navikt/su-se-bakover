package no.nav.su.se.bakover.domain.beregning

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragFactory
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import org.junit.jupiter.api.Test

internal class BeregningsgrunnlagTest {
    @Test
    fun `skal legge til forventet inntekt som et månedsbeløp med en periode tilsvarende beregningsperioden 12mnd`() {
        val beregningsperiode = Periode(fraOgMed = 1.januar(2020), tilOgMed = 31.desember(2020))
        Beregningsgrunnlag(
            periode = beregningsperiode,
            forventetInntektPerÅr = 120_000.0,
            fradragFraSaksbehandler = listOf(
                FradragFactory.ny(
                    type = Fradragstype.Kapitalinntekt,
                    månedsbeløp = 2000.0,
                    periode = beregningsperiode,
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER
                )
            )
        ).fradrag shouldBe listOf(
            FradragFactory.ny(
                type = Fradragstype.Kapitalinntekt,
                månedsbeløp = 2000.0,
                periode = beregningsperiode,
                utenlandskInntekt = null,
                tilhører = FradragTilhører.BRUKER
            ),
            FradragFactory.ny(
                type = Fradragstype.ForventetInntekt,
                månedsbeløp = 10_000.0,
                periode = beregningsperiode,
                utenlandskInntekt = null,
                tilhører = FradragTilhører.BRUKER
            )
        )
    }

    @Test
    fun `skal legge til forventet inntekt som et månedsbeløp med en periode tilsvarende beregningen 1mnd`() {
        val beregningsperiode = Periode(fraOgMed = 1.januar(2020), tilOgMed = 31.januar(2020))
        Beregningsgrunnlag(
            periode = beregningsperiode,
            forventetInntektPerÅr = 120_000.0,
            fradragFraSaksbehandler = listOf(
                FradragFactory.ny(
                    type = Fradragstype.Kapitalinntekt,
                    månedsbeløp = 2000.0,
                    periode = beregningsperiode,
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER
                )
            )
        ).fradrag shouldBe listOf(
            FradragFactory.ny(
                type = Fradragstype.Kapitalinntekt,
                månedsbeløp = 2000.0,
                periode = beregningsperiode,
                utenlandskInntekt = null,
                tilhører = FradragTilhører.BRUKER
            ),
            FradragFactory.ny(
                type = Fradragstype.ForventetInntekt,
                månedsbeløp = 10_000.0,
                periode = beregningsperiode,
                utenlandskInntekt = null,
                tilhører = FradragTilhører.BRUKER
            )
        )
    }

    @Test
    fun `tåler at man ikke har forventet inntekt`() {
        val beregningsperiode = Periode(fraOgMed = 1.januar(2020), tilOgMed = 31.januar(2020))
        Beregningsgrunnlag(
            periode = beregningsperiode,
            forventetInntektPerÅr = 0.0,
            fradragFraSaksbehandler = emptyList()
        ).fradrag shouldBe listOf(
            FradragFactory.ny(
                type = Fradragstype.ForventetInntekt,
                månedsbeløp = 0.0,
                periode = beregningsperiode,
                utenlandskInntekt = null,
                tilhører = FradragTilhører.BRUKER
            )
        )
    }
}
