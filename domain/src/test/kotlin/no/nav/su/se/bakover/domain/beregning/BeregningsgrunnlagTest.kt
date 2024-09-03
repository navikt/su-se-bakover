package no.nav.su.se.bakover.domain.beregning

import arrow.core.left
import beregning.domain.Beregningsgrunnlag
import beregning.domain.UgyldigBeregningsgrunnlag
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.domain.tid.april
import no.nav.su.se.bakover.common.domain.tid.august
import no.nav.su.se.bakover.common.domain.tid.desember
import no.nav.su.se.bakover.common.domain.tid.februar
import no.nav.su.se.bakover.common.domain.tid.januar
import no.nav.su.se.bakover.common.domain.tid.juli
import no.nav.su.se.bakover.common.domain.tid.mai
import no.nav.su.se.bakover.common.domain.tid.november
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.desember
import no.nav.su.se.bakover.common.tid.periode.februar
import no.nav.su.se.bakover.common.tid.periode.januar
import no.nav.su.se.bakover.common.tid.periode.november
import no.nav.su.se.bakover.common.tid.periode.år
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.lagFradragsgrunnlag
import org.junit.jupiter.api.Test
import vilkår.inntekt.domain.grunnlag.FradragFactory
import vilkår.inntekt.domain.grunnlag.FradragTilhører
import vilkår.inntekt.domain.grunnlag.Fradragstype
import vilkår.uføre.domain.Uføregrad
import vilkår.uføre.domain.Uføregrunnlag

internal class BeregningsgrunnlagTest {
    @Test
    fun `skal legge til forventet inntekt som et månedsbeløp med en periode tilsvarende beregningsperioden 12mnd`() {
        val beregningsperiode = år(2020)
        Beregningsgrunnlag.create(
            beregningsperiode = beregningsperiode,
            uføregrunnlag = listOf(
                Uføregrunnlag(
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
                Uføregrunnlag(
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
                Uføregrunnlag(
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
                Uføregrunnlag(
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
                Uføregrunnlag(
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
                Uføregrunnlag(
                    periode = beregningsperiode,
                    uføregrad = Uføregrad.parse(100),
                    forventetInntekt = 2000,
                    opprettet = fixedTidspunkt,
                ),
                Uføregrunnlag(
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
                Uføregrunnlag(
                    periode = år(2021),
                    uføregrad = Uføregrad.parse(100),
                    forventetInntekt = 2000,
                    opprettet = fixedTidspunkt,
                ),
                Uføregrunnlag(
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
                Uføregrunnlag(
                    periode = Periode.create(1.mai(2021), 31.juli(2021)),
                    uføregrad = Uføregrad.parse(100),
                    forventetInntekt = 2000,
                    opprettet = fixedTidspunkt,
                ),
                Uføregrunnlag(
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
                Uføregrunnlag(
                    periode = Periode.create(1.januar(2021), 30.april(2021)),
                    uføregrad = Uføregrad.parse(100),
                    forventetInntekt = 12_000,
                    opprettet = fixedTidspunkt,
                ),
                Uføregrunnlag(
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
                Uføregrunnlag(
                    periode = januar(2021),
                    uføregrad = Uføregrad.parse(100),
                    forventetInntekt = 2000,
                    opprettet = fixedTidspunkt,
                ),
                Uføregrunnlag(
                    periode = desember(2021),
                    uføregrad = Uføregrad.parse(100),
                    forventetInntekt = 2000,
                    opprettet = fixedTidspunkt,
                ),
            ),
            fradragFraSaksbehandler = emptyList(),
        ) shouldBe UgyldigBeregningsgrunnlag.ManglerForventetInntektForEnkelteMåneder((februar(2021)..november(2021)).måneder()).left()

        Beregningsgrunnlag.tryCreate(
            beregningsperiode = beregningsperiode,
            uføregrunnlag = listOf(
                Uføregrunnlag(
                    periode = Periode.create(1.januar(2021), 30.november(2021)),
                    uføregrad = Uføregrad.parse(100),
                    forventetInntekt = 2000,
                    opprettet = fixedTidspunkt,
                ),
            ),
            fradragFraSaksbehandler = emptyList(),
        ) shouldBe UgyldigBeregningsgrunnlag.ManglerForventetInntektForEnkelteMåneder(listOf(desember(2021))).left()

        Beregningsgrunnlag.tryCreate(
            beregningsperiode = beregningsperiode,
            uføregrunnlag = listOf(
                Uføregrunnlag(
                    periode = Periode.create(1.februar(2021), 31.desember(2021)),
                    uføregrad = Uføregrad.parse(100),
                    forventetInntekt = 2000,
                    opprettet = fixedTidspunkt,
                ),
            ),
            fradragFraSaksbehandler = emptyList(),
        ) shouldBe UgyldigBeregningsgrunnlag.ManglerForventetInntektForEnkelteMåneder(januar(2021).måneder()).left()

        Beregningsgrunnlag.create(
            beregningsperiode = beregningsperiode,
            uføregrunnlag = listOf(
                Uføregrunnlag(
                    periode = januar(2021),
                    uføregrad = Uføregrad.parse(100),
                    forventetInntekt = 12_000,
                    opprettet = fixedTidspunkt,
                ),
                Uføregrunnlag(
                    periode = Periode.create(1.februar(2021), 31.juli(2021)),
                    uføregrad = Uføregrad.parse(100),
                    forventetInntekt = 24_000,
                    opprettet = fixedTidspunkt,
                ),
                Uføregrunnlag(
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
