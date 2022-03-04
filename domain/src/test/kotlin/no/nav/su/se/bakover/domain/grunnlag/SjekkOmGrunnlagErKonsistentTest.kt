package no.nav.su.se.bakover.domain.grunnlag

import arrow.core.left
import arrow.core.right
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.domain.innvilgetFormueVilkår
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.generer
import no.nav.su.se.bakover.test.lagFradragsgrunnlag
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.UUID

internal class SjekkOmGrunnlagErKonsistentTest {

    private val periode = Periode.create(1.januar(2021), 31.desember(2021))

    @Nested
    inner class Uføre {
        @Test
        fun `uføregrunnlag mangler`() {
            SjekkOmGrunnlagErKonsistent.Uføre(emptyList()).resultat shouldBe setOf(Konsistensproblem.Uføre.Mangler).left()
        }
    }

    @Nested
    inner class Bosituasjon {
        @Test
        fun `bosituasjon er ufullstendig`() {
            val bosituasjon = Grunnlag.Bosituasjon.Ufullstendig.HarIkkeEps(
                id = UUID.randomUUID(),
                opprettet = fixedTidspunkt,
                periode = periode,
            )
            SjekkOmGrunnlagErKonsistent.Bosituasjon(listOf(bosituasjon)).resultat shouldBe setOf(Konsistensproblem.Bosituasjon.Ufullstendig).left()
        }

        @Test
        fun `bosituasjon har flere innslag`() {
            val bosituasjon1 = Grunnlag.Bosituasjon.Fullstendig.EktefellePartnerSamboer.Under67.UførFlyktning(
                id = UUID.randomUUID(),
                opprettet = fixedTidspunkt,
                periode = periode,
                fnr = Fnr.generer(),
                begrunnelse = "",
            )
            val bosituasjon2 = Grunnlag.Bosituasjon.Fullstendig.Enslig(
                id = UUID.randomUUID(),
                opprettet = fixedTidspunkt,
                periode = periode,
                begrunnelse = "",
            )
            SjekkOmGrunnlagErKonsistent.Bosituasjon(listOf(bosituasjon1, bosituasjon2)).resultat shouldBe setOf(
                Konsistensproblem.Bosituasjon.Flere,
            ).left()
        }

        @Test
        fun `bosituasjon mangler`() {
            SjekkOmGrunnlagErKonsistent.Bosituasjon(emptyList()).resultat shouldBe setOf(Konsistensproblem.Bosituasjon.Mangler).left()
        }
    }

    @Nested
    inner class BosituasjonOgFradrag {
        @Test
        fun `bosituasjon har flere innslag og fradrag har inntekter for eps`() {
            val bosituasjon1 = Grunnlag.Bosituasjon.Fullstendig.EktefellePartnerSamboer.Under67.UførFlyktning(
                id = UUID.randomUUID(),
                opprettet = fixedTidspunkt,
                periode = periode,
                fnr = Fnr.generer(),
                begrunnelse = "",
            )
            val bosituasjon2 = Grunnlag.Bosituasjon.Fullstendig.Enslig(
                id = UUID.randomUUID(),
                opprettet = fixedTidspunkt,
                periode = periode,
                begrunnelse = "",
            )
            val arbEps = lagFradragsgrunnlag(
                type = Fradragstype.Arbeidsinntekt,
                månedsbeløp = 5000.0,
                periode = periode,
                utenlandskInntekt = null,
                tilhører = FradragTilhører.EPS,
            )
            SjekkOmGrunnlagErKonsistent.BosituasjonOgFradrag(
                listOf(bosituasjon1, bosituasjon2),
                listOf(arbEps),
            ).resultat shouldBe setOf(
                Konsistensproblem.BosituasjonOgFradrag.FlereBosituasjonerOgFradragForEPS,
            ).left()
        }

        @Test
        fun `bosituasjon uten eps men fradrag for eps`() {
            val bosituasjon = Grunnlag.Bosituasjon.Fullstendig.Enslig(
                id = UUID.randomUUID(),
                opprettet = fixedTidspunkt,
                periode = periode,
                begrunnelse = "",
            )
            val arbEps = lagFradragsgrunnlag(
                type = Fradragstype.Arbeidsinntekt,
                månedsbeløp = 5000.0,
                periode = periode,
                utenlandskInntekt = null,
                tilhører = FradragTilhører.EPS,
            )
            SjekkOmGrunnlagErKonsistent.BosituasjonOgFradrag(
                listOf(bosituasjon),
                listOf(arbEps),
            ).resultat shouldBe setOf(
                Konsistensproblem.BosituasjonOgFradrag.IngenEPSMenFradragForEPS,
            ).left()
        }
    }

    @Nested
    inner class Fullstendig {
        @Test
        fun `fullstendig konsistenssjekk`() {
            val bosituasjon1 = Grunnlag.Bosituasjon.Fullstendig.EktefellePartnerSamboer.Under67.UførFlyktning(
                id = UUID.randomUUID(),
                opprettet = fixedTidspunkt,
                periode = periode,
                fnr = Fnr.generer(),
                begrunnelse = "",
            )
            val bosituasjon2 = Grunnlag.Bosituasjon.Fullstendig.Enslig(
                id = UUID.randomUUID(),
                opprettet = fixedTidspunkt,
                periode = periode,
                begrunnelse = "",
            )
            val arbEps = lagFradragsgrunnlag(
                type = Fradragstype.Arbeidsinntekt,
                månedsbeløp = 5000.0,
                periode = periode,
                utenlandskInntekt = null,
                tilhører = FradragTilhører.EPS,
            )
            SjekkOmGrunnlagErKonsistent(
                formuegrunnlag = emptyList(),
                uføregrunnlag = emptyList(),
                bosituasjongrunnlag = listOf(bosituasjon1, bosituasjon2),
                fradragsgrunnlag = listOf(arbEps),
            ).resultat shouldBe setOf(
                Konsistensproblem.Uføre.Mangler,
                Konsistensproblem.Bosituasjon.Flere,
                Konsistensproblem.BosituasjonOgFradrag.FlereBosituasjonerOgFradragForEPS,
            ).left()
        }
    }

    @Nested
    inner class HappyCase {
        @Test
        fun `happy case`() {
            val uføregrunnlag = Grunnlag.Uføregrunnlag(
                periode = periode,
                uføregrad = Uføregrad.parse(100),
                forventetInntekt = 0,
                opprettet = fixedTidspunkt,
            )
            val bosituasjon = Grunnlag.Bosituasjon.Fullstendig.EktefellePartnerSamboer.Under67.UførFlyktning(
                id = UUID.randomUUID(),
                opprettet = fixedTidspunkt,
                periode = periode,
                fnr = Fnr.generer(),
                begrunnelse = "",
            )
            val arbEps = lagFradragsgrunnlag(
                type = Fradragstype.Arbeidsinntekt,
                månedsbeløp = 5000.0,
                periode = periode,
                utenlandskInntekt = null,
                tilhører = FradragTilhører.EPS,
            )
            SjekkOmGrunnlagErKonsistent(
                formuegrunnlag = innvilgetFormueVilkår(periode = periode).grunnlag,
                uføregrunnlag = listOf(uføregrunnlag),
                bosituasjongrunnlag = listOf(bosituasjon),
                fradragsgrunnlag = listOf(arbEps),
            ).resultat shouldBe Unit.right()
        }
    }
}
