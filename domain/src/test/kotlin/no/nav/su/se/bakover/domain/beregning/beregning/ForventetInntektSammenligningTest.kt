package no.nav.su.se.bakover.domain.beregning.beregning

import arrow.core.nonEmptyListOf
import beregning.domain.Beregning
import beregning.domain.BeregningMedFradragBeregnetMånedsvis
import beregning.domain.ForventetInntektSammenligning.BenyttetFradrag
import beregning.domain.forventetInntektSammenligning
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.domain.extensions.toNonEmptyList
import no.nav.su.se.bakover.common.domain.regelspesifisering.Regelspesifisering
import no.nav.su.se.bakover.common.domain.regelspesifisering.Regelspesifiseringer
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.domain.tid.desember
import no.nav.su.se.bakover.common.domain.tid.januar
import no.nav.su.se.bakover.common.domain.tid.juli
import no.nav.su.se.bakover.common.domain.tid.juni
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.januar
import no.nav.su.se.bakover.common.tid.periode.juli
import no.nav.su.se.bakover.common.tid.periode.juni
import no.nav.su.se.bakover.test.beregning
import no.nav.su.se.bakover.test.bosituasjonEpsUnder67
import no.nav.su.se.bakover.test.fradragsgrunnlagArbeidsinntekt
import no.nav.su.se.bakover.test.grunnlag.nyFradragsgrunnlag
import no.nav.su.se.bakover.test.grunnlag.uføregrunnlagForventetInntekt
import org.junit.jupiter.api.Test
import vilkår.inntekt.domain.grunnlag.FradragTilhører
import vilkår.inntekt.domain.grunnlag.Fradragstype

internal class ForventetInntektSammenligningTest {

    @Test
    fun `arbeidsinntekt høyere enn forventet inntekt trekkes fra`() {
        val forventetÅrsinntekt = 12_000
        val arbeidsinntektPerMåned = 15_000.0
        val beregning = beregning(
            uføregrunnlag = nonEmptyListOf(uføregrunnlagForventetInntekt(forventetInntekt = forventetÅrsinntekt)),
            fradragsgrunnlag = listOf(fradragsgrunnlagArbeidsinntekt(arbeidsinntekt = arbeidsinntektPerMåned)),
        )

        val sammenligning = beregning.forventetInntektSammenligning(januar(2021)).shouldNotBeNull()

        sammenligning.forventetInntekt shouldBe 1_000.0
        sammenligning.sumArbeidsinntekterEtterLoven shouldBe arbeidsinntektPerMåned
        sammenligning.benyttetFradrag shouldBe BenyttetFradrag.ARBEIDSINNTEKTER
    }

    @Test
    fun `forventet inntekt høyere enn arbeidsinntekt trekkes fra`() {
        val forventetÅrsinntekt = 240_000
        val beregning = beregning(
            uføregrunnlag = nonEmptyListOf(uføregrunnlagForventetInntekt(forventetInntekt = forventetÅrsinntekt)),
            fradragsgrunnlag = listOf(fradragsgrunnlagArbeidsinntekt(arbeidsinntekt = 15_000.0)),
        )

        val sammenligning = beregning.forventetInntektSammenligning(januar(2021)).shouldNotBeNull()

        sammenligning.forventetInntekt shouldBe 20_000.0
        sammenligning.benyttetFradrag shouldBe BenyttetFradrag.FORVENTET_INNTEKT
    }

    @Test
    fun `ved like beløp trekkes forventet inntekt fra`() {
        val forventetÅrsinntekt = 180_000
        val beregning = beregning(
            uføregrunnlag = nonEmptyListOf(uføregrunnlagForventetInntekt(forventetInntekt = forventetÅrsinntekt)),
            fradragsgrunnlag = listOf(fradragsgrunnlagArbeidsinntekt(arbeidsinntekt = 15_000.0)),
        )

        val sammenligning = beregning.forventetInntektSammenligning(januar(2021)).shouldNotBeNull()

        sammenligning.forventetInntekt shouldBe 15_000.0
        sammenligning.sumArbeidsinntekterEtterLoven shouldBe 15_000.0
        sammenligning.benyttetFradrag shouldBe BenyttetFradrag.FORVENTET_INNTEKT
    }

