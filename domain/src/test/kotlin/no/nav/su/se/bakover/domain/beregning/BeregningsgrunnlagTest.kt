package no.nav.su.se.bakover.domain.beregning

import arrow.core.left
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.april
import no.nav.su.se.bakover.common.august
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.februar
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.juli
import no.nav.su.se.bakover.common.mai
import no.nav.su.se.bakover.common.november
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.beregning.fradrag.F
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragFactory
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.grunnlag.Uføregrad
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.lagFradragsgrunnlag
import no.nav.su.se.bakover.test.månedsperiodeDesember2021
import no.nav.su.se.bakover.test.månedsperiodeJanuar2020
import no.nav.su.se.bakover.test.månedsperiodeJanuar2021
import org.junit.jupiter.api.Test

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
                    opprettet = fixedTidspunkt,
                ),
            ),
            fradragFraSaksbehandler = listOf(
                lagFradragsgrunnlag(
                    type = Fradragstype(F.Kapitalinntekt),
                    månedsbeløp = 2000.0,
                    periode = beregningsperiode,
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER,
                ),
            ),
        ).fradrag shouldBe listOf(
            FradragFactory.ny(
                type = Fradragstype(F.Kapitalinntekt),
                månedsbeløp = 2000.0,
                periode = beregningsperiode,
                utenlandskInntekt = null,
                tilhører = FradragTilhører.BRUKER,
            ),
            FradragFactory.ny(
                type = Fradragstype(F.ForventetInntekt),
                månedsbeløp = 10_000.0,
                periode = beregningsperiode,
                utenlandskInntekt = null,
                tilhører = FradragTilhører.BRUKER,
            ),
        )
    }

    @Test
    fun `skal legge til forventet inntekt som et månedsbeløp med en periode tilsvarende beregningen 1mnd`() {
        val beregningsperiode = månedsperiodeJanuar2020
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
                    type = Fradragstype(F.Kapitalinntekt),
                    månedsbeløp = 2000.0,
                    periode = beregningsperiode,
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER,
                ),
            ),
        ).fradrag shouldBe listOf(
            FradragFactory.ny(
                type = Fradragstype(F.Kapitalinntekt),
                månedsbeløp = 2000.0,
                periode = beregningsperiode,
                utenlandskInntekt = null,
                tilhører = FradragTilhører.BRUKER,
            ),
            FradragFactory.ny(
                type = Fradragstype(F.ForventetInntekt),
                månedsbeløp = 10_000.0,
                periode = beregningsperiode,
                utenlandskInntekt = null,
                tilhører = FradragTilhører.BRUKER,
            ),
        )
    }

    @Test
    fun `tåler at man ikke har forventet inntekt`() {
        val beregningsperiode = månedsperiodeJanuar2020
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
            FradragFactory.ny(
                type = Fradragstype(F.ForventetInntekt),
                månedsbeløp = 0.0,
                periode = beregningsperiode,
                utenlandskInntekt = null,
                tilhører = FradragTilhører.BRUKER,
            ),
        )
    }

    @Test
    fun `validerer fradrag`() {
        val beregningsperiode = månedsperiodeJanuar2020
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
        ).isRight() shouldBe true

        Beregningsgrunnlag.tryCreate(
            beregningsperiode = beregningsperiode,
            uføregrunnlag = listOf(
                Grunnlag.Uføregrunnlag(
                    periode = Periode.create(1.januar(2019), 31.desember(2019)),
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
        val beregningsperiode = Periode.create(1.januar(2021), 31.desember(2021))

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
                    periode = Periode.create(1.januar(2021), 31.desember(2021)),
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
                    periode = Periode.create(1.januar(2021), 31.desember(2021)),
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
            FradragFactory.ny(
                type = Fradragstype(F.ForventetInntekt),
                månedsbeløp = 1_000.0,
                periode = Periode.create(1.januar(2021), 30.april(2021)),
                utenlandskInntekt = null,
                tilhører = FradragTilhører.BRUKER,
            ),
            FradragFactory.ny(
                type = Fradragstype(F.ForventetInntekt),
                månedsbeløp = 2_000.0,
                periode = Periode.create(1.mai(2021), 31.desember(2021)),
                utenlandskInntekt = null,
                tilhører = FradragTilhører.BRUKER,
            ),
        )
    }

    @Test
    fun `forventet inntekt må være definert for hele beregningsperioden`() {
        val beregningsperiode = Periode.create(1.januar(2021), 31.desember(2021))

        Beregningsgrunnlag.tryCreate(
            beregningsperiode = beregningsperiode,
            uføregrunnlag = listOf(
                Grunnlag.Uføregrunnlag(
                    periode = månedsperiodeJanuar2021,
                    uføregrad = Uføregrad.parse(100),
                    forventetInntekt = 2000,
                    opprettet = fixedTidspunkt,
                ),
                Grunnlag.Uføregrunnlag(
                    periode = månedsperiodeDesember2021,
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
                    periode = månedsperiodeJanuar2021,
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
            FradragFactory.ny(
                type = Fradragstype(F.ForventetInntekt),
                månedsbeløp = 1_000.0,
                periode = månedsperiodeJanuar2021,
                utenlandskInntekt = null,
                tilhører = FradragTilhører.BRUKER,
            ),
            FradragFactory.ny(
                type = Fradragstype(F.ForventetInntekt),
                månedsbeløp = 2_000.0,
                periode = Periode.create(1.februar(2021), 31.juli(2021)),
                utenlandskInntekt = null,
                tilhører = FradragTilhører.BRUKER,
            ),
            FradragFactory.ny(
                type = Fradragstype(F.ForventetInntekt),
                månedsbeløp = 4_000.0,
                periode = Periode.create(1.august(2021), 31.desember(2021)),
                utenlandskInntekt = null,
                tilhører = FradragTilhører.BRUKER,
            ),
        )
    }
}
