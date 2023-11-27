package no.nav.su.se.bakover.domain.beregning

import arrow.core.left
import behandling.domain.beregning.fradrag.Fradragstype
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.extensions.april
import no.nav.su.se.bakover.common.extensions.august
import no.nav.su.se.bakover.common.extensions.desember
import no.nav.su.se.bakover.common.extensions.februar
import no.nav.su.se.bakover.common.extensions.januar
import no.nav.su.se.bakover.common.extensions.juli
import no.nav.su.se.bakover.common.extensions.mai
import no.nav.su.se.bakover.common.extensions.november
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.desember
import no.nav.su.se.bakover.common.tid.periode.januar
import no.nav.su.se.bakover.common.tid.periode.år
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragFactory
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.grunnlag.Uføregrad
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.lagFradragsgrunnlag
import org.junit.jupiter.api.Test

internal class BeregningsgrunnlagTest {
    @Test
    fun `skal legge til forventet inntekt som et månedsbeløp med en periode tilsvarende beregningsperioden 12mnd`() {
        val beregningsperiode = år(2020)
        Beregningsgrunnlag.create(
            beregningsperiode = beregningsperiode,
            uføregrunnlag = listOf(
                Grunnlag.Uføregrunnlag(
                    periode = beregningsperiode,
                    uføregrad = Uføregrad.parse(20),
                    forventetInntekt = 120_000,
                    opprettet = fixedTidspunkt,
                ),
            ),
            fradragFraSaksbehandler = listOf(
                lagFradragsgrunnlag(
                    type = Fradragstype.Kapitalinntekt,
                    månedsbeløp = 2000.0,
                    periode = beregningsperiode,
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER,
                ),
            ),
        ).fradrag shouldBe listOf(
            FradragFactory.nyFradragsperiode(
                fradragstype = Fradragstype.Kapitalinntekt,
                månedsbeløp = 2000.0,
                periode = beregningsperiode,
                utenlandskInntekt = null,
                tilhører = FradragTilhører.BRUKER,
            ),
            FradragFactory.nyFradragsperiode(
                fradragstype = Fradragstype.ForventetInntekt,
                månedsbeløp = 10_000.0,
                periode = beregningsperiode,
                utenlandskInntekt = null,
                tilhører = FradragTilhører.BRUKER,
            ),
        )
    }

    @Test
    fun `skal legge til forventet inntekt som et månedsbeløp med en periode tilsvarende beregningen 1mnd`() {
        val beregningsperiode = januar(2020)
        Beregningsgrunnlag.create(
            beregningsperiode = beregningsperiode,
            uføregrunnlag = listOf(
                Grunnlag.Uføregrunnlag(
                    periode = beregningsperiode,
                    uføregrad = Uføregrad.parse(20),
                    forventetInntekt = 120_000,
                    opprettet = fixedTidspunkt,
                ),
            ),
            fradragFraSaksbehandler = listOf(
                lagFradragsgrunnlag(
                    type = Fradragstype.Kapitalinntekt,
                    månedsbeløp = 2000.0,
                    periode = beregningsperiode,
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER,
                ),
            ),
        ).fradrag shouldBe listOf(
            FradragFactory.nyFradragsperiode(
                fradragstype = Fradragstype.Kapitalinntekt,
                månedsbeløp = 2000.0,
                periode = beregningsperiode,
                utenlandskInntekt = null,
                tilhører = FradragTilhører.BRUKER,
            ),
            FradragFactory.nyFradragsperiode(
                fradragstype = Fradragstype.ForventetInntekt,
                månedsbeløp = 10_000.0,
                periode = beregningsperiode,
                utenlandskInntekt = null,
                tilhører = FradragTilhører.BRUKER,
            ),
        )
    }

    @Test
    fun `tåler at man ikke har forventet inntekt`() {
        val beregningsperiode = januar(2020)
        Beregningsgrunnlag.create(
            beregningsperiode = beregningsperiode,
            uføregrunnlag = listOf(
                Grunnlag.Uføregrunnlag(
                    periode = beregningsperiode,
                    uføregrad = Uføregrad.parse(100),
                    forventetInntekt = 0,
                    opprettet = fixedTidspunkt,
                ),
            ),
            fradragFraSaksbehandler = emptyList(),
        ).fradrag shouldBe listOf(
            FradragFactory.nyFradragsperiode(
                fradragstype = Fradragstype.ForventetInntekt,
                månedsbeløp = 0.0,
                periode = beregningsperiode,
                utenlandskInntekt = null,
                tilhører = FradragTilhører.BRUKER,
            ),
        )
    }

