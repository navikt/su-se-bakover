package no.nav.su.se.bakover.domain.beregning

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import no.nav.su.se.bakover.common.april
import no.nav.su.se.bakover.common.august
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.februar
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.juli
import no.nav.su.se.bakover.common.mai
import no.nav.su.se.bakover.common.november
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragFactory
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.grunnlag.Uføregrad
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows

internal class BeregningsgrunnlagTest {
    @Test
    fun `skal legge til forventet inntekt som et månedsbeløp med en periode tilsvarende beregningsperioden 12mnd`() {
        val beregningsperiode = Periode.create(fraOgMed = 1.januar(2020), tilOgMed = 31.desember(2020))
        Beregningsgrunnlag.create(
            beregningsperiode = beregningsperiode,
            uføregrunnlag = listOf(
                Grunnlag.Uføregrunnlag(
                    periode = beregningsperiode,
                    uføregrad = Uføregrad.parse(20),
                    forventetInntekt = 120_000,
                ),
            ),
            fradragFraSaksbehandler = listOf(
                FradragFactory.ny(
                    type = Fradragstype.Kapitalinntekt,
                    månedsbeløp = 2000.0,
                    periode = beregningsperiode,
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER,
                ),
            ),
        ).fradrag shouldBe listOf(
            FradragFactory.ny(
                type = Fradragstype.Kapitalinntekt,
                månedsbeløp = 2000.0,
                periode = beregningsperiode,
                utenlandskInntekt = null,
                tilhører = FradragTilhører.BRUKER,
            ),
            FradragFactory.ny(
                type = Fradragstype.ForventetInntekt,
                månedsbeløp = 10_000.0,
                periode = beregningsperiode,
                utenlandskInntekt = null,
                tilhører = FradragTilhører.BRUKER,
            ),
        )
    }

    @Test
    fun `skal legge til forventet inntekt som et månedsbeløp med en periode tilsvarende beregningen 1mnd`() {
        val beregningsperiode = Periode.create(fraOgMed = 1.januar(2020), tilOgMed = 31.januar(2020))
        Beregningsgrunnlag.create(
            beregningsperiode = beregningsperiode,
            uføregrunnlag = listOf(
                Grunnlag.Uføregrunnlag(
                    periode = beregningsperiode,
                    uføregrad = Uføregrad.parse(20),
                    forventetInntekt = 120_000,
                ),
            ),
            fradragFraSaksbehandler = listOf(
                FradragFactory.ny(
                    type = Fradragstype.Kapitalinntekt,
                    månedsbeløp = 2000.0,
                    periode = beregningsperiode,
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER,
                ),
            ),
        ).fradrag shouldBe listOf(
            FradragFactory.ny(
                type = Fradragstype.Kapitalinntekt,
                månedsbeløp = 2000.0,
                periode = beregningsperiode,
                utenlandskInntekt = null,
                tilhører = FradragTilhører.BRUKER,
            ),
            FradragFactory.ny(
                type = Fradragstype.ForventetInntekt,
                månedsbeløp = 10_000.0,
                periode = beregningsperiode,
                utenlandskInntekt = null,
                tilhører = FradragTilhører.BRUKER,
            ),
        )
    }

    @Test
    fun `tåler at man ikke har forventet inntekt`() {
        val beregningsperiode = Periode.create(fraOgMed = 1.januar(2020), tilOgMed = 31.januar(2020))
        Beregningsgrunnlag.create(
            beregningsperiode = beregningsperiode,
            uføregrunnlag = listOf(
                Grunnlag.Uføregrunnlag(
                    periode = beregningsperiode,
                    uføregrad = Uføregrad.parse(100),
                    forventetInntekt = 0,
                ),
            ),
            fradragFraSaksbehandler = emptyList(),
        ).fradrag shouldBe listOf(
            FradragFactory.ny(
                type = Fradragstype.ForventetInntekt,
                månedsbeløp = 0.0,
                periode = beregningsperiode,
                utenlandskInntekt = null,
                tilhører = FradragTilhører.BRUKER,
            ),
        )
    }