    @Test
    fun `tar bare med søkers arbeidsinntekter etter loven, med én linje per inntekt`() {
        val beregning = beregning(
            bosituasjon = bosituasjonEpsUnder67(),
            uføregrunnlag = nonEmptyListOf(uføregrunnlagForventetInntekt(forventetInntekt = 12)),
            fradragsgrunnlag = listOf(
                fradragsgrunnlagArbeidsinntekt(arbeidsinntekt = 7_000.0),
                fradragsgrunnlagArbeidsinntekt(arbeidsinntekt = 8_000.0),
                nyFradragsgrunnlag(type = Fradragstype.Kontantstøtte, månedsbeløp = 1_000.0),
                nyFradragsgrunnlag(type = Fradragstype.Uføretrygd, månedsbeløp = 3_000.0),
                fradragsgrunnlagArbeidsinntekt(arbeidsinntekt = 20_000.0, tilhører = FradragTilhører.EPS),
            ),
        )

        val sammenligning = beregning.forventetInntektSammenligning(januar(2021)).shouldNotBeNull()

        sammenligning.arbeidsinntekterEtterLoven.map { it.fradragstype to it.månedsbeløp } shouldContainExactlyInAnyOrder listOf(
            Fradragstype.Arbeidsinntekt to 7_000.0,
            Fradragstype.Arbeidsinntekt to 8_000.0,
            Fradragstype.Kontantstøtte to 1_000.0,
        )
        sammenligning.sumArbeidsinntekterEtterLoven shouldBe 16_000.0
        sammenligning.benyttetFradrag shouldBe BenyttetFradrag.ARBEIDSINNTEKTER
    }

    @Test
    fun `følger forventet inntekt for hver måned når uføregrunnlaget endres i perioden`() {
        val førsteHalvår = Periode.create(1.januar(2021), 30.juni(2021))
        val andreHalvår = Periode.create(1.juli(2021), 31.desember(2021))
        val beregning = beregning(
            uføregrunnlag = nonEmptyListOf(
                uføregrunnlagForventetInntekt(periode = førsteHalvår, forventetInntekt = 12),
                uføregrunnlagForventetInntekt(periode = andreHalvår, forventetInntekt = 240_000),
            ),
            fradragsgrunnlag = listOf(fradragsgrunnlagArbeidsinntekt(arbeidsinntekt = 5_000.0)),
        )

        val sammenligningJuni = beregning.forventetInntektSammenligning(juni(2021)).shouldNotBeNull()
        val sammenligningJuli = beregning.forventetInntektSammenligning(juli(2021)).shouldNotBeNull()

        sammenligningJuni.forventetInntekt shouldBe 1.0
        sammenligningJuni.benyttetFradrag shouldBe BenyttetFradrag.ARBEIDSINNTEKTER
        sammenligningJuli.forventetInntekt shouldBe 20_000.0
        sammenligningJuli.benyttetFradrag shouldBe BenyttetFradrag.FORVENTET_INNTEKT
    }

    @Test
    fun `følger arbeidsinntekten for hver måned når den bare gjelder deler av perioden`() {
        val andreHalvår = Periode.create(1.juli(2021), 31.desember(2021))
        val arbeidsinntektPerMåned = 15_000.0
        val beregning = beregning(
            uføregrunnlag = nonEmptyListOf(uføregrunnlagForventetInntekt(forventetInntekt = 12)),
            fradragsgrunnlag = listOf(
                fradragsgrunnlagArbeidsinntekt(periode = andreHalvår, arbeidsinntekt = arbeidsinntektPerMåned),
            ),
        )

        beregning.forventetInntektSammenligning(juni(2021)).shouldBeNull()
        val sammenligningJuli = beregning.forventetInntektSammenligning(juli(2021)).shouldNotBeNull()

        sammenligningJuli.sumArbeidsinntekterEtterLoven shouldBe arbeidsinntektPerMåned
        sammenligningJuli.benyttetFradrag shouldBe BenyttetFradrag.ARBEIDSINNTEKTER
    }