    @Test
    fun `validerer fradrag`() {
        val beregningsperiode = januar(2020)
        Beregningsgrunnlag.tryCreate(
            beregningsperiode = beregningsperiode,
            uføregrunnlag = listOf(
                Grunnlag.Uføregrunnlag(
                    periode = beregningsperiode,
                    uføregrad = Uføregrad.parse(100),
                    forventetInntekt = 0,
                    opprettet = fixedTidspunkt,
                ),
            ),
            fradragFraSaksbehandler = emptyList(),
        ).shouldBeRight()

        Beregningsgrunnlag.tryCreate(
            beregningsperiode = beregningsperiode,
            uføregrunnlag = listOf(
                Grunnlag.Uføregrunnlag(
                    periode = år(2019),
                    uføregrad = Uføregrad.parse(100),
                    forventetInntekt = 0,
                    opprettet = fixedTidspunkt,
                ),
            ),
            fradragFraSaksbehandler = emptyList(),
        ) shouldBe UgyldigBeregningsgrunnlag.IkkeLovMedFradragUtenforPerioden.left()
    }

    @Test
    fun `tillater ikke overlappende perioder med forventet inntekt`() {
        val beregningsperiode = år(2021)

        Beregningsgrunnlag.tryCreate(
            beregningsperiode = beregningsperiode,
            uføregrunnlag = listOf(
                Grunnlag.Uføregrunnlag(
                    periode = beregningsperiode,
                    uføregrad = Uføregrad.parse(100),
                    forventetInntekt = 2000,
                    opprettet = fixedTidspunkt,
                ),
                Grunnlag.Uføregrunnlag(
                    periode = beregningsperiode,
                    uføregrad = Uføregrad.parse(100),
                    forventetInntekt = 2000,
                    opprettet = fixedTidspunkt,
                ),
            ),
            fradragFraSaksbehandler = emptyList(),
        ) shouldBe UgyldigBeregningsgrunnlag.OverlappendePerioderMedForventetInntekt.left()

        Beregningsgrunnlag.tryCreate(
            beregningsperiode = beregningsperiode,
            uføregrunnlag = listOf(
                Grunnlag.Uføregrunnlag(
                    periode = år(2021),
                    uføregrad = Uføregrad.parse(100),
                    forventetInntekt = 2000,
                    opprettet = fixedTidspunkt,
                ),
                Grunnlag.Uføregrunnlag(
                    periode = Periode.create(1.mai(2021), 31.juli(2021)),
                    uføregrad = Uføregrad.parse(100),
                    forventetInntekt = 2000,
                    opprettet = fixedTidspunkt,
                ),
            ),
            fradragFraSaksbehandler = emptyList(),
        ) shouldBe UgyldigBeregningsgrunnlag.OverlappendePerioderMedForventetInntekt.left()

        Beregningsgrunnlag.tryCreate(
            beregningsperiode = beregningsperiode,
            uføregrunnlag = listOf(
                Grunnlag.Uføregrunnlag(
                    periode = Periode.create(1.mai(2021), 31.juli(2021)),
                    uføregrad = Uføregrad.parse(100),
                    forventetInntekt = 2000,
                    opprettet = fixedTidspunkt,
                ),
                Grunnlag.Uføregrunnlag(
                    periode = år(2021),
                    uføregrad = Uføregrad.parse(100),
                    forventetInntekt = 2000,
                    opprettet = fixedTidspunkt,
                ),
            ),
            fradragFraSaksbehandler = emptyList(),
        ) shouldBe UgyldigBeregningsgrunnlag.OverlappendePerioderMedForventetInntekt.left()

        Beregningsgrunnlag.create(
            beregningsperiode = beregningsperiode,
            uføregrunnlag = listOf(
                Grunnlag.Uføregrunnlag(
                    periode = Periode.create(1.januar(2021), 30.april(2021)),
                    uføregrad = Uføregrad.parse(100),
                    forventetInntekt = 12_000,
                    opprettet = fixedTidspunkt,
                ),
                Grunnlag.Uføregrunnlag(
                    periode = Periode.create(1.mai(2021), 31.desember(2021)),
                    uføregrad = Uføregrad.parse(100),
                    forventetInntekt = 24_000,
                    opprettet = fixedTidspunkt,
                ),
            ),
            fradragFraSaksbehandler = emptyList(),
        ).fradrag shouldBe listOf(
            FradragFactory.nyFradragsperiode(
                fradragstype = Fradragstype.ForventetInntekt,
                månedsbeløp = 1_000.0,
                periode = Periode.create(1.januar(2021), 30.april(2021)),
                utenlandskInntekt = null,
                tilhører = FradragTilhører.BRUKER,
            ),
            FradragFactory.nyFradragsperiode(
                fradragstype = Fradragstype.ForventetInntekt,
                månedsbeløp = 2_000.0,
                periode = Periode.create(1.mai(2021), 31.desember(2021)),
                utenlandskInntekt = null,
                tilhører = FradragTilhører.BRUKER,
            ),
        )
    }

