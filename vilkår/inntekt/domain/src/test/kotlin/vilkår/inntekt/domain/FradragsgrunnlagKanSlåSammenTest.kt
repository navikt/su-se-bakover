package vilkår.inntekt.domain

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.tid.periode.februar
import no.nav.su.se.bakover.common.tid.periode.januar
import no.nav.su.se.bakover.common.tid.periode.mai
import no.nav.su.se.bakover.common.tid.periode.mars
import no.nav.su.se.bakover.test.grunnlag.nyFradragsgrunnlag
import org.junit.jupiter.api.Test
import vilkår.inntekt.domain.grunnlag.FradragTilhører
import vilkår.inntekt.domain.grunnlag.Fradragstype
import vilkår.inntekt.domain.grunnlag.UtenlandskInntekt

internal class FradragsgrunnlagKanSlåSammenTest {

    @Test
    fun `fradragstype må være lik`() {
        val dagpenger1 = nyFradragsgrunnlag(type = Fradragstype.Dagpenger)
        val sosialstønad1 = nyFradragsgrunnlag(type = Fradragstype.Sosialstønad)
        val sosialstønad2 = nyFradragsgrunnlag(type = Fradragstype.Sosialstønad)
        dagpenger1.kanSlåSammen(sosialstønad1) shouldBe false
        sosialstønad1.kanSlåSammen(sosialstønad2) shouldBe true
    }

    @Test
    fun `utenlandsinntekt må være lik`() {
        val utenlandsInntektnull1 = nyFradragsgrunnlag(utenlandskInntekt = null)
        val utenlandsInntektnull2 = nyFradragsgrunnlag(utenlandskInntekt = null)
        val utenlandskInntektSwe1 =
            nyFradragsgrunnlag(utenlandskInntekt = UtenlandskInntekt.create(1000, "SEK", 1.0))
        val utenlandskInntektSwe2 =
            nyFradragsgrunnlag(utenlandskInntekt = UtenlandskInntekt.create(1000, "SEK", 1.0))
        val utenlandskInntektDK = nyFradragsgrunnlag(utenlandskInntekt = UtenlandskInntekt.create(1000, "DK", 1.1))

        utenlandsInntektnull1.kanSlåSammen(utenlandsInntektnull2) shouldBe true
        utenlandsInntektnull2.kanSlåSammen(utenlandskInntektSwe1) shouldBe false
        utenlandskInntektSwe1.kanSlåSammen(utenlandskInntektSwe2) shouldBe true
        utenlandskInntektSwe1.kanSlåSammen(utenlandskInntektDK) shouldBe false
    }

    @Test
    fun `tilhører må være lik`() {
        val bruker1 = nyFradragsgrunnlag(tilhører = FradragTilhører.BRUKER)
        val bruker2 = nyFradragsgrunnlag(tilhører = FradragTilhører.BRUKER)
        val eps1 = nyFradragsgrunnlag(tilhører = FradragTilhører.EPS)
        val eps2 = nyFradragsgrunnlag(tilhører = FradragTilhører.EPS)

        bruker1.kanSlåSammen(bruker2) shouldBe true
        bruker1.kanSlåSammen(eps1) shouldBe false
        eps1.kanSlåSammen(eps2) shouldBe true
    }

    @Test
    fun `månedsbeløp må være lik`() {
        val f1 = nyFradragsgrunnlag(månedsbeløp = 100.0)
        val f2 = nyFradragsgrunnlag(månedsbeløp = 100.0)
        val f3 = nyFradragsgrunnlag(månedsbeløp = 200.0)

        f1.kanSlåSammen(f2) shouldBe true
        f1.kanSlåSammen(f3) shouldBe false
    }

    @Test
    fun `perioden må tilstøte eller overlappe`() {
        val f1 = nyFradragsgrunnlag(periode = januar(2021))
        val f2 = nyFradragsgrunnlag(periode = januar(2021))
        val f3 = nyFradragsgrunnlag(periode = februar(2021))
        val f4 = nyFradragsgrunnlag(periode = mars(2021))
        val f5 = nyFradragsgrunnlag(periode = mai(2021))
        f1.kanSlåSammen(f2) shouldBe true
        f2.kanSlåSammen(f3) shouldBe true
        f3.kanSlåSammen(f4) shouldBe true
        f4.kanSlåSammen(f5) shouldBe false
    }
}
