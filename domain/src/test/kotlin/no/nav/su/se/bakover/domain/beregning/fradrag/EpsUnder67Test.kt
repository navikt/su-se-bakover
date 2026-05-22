package no.nav.su.se.bakover.domain.beregning.fradrag

import beregning.domain.fradrag.FradragStrategy
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.maps.shouldHaveSize
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.domain.tid.desember
import no.nav.su.se.bakover.common.domain.tid.mai
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.januar
import no.nav.su.se.bakover.common.tid.periode.år
import org.junit.jupiter.api.Test
import vilkår.inntekt.domain.grunnlag.FradragTilhører.BRUKER
import vilkår.inntekt.domain.grunnlag.FradragTilhører.EPS
import vilkår.inntekt.domain.grunnlag.Fradragstype
import vilkår.inntekt.domain.grunnlag.Fradragstype.Arbeidsinntekt
import vilkår.inntekt.domain.grunnlag.Fradragstype.BeregnetFradragEPS
import vilkår.inntekt.domain.grunnlag.Fradragstype.ForventetInntekt
import vilkår.inntekt.domain.grunnlag.Fradragstype.Kapitalinntekt
import vilkår.inntekt.domain.grunnlag.Fradragstype.NAVytelserTilLivsopphold
import vilkår.inntekt.domain.grunnlag.Fradragstype.PrivatPensjon

internal class EpsUnder67Test {
    @Test
    fun `velger arbeidsinntekt dersom den er større enn forventet inntekt`() {
        val periode = år(2020)
        val arbeidsinntekt = lagFradrag(Arbeidsinntekt, 2000.0, periode)
        val navYtelserTilLivsopphold = lagFradrag(NAVytelserTilLivsopphold, 500.0, periode)
        val forventetInntekt = lagFradrag(ForventetInntekt, 500.0, periode)

        val expectedArbeidsinntekt =
            lagPeriodisertFradrag(Arbeidsinntekt, 2000.0, januar(2020))
        val expectedNavYtelserTilLivsopphold =
            lagPeriodisertFradrag(NAVytelserTilLivsopphold, 500.0, januar(2020))

        FradragStrategy.Uføre.EpsUnder67År.beregn(
            fradrag = listOf(arbeidsinntekt, navYtelserTilLivsopphold, forventetInntekt),
            beregningsperiode = periode,
        ).let {
            it.size shouldBe 12
            it[januar(2020)]!!.verdi shouldContainAll listOf(
                expectedArbeidsinntekt,
                expectedNavYtelserTilLivsopphold,
            )
            it.values.forEach { månedsfradrag ->
                månedsfradrag.verdi.none { it.fradragstype == ForventetInntekt } shouldBe true
                månedsfradrag.verdi.any { it.fradragstype == NAVytelserTilLivsopphold } shouldBe true
            }
        }
    }

    @Test
    fun `velger forventet inntekt dersom den er større enn arbeidsinntekt`() {
        val periode = år(2020)
        val arbeidsinntekt = lagFradrag(Arbeidsinntekt, 500.0, periode)
        val navYtelserTilLivsopphold = lagFradrag(NAVytelserTilLivsopphold, 500.0, periode)
        val forventetInntekt = lagFradrag(ForventetInntekt, 2000.0, periode)

        val expectedForventetInntekt =
            lagPeriodisertFradrag(ForventetInntekt, 2000.0, januar(2020))
        val expectedOppholdtilLivsytelser =
            lagPeriodisertFradrag(NAVytelserTilLivsopphold, 500.0, januar(2020))

        FradragStrategy.Uføre.EpsUnder67År.beregn(
            fradrag = listOf(arbeidsinntekt, navYtelserTilLivsopphold, forventetInntekt),
            beregningsperiode = periode,
        ).let {
            it.size shouldBe 12
            it[januar(2020)]!!.verdi shouldContainAll listOf(
                expectedForventetInntekt,
                expectedOppholdtilLivsytelser,
            )
            it.values.forEach { månedsfradrag ->
                månedsfradrag.verdi.none { it.fradragstype == Arbeidsinntekt } shouldBe true
                månedsfradrag.verdi.any { it.fradragstype == NAVytelserTilLivsopphold } shouldBe true
            }
        }
    }

    @Test
    fun `samlet sum av brukers arbeidsinntekter etter loven større enn forventet inntekt og velges som fradrag - bor med EPS under 67`() {
        val periode = år(2020)
        val arbeidsinntekt = lagFradrag(Arbeidsinntekt, 800.0, periode)
        val sykepenger = lagFradrag(Fradragstype.Sykepenger, 800.0, periode)
        val dagpenger = lagFradrag(Fradragstype.Dagpenger, 800.0, periode)
        val forventetInntekt = lagFradrag(ForventetInntekt, 2000.0, periode)
        // Samlet arbeidsinntekt etter loven: 800 + 800 + 800 = 2400 > forventet inntekt 2000

        val expectedArbeidsinntekt = lagPeriodisertFradrag(Arbeidsinntekt, 800.0, januar(2020))
        val expectedSykepenger = lagPeriodisertFradrag(Fradragstype.Sykepenger, 800.0, januar(2020))
        val expectedDagpenger = lagPeriodisertFradrag(Fradragstype.Dagpenger, 800.0, januar(2020))

        FradragStrategy.Uføre.EpsUnder67År.beregn(
            fradrag = listOf(arbeidsinntekt, sykepenger, dagpenger, forventetInntekt),
            beregningsperiode = periode,
        ).let {
            it.size shouldBe 12
            it[januar(2020)]!!.verdi shouldContainAll listOf(
                expectedArbeidsinntekt,
                expectedSykepenger,
                expectedDagpenger,
            )
            it.values.forEach { månedsfradrag ->
                månedsfradrag.verdi.none { it.fradragstype == ForventetInntekt } shouldBe true
            }
        }
    }