    @Test
    fun `forventet inntekt må være definert for hele beregningsperioden`() {
        val beregningsperiode = år(2021)

        Beregningsgrunnlag.tryCreate(
            beregningsperiode = beregningsperiode,
            uføregrunnlag = listOf(
                Grunnlag.Uføregrunnlag(
                    periode = januar(2021),
                    uføregrad = Uføregrad.parse(100),
                    forventetInntekt = 2000,
                    opprettet = fixedTidspunkt,
                ),
                Grunnlag.Uføregrunnlag(
                    periode = desember(2021),
                    uføregrad = Uføregrad.parse(100),
                    forventetInntekt = 2000,
                    opprettet = fixedTidspunkt,
                ),
            ),
            fradragFraSaksbehandler = emptyList(),
        ) shouldBe UgyldigBeregningsgrunnlag.ManglerForventetInntektForEnkelteMåneder.left()

        Beregningsgrunnlag.tryCreate(
            beregningsperiode = beregningsperiode,
            uføregrunnlag = listOf(
                Grunnlag.Uføregrunnlag(
                    periode = Periode.create(1.januar(2021), 30.november(2021)),
                    uføregrad = Uføregrad.parse(100),
                    forventetInntekt = 2000,
                    opprettet = fixedTidspunkt,
                ),
            ),
            fradragFraSaksbehandler = emptyList(),
        ) shouldBe UgyldigBeregningsgrunnlag.ManglerForventetInntektForEnkelteMåneder.left()

        Beregningsgrunnlag.tryCreate(
            beregningsperiode = beregningsperiode,
            uføregrunnlag = listOf(
                Grunnlag.Uføregrunnlag(
                    periode = Periode.create(1.februar(2021), 31.desember(2021)),
                    uføregrad = Uføregrad.parse(100),
                    forventetInntekt = 2000,
                    opprettet = fixedTidspunkt,
                ),
            ),
            fradragFraSaksbehandler = emptyList(),
        ) shouldBe UgyldigBeregningsgrunnlag.ManglerForventetInntektForEnkelteMåneder.left()

        Beregningsgrunnlag.create(
            beregningsperiode = beregningsperiode,
            uføregrunnlag = listOf(
                Grunnlag.Uføregrunnlag(
                    periode = januar(2021),
                    uføregrad = Uføregrad.parse(100),
                    forventetInntekt = 12_000,
                    opprettet = fixedTidspunkt,
                ),
                Grunnlag.Uføregrunnlag(
                    periode = Periode.create(1.februar(2021), 31.juli(2021)),
                    uføregrad = Uføregrad.parse(100),
                    forventetInntekt = 24_000,
                    opprettet = fixedTidspunkt,
                ),
                Grunnlag.Uføregrunnlag(
                    periode = Periode.create(1.august(2021), 31.desember(2021)),
                    uføregrad = Uføregrad.parse(100),
                    forventetInntekt = 48_000,
                    opprettet = fixedTidspunkt,
                ),
            ),
            fradragFraSaksbehandler = emptyList(),
        ).fradrag shouldBe listOf(
            FradragFactory.nyFradragsperiode(
                fradragstype = Fradragstype.ForventetInntekt,
                månedsbeløp = 1_000.0,
                periode = januar(2021),
                utenlandskInntekt = null,
                tilhører = FradragTilhører.BRUKER,
            ),
            FradragFactory.nyFradragsperiode(
                fradragstype = Fradragstype.ForventetInntekt,
                månedsbeløp = 2_000.0,
                periode = Periode.create(1.februar(2021), 31.juli(2021)),
                utenlandskInntekt = null,
                tilhører = FradragTilhører.BRUKER,
            ),
            FradragFactory.nyFradragsperiode(
                fradragstype = Fradragstype.ForventetInntekt,
                månedsbeløp = 4_000.0,
                periode = Periode.create(1.august(2021), 31.desember(2021)),
                utenlandskInntekt = null,
                tilhører = FradragTilhører.BRUKER,
            ),
        )
    }
}
