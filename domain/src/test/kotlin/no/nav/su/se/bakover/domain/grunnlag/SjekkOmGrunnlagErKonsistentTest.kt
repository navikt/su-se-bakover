package no.nav.su.se.bakover.domain.grunnlag

import io.kotest.assertions.arrow.either.shouldBeLeft
import io.kotest.assertions.arrow.either.shouldBeRight
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.FnrGenerator
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragFactory
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.UUID

internal class SjekkOmGrunnlagErKonsistentTest {

    private val periode = Periode.create(1.januar(2021), 31.desember(2021))

    @Nested
    inner class Uføre {
        @Test
        fun `uføregrunnlag mangler`() {
            SjekkOmGrunnlagErKonsistent.Uføre(emptyList()).resultat shouldBeLeft setOf(Konsistensproblem.Uføre.Mangler)
        }
    }

    @Nested
    inner class Bosituasjon {
        @Test
        fun `bosituasjon er ufullstendig`() {
            val bosituasjon = Grunnlag.Bosituasjon.Ufullstendig.HarIkkeEps(
                id = UUID.randomUUID(),
                opprettet = Tidspunkt.now(),
                periode = periode,
            )
            SjekkOmGrunnlagErKonsistent.Bosituasjon(listOf(bosituasjon)).resultat shouldBeLeft setOf(Konsistensproblem.Bosituasjon.Ufullstendig)
        }

        @Test
        fun `bosituasjon har flere innslag`() {
            val bosituasjon1 = Grunnlag.Bosituasjon.Fullstendig.EktefellePartnerSamboer.Under67.UførFlyktning(
                id = UUID.randomUUID(),
                opprettet = Tidspunkt.now(),
                periode = periode,
                fnr = FnrGenerator.random(),
                begrunnelse = "",
            )
            val bosituasjon2 = Grunnlag.Bosituasjon.Fullstendig.Enslig(
                id = UUID.randomUUID(),
                opprettet = Tidspunkt.now(),
                periode = periode,
                begrunnelse = "",
            )
            SjekkOmGrunnlagErKonsistent.Bosituasjon(listOf(bosituasjon1, bosituasjon2)).resultat shouldBeLeft setOf(Konsistensproblem.Bosituasjon.Flere)
        }

        @Test
        fun `bosituasjon mangler`() {
            SjekkOmGrunnlagErKonsistent.Bosituasjon(emptyList()).resultat shouldBeLeft setOf(Konsistensproblem.Bosituasjon.Mangler)
        }
    }

    @Nested
    inner class BosituasjonOgFradrag {
        @Test
        fun `bosituasjon har flere innslag og fradrag har inntekter for eps`() {
            val bosituasjon1 = Grunnlag.Bosituasjon.Fullstendig.EktefellePartnerSamboer.Under67.UførFlyktning(
                id = UUID.randomUUID(),
                opprettet = Tidspunkt.now(),
                periode = periode,
                fnr = FnrGenerator.random(),
                begrunnelse = "",
            )
            val bosituasjon2 = Grunnlag.Bosituasjon.Fullstendig.Enslig(
                id = UUID.randomUUID(),
                opprettet = Tidspunkt.now(),
                periode = periode,
                begrunnelse = "",
            )
            val arbEps = Grunnlag.Fradragsgrunnlag(
                fradrag = FradragFactory.ny(
                    type = Fradragstype.Arbeidsinntekt,
                    månedsbeløp = 5000.0,
                    periode = periode,
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.EPS,
                ),
            )
            SjekkOmGrunnlagErKonsistent.BosituasjonOgFradrag(
                listOf(bosituasjon1, bosituasjon2),
                listOf(arbEps),
            ).resultat shouldBeLeft setOf(
                Konsistensproblem.BosituasjonOgFradrag.FlereBosituasjonerOgFradragForEPS,
            )
        }

        @Test
        fun `bosituasjon uten eps men fradrag for eps`() {
            val bosituasjon = Grunnlag.Bosituasjon.Fullstendig.Enslig(
                id = UUID.randomUUID(),
                opprettet = Tidspunkt.now(),
                periode = periode,
                begrunnelse = "",
            )
            val arbEps = Grunnlag.Fradragsgrunnlag(
                fradrag = FradragFactory.ny(
                    type = Fradragstype.Arbeidsinntekt,
                    månedsbeløp = 5000.0,
                    periode = periode,
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.EPS,
                ),
            )
            SjekkOmGrunnlagErKonsistent.BosituasjonOgFradrag(
                listOf(bosituasjon),
                listOf(arbEps),
            ).resultat shouldBeLeft setOf(
                Konsistensproblem.BosituasjonOgFradrag.IngenEPSMenFradragForEPS,
            )
        }
    }

    @Nested
    inner class Fullstendig {
        @Test
        fun `fullstendig konsistenssjekk`() {
            val bosituasjon1 = Grunnlag.Bosituasjon.Fullstendig.EktefellePartnerSamboer.Under67.UførFlyktning(
                id = UUID.randomUUID(),
                opprettet = Tidspunkt.now(),
                periode = periode,
                fnr = FnrGenerator.random(),
                begrunnelse = "",
            )
            val bosituasjon2 = Grunnlag.Bosituasjon.Fullstendig.Enslig(
                id = UUID.randomUUID(),
                opprettet = Tidspunkt.now(),
                periode = periode,
                begrunnelse = "",
            )
            val arbEps = Grunnlag.Fradragsgrunnlag(
                fradrag = FradragFactory.ny(
                    type = Fradragstype.Arbeidsinntekt,
                    månedsbeløp = 5000.0,
                    periode = periode,
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.EPS,
                ),
            )
            SjekkOmGrunnlagErKonsistent(
                uføregrunnlag = emptyList(),
                bosituasjongrunnlag = listOf(bosituasjon1, bosituasjon2),
                fradragsgrunnlag = listOf(arbEps),
            ).resultat shouldBeLeft setOf(
                Konsistensproblem.Uføre.Mangler,
                Konsistensproblem.Bosituasjon.Flere,
                Konsistensproblem.BosituasjonOgFradrag.FlereBosituasjonerOgFradragForEPS,
            )
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
            )
            val bosituasjon = Grunnlag.Bosituasjon.Fullstendig.EktefellePartnerSamboer.Under67.UførFlyktning(
                id = UUID.randomUUID(),
                opprettet = Tidspunkt.now(),
                periode = periode,
                fnr = FnrGenerator.random(),
                begrunnelse = "",
            )
            val arbEps = Grunnlag.Fradragsgrunnlag(
                fradrag = FradragFactory.ny(
                    type = Fradragstype.Arbeidsinntekt,
                    månedsbeløp = 5000.0,
                    periode = periode,
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.EPS,
                ),
            )
            SjekkOmGrunnlagErKonsistent(
                uføregrunnlag = listOf(uføregrunnlag),
                bosituasjongrunnlag = listOf(bosituasjon),
                fradragsgrunnlag = listOf(arbEps),
            ).resultat shouldBeRight Unit
        }
    }
}