    @Test
    fun `gir null når forventet inntekt er 0 kroner, også med registrert arbeidsinntekt på 0 kroner`() {
        val måned = januar(2021)
        val forventetÅrsinntekt = 0
        listOf(0.0, 15_000.0).forEach { arbeidsinntektPerMåned ->
            val beregning = beregning(
                uføregrunnlag = nonEmptyListOf(uføregrunnlagForventetInntekt(forventetInntekt = forventetÅrsinntekt)),
                fradragsgrunnlag = listOf(fradragsgrunnlagArbeidsinntekt(arbeidsinntekt = arbeidsinntektPerMåned)),
            )

            beregning.forventetInntektSammenligning(måned).shouldBeNull()
            beregning.getMånedsberegninger().single { it.måned == måned }.getSumFradrag() shouldBe arbeidsinntektPerMåned
        }
    }

    @Test
    fun `gir nulinl når registrert arbeidsinntekt er 0 kroner og forventet inntekt er positiv`() {
        val måned = januar(2021)
        val forventetÅrsinntekt = 12_000
        val beregning = beregning(
            uføregrunnlag = nonEmptyListOf(uføregrunnlagForventetInntekt(forventetInntekt = forventetÅrsinntekt)),
            fradragsgrunnlag = listOf(fradragsgrunnlagArbeidsinntekt(arbeidsinntekt = 0.0)),
        )

        beregning.forventetInntektSammenligning(måned).shouldBeNull()
        val månedsberegning = beregning.getMånedsberegninger().single { it.måned == måned }
        månedsberegning.getSumFradrag() shouldBe 1_000.0
        månedsberegning.getFradrag().single().fradragstype shouldBe Fradragstype.ForventetInntekt
    }

    @Test
    fun `gir null for alder`() {
        val beregning = beregning(sakstype = Sakstype.ALDER)

        beregning.forventetInntektSammenligning(januar(2021)).shouldBeNull()
    }

    @Test
    fun `gir null for eldre regelversjon og beregning uten regelspesifisering`() {
        val inntektsvalg = Regelspesifiseringer.REGEL_FRADRAG_MINUS_MINST_ARBEID_OG_FORVENTET
        // Versjon 1 sammenlignet bare med fradragstypen Arbeidsinntekt.
        val historiskVersjon = "1"
        val eldreRegelversjon = Regelspesifisering.Beregning(
            kode = inntektsvalg.kode,
            versjon = historiskVersjon,
            verdi = "",
            avhengigeRegler = emptyList(),
        )
        val beregning = beregning(
            uføregrunnlag = nonEmptyListOf(uføregrunnlagForventetInntekt(forventetInntekt = 12)),
            fradragsgrunnlag = listOf(fradragsgrunnlagArbeidsinntekt(arbeidsinntekt = 15_000.0)),
        )

        beregning.medRegel(eldreRegelversjon).forventetInntektSammenligning(januar(2021)).shouldBeNull()
        beregning.medRegel(Regelspesifisering.BeregnetUtenSpesifisering).forventetInntektSammenligning(januar(2021)).shouldBeNull()
    }

    private fun Beregning.medRegel(benyttetRegel: Regelspesifisering): Beregning =
        (this as BeregningMedFradragBeregnetMånedsvis).copy(
            månedsberegninger = getMånedsberegningerMedRegel()
                .map { it.copy(benyttetRegel = benyttetRegel) }
                .toNonEmptyList(),
        )
}