    @Test
    fun `forventet inntekt større enn samlet sum av brukers arbeidsinntekter etter loven og velges som fradrag - bor med EPS under 67`() {
        val periode = år(2020)
        val arbeidsinntekt = lagFradrag(Arbeidsinntekt, 300.0, periode)
        val sykepenger = lagFradrag(Fradragstype.Sykepenger, 300.0, periode)
        val dagpenger = lagFradrag(Fradragstype.Dagpenger, 300.0, periode)
        val forventetInntekt = lagFradrag(ForventetInntekt, 2000.0, periode)
        // Samlet arbeidsinntekt etter loven: 300 + 300 + 300 = 900 < forventet inntekt 2000

        val expectedForventetInntekt = lagPeriodisertFradrag(ForventetInntekt, 2000.0, januar(2020))

        FradragStrategy.Uføre.EpsUnder67År.beregn(
            fradrag = listOf(arbeidsinntekt, sykepenger, dagpenger, forventetInntekt),
            beregningsperiode = periode,
        ).let {
            it.size shouldBe 12
            it[januar(2020)]!!.verdi shouldContainAll listOf(expectedForventetInntekt)
            it.values.forEach { månedsfradrag ->
                månedsfradrag.verdi.none { it.fradragstype == Arbeidsinntekt } shouldBe true
                månedsfradrag.verdi.none { it.fradragstype == Fradragstype.Sykepenger } shouldBe true
                månedsfradrag.verdi.none { it.fradragstype == Fradragstype.Dagpenger } shouldBe true
            }
        }
    }

    @Test
    fun `tar med fradrag som tilhører EPS`() {
        val periode = år(2020)
        val epsArbeidsinntekt = lagFradrag(Arbeidsinntekt, 2000.0, periode, tilhører = EPS)
        val forventetInntekt = lagFradrag(ForventetInntekt, 1000.0, periode)

        val expectedBrukerInntekt =
            lagPeriodisertFradrag(ForventetInntekt, 1000.0, januar(2020))
        val expectedEpsInntekt = lagPeriodisertFradrag(
            BeregnetFradragEPS,
            2000.0,
            januar(2020),
            EPS,
        )

        FradragStrategy.Uføre.EpsUnder67År.beregn(
            fradrag = listOf(epsArbeidsinntekt, forventetInntekt),
            beregningsperiode = periode,
        ).let {
            it.size shouldBe 12
            it[januar(2020)]!!.verdi shouldBe listOf(
                expectedBrukerInntekt,
                expectedEpsInntekt,
            )
            it.values.forEach { it.verdi.any { it.tilhører == BRUKER } shouldBe true }
            it.values.forEach { it.verdi.any { it.tilhører == EPS } shouldBe true }
        }
    }

    @Test
    fun `inneholder bare ett fradrag for eps, uavhengig av hvor mange som er input`() {
        val periode = år(2020)
        val forventetInntekt = lagFradrag(ForventetInntekt, 10000.0, periode)
        val epsForventetInntekt = lagFradrag(ForventetInntekt, 150000.0, periode, tilhører = EPS)
        val epsUføretrygd = lagFradrag(NAVytelserTilLivsopphold, 150000.0, periode, tilhører = EPS)
        val epsArbeidsinntekt = lagFradrag(Arbeidsinntekt, 5000.0, periode, tilhører = EPS)
        val epsKapitalinntekt = lagFradrag(Kapitalinntekt, 60000.0, periode, tilhører = EPS)
        val epsPensjon = lagFradrag(PrivatPensjon, 15000.0, periode, tilhører = EPS)

        FradragStrategy.Uføre.EpsUnder67År.beregn(
            fradrag = listOf(
                forventetInntekt,
                epsForventetInntekt,
                epsUføretrygd,
                epsArbeidsinntekt,
                epsKapitalinntekt,
                epsPensjon,
            ),
            beregningsperiode = periode,
        ).let { fradrag ->
            fradrag.size shouldBe 12
            fradrag.values.forEach { alleFradrag ->
                alleFradrag.verdi.filter { it.tilhører == EPS }.let { epsFradrag ->
                    epsFradrag shouldHaveSize 1
                    epsFradrag.all { it.fradragstype == BeregnetFradragEPS } shouldBe true
                }
            }
        }
    }

    @Test
    fun `sosialstønad for EPS går til fradrag uavhengig av om det eksisterer et fribeløp`() {
        val periode = Periode.create(1.mai(2021), 31.desember(2021))

        FradragStrategy.Uføre.EpsUnder67År.beregn(
            fradrag = listOf(
                lagFradrag(ForventetInntekt, 0.0, periode),
                lagFradrag(Fradragstype.Sosialstønad, 5000.0, periode, EPS),
            ),
            beregningsperiode = periode,
        ).let {
            it shouldHaveSize 8
            it.values.sumOf { it.verdi.sumOf { it.månedsbeløp } }
        } shouldBe 8 * 5000
    }
}