    @Test
    fun `validerer fradrag`() {
        val beregningsperiode = Periode.create(fraOgMed = 1.januar(2020), tilOgMed = 31.januar(2020))
        assertDoesNotThrow {
            Beregningsgrunnlag.create(
                beregningsperiode = beregningsperiode,
                uføregrunnlag = listOf(
                    Grunnlag.Uføregrunnlag(
                        periode = beregningsperiode,
                        uføregrad = Uføregrad.parse(100),
                        forventetInntekt = 0,
                    ),
                ),
                fradragFraSaksbehandler = emptyList(),
            )
        }

        assertThrows<IllegalArgumentException> {
            Beregningsgrunnlag.create(
                beregningsperiode = beregningsperiode,
                uføregrunnlag = listOf(
                    Grunnlag.Uføregrunnlag(
                        periode = beregningsperiode,
                        uføregrad = Uføregrad.parse(100),
                        forventetInntekt = 0,
                    ),
                ),
                fradragFraSaksbehandler = listOf(
                    FradragFactory.ny(
                        type = Fradragstype.ForventetInntekt,
                        månedsbeløp = 0.0,
                        periode = Periode.create(1.januar(2019), 31.desember(2019)),
                        utenlandskInntekt = null,
                        tilhører = FradragTilhører.BRUKER,
                    ),
                ),
            )
        }
    }

    @Test
    fun `tillater ikke overlappende perioder med forventet inntekt`() {
        val expectedError = "OverlappendePerioderMedForventetInntekt"
        val beregningsperiode = Periode.create(1.januar(2021), 31.desember(2021))

        assertThrows<IllegalArgumentException> {
            Beregningsgrunnlag.create(
                beregningsperiode = beregningsperiode,
                uføregrunnlag = listOf(
                    Grunnlag.Uføregrunnlag(
                        periode = beregningsperiode,
                        uføregrad = Uføregrad.parse(100),
                        forventetInntekt = 2000,
                    ),
                    Grunnlag.Uføregrunnlag(
                        periode = beregningsperiode,
                        uføregrad = Uføregrad.parse(100),
                        forventetInntekt = 2000,
                    ),
                ),
                fradragFraSaksbehandler = emptyList(),
            )
        }.message shouldContain expectedError

        assertThrows<IllegalArgumentException> {
            Beregningsgrunnlag.create(
                beregningsperiode = beregningsperiode,
                uføregrunnlag = listOf(
                    Grunnlag.Uføregrunnlag(
                        periode = Periode.create(1.januar(2021), 31.desember(2021)),
                        uføregrad = Uføregrad.parse(100),
                        forventetInntekt = 2000,
                    ),
                    Grunnlag.Uføregrunnlag(
                        periode = Periode.create(1.mai(2021), 31.juli(2021)),
                        uføregrad = Uføregrad.parse(100),
                        forventetInntekt = 2000,
                    ),
                ),
                fradragFraSaksbehandler = emptyList(),
            )
        }.message shouldContain expectedError

        assertThrows<IllegalArgumentException> {
            Beregningsgrunnlag.create(
                beregningsperiode = beregningsperiode,
                uføregrunnlag = listOf(
                    Grunnlag.Uføregrunnlag(
                        periode = Periode.create(1.mai(2021), 31.juli(2021)),
                        uføregrad = Uføregrad.parse(100),
                        forventetInntekt = 2000,
                    ),
                    Grunnlag.Uføregrunnlag(
                        periode = Periode.create(1.januar(2021), 31.desember(2021)),
                        uføregrad = Uføregrad.parse(100),
                        forventetInntekt = 2000,
                    ),
                ),
                fradragFraSaksbehandler = emptyList(),
            )
        }.message shouldContain expectedError

        Beregningsgrunnlag.create(
            beregningsperiode = beregningsperiode,
            uføregrunnlag = listOf(
                Grunnlag.Uføregrunnlag(
                    periode = Periode.create(1.januar(2021), 30.april(2021)),
                    uføregrad = Uføregrad.parse(100),
                    forventetInntekt = 12_000,
                ),
                Grunnlag.Uføregrunnlag(
                    periode = Periode.create(1.mai(2021), 31.desember(2021)),
                    uføregrad = Uføregrad.parse(100),
                    forventetInntekt = 24_000,
                ),
            ),
            fradragFraSaksbehandler = emptyList(),
        ).fradrag shouldBe listOf(
            FradragFactory.ny(
                type = Fradragstype.ForventetInntekt,
                månedsbeløp = 1_000.0,
                periode = Periode.create(1.januar(2021), 30.april(2021)),
                utenlandskInntekt = null,
                tilhører = FradragTilhører.BRUKER,
            ),
            FradragFactory.ny(
                type = Fradragstype.ForventetInntekt,
                månedsbeløp = 2_000.0,
                periode = Periode.create(1.mai(2021), 31.desember(2021)),
                utenlandskInntekt = null,
                tilhører = FradragTilhører.BRUKER,
            ),
        )
    }

    @Test
    fun `forventet inntekt må være definert for hele beregningsperioden`() {
        val expectedError = "ManglerForventetInntektForEnkelteMåneder"
        val beregningsperiode = Periode.create(1.januar(2021), 31.desember(2021))

        assertThrows<IllegalArgumentException> {
            Beregningsgrunnlag.create(
                beregningsperiode = beregningsperiode,
                uføregrunnlag = listOf(
                    Grunnlag.Uføregrunnlag(
                        periode = Periode.create(1.januar(2021), 31.januar(2021)),
                        uføregrad = Uføregrad.parse(100),
                        forventetInntekt = 2000,
                    ),
                    Grunnlag.Uføregrunnlag(
                        periode = Periode.create(1.desember(2021), 31.desember(2021)),
                        uføregrad = Uføregrad.parse(100),
                        forventetInntekt = 2000,
                    ),
                ),
                fradragFraSaksbehandler = emptyList(),
            )
        }.message shouldContain expectedError

        assertThrows<IllegalArgumentException> {
            Beregningsgrunnlag.create(
                beregningsperiode = beregningsperiode,
                uføregrunnlag = listOf(
                    Grunnlag.Uføregrunnlag(
                        periode = Periode.create(1.januar(2021), 30.november(2021)),
                        uføregrad = Uføregrad.parse(100),
                        forventetInntekt = 2000,
                    ),
                ),
                fradragFraSaksbehandler = emptyList(),
            )
        }.message shouldContain expectedError

        assertThrows<IllegalArgumentException> {
            Beregningsgrunnlag.create(
                beregningsperiode = beregningsperiode,
                uføregrunnlag = listOf(
                    Grunnlag.Uføregrunnlag(
                        periode = Periode.create(1.februar(2021), 31.desember(2021)),
                        uføregrad = Uføregrad.parse(100),
                        forventetInntekt = 2000,
                    ),
                ),
                fradragFraSaksbehandler = emptyList(),
            )
        }.message shouldContain expectedError

        Beregningsgrunnlag.create(
            beregningsperiode = beregningsperiode,
            uføregrunnlag = listOf(
                Grunnlag.Uføregrunnlag(
                    periode = Periode.create(1.januar(2021), 31.januar(2021)),
                    uføregrad = Uføregrad.parse(100),
                    forventetInntekt = 12_000,
                ),
                Grunnlag.Uføregrunnlag(
                    periode = Periode.create(1.februar(2021), 31.juli(2021)),
                    uføregrad = Uføregrad.parse(100),
                    forventetInntekt = 24_000,
                ),
                Grunnlag.Uføregrunnlag(
                    periode = Periode.create(1.august(2021), 31.desember(2021)),
                    uføregrad = Uføregrad.parse(100),
                    forventetInntekt = 48_000,
                ),
            ),
            fradragFraSaksbehandler = emptyList(),
        ).fradrag shouldBe listOf(
            FradragFactory.ny(
                type = Fradragstype.ForventetInntekt,
                månedsbeløp = 1_000.0,
                periode = Periode.create(1.januar(2021), 31.januar(2021)),
                utenlandskInntekt = null,
                tilhører = FradragTilhører.BRUKER,
            ),
            FradragFactory.ny(
                type = Fradragstype.ForventetInntekt,
                månedsbeløp = 2_000.0,
                periode = Periode.create(1.februar(2021), 31.juli(2021)),
                utenlandskInntekt = null,
                tilhører = FradragTilhører.BRUKER,
            ),
            FradragFactory.ny(
                type = Fradragstype.ForventetInntekt,
                månedsbeløp = 4_000.0,
                periode = Periode.create(1.august(2021), 31.desember(2021)),
                utenlandskInntekt = null,
                tilhører = FradragTilhører.BRUKER,
            ),
        )
    }
}
