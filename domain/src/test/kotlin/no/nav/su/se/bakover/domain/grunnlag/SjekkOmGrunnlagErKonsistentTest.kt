package no.nav.su.se.bakover.domain.grunnlag

import arrow.core.left
import arrow.core.right
import beregning.domain.fradrag.FradragTilhører
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.extensions.april
import no.nav.su.se.bakover.common.extensions.desember
import no.nav.su.se.bakover.common.extensions.januar
import no.nav.su.se.bakover.common.extensions.juni
import no.nav.su.se.bakover.common.extensions.mai
import no.nav.su.se.bakover.common.extensions.november
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.år
import no.nav.su.se.bakover.test.arbeidsinntekt
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.fullstendigMedEPS
import no.nav.su.se.bakover.test.fullstendigUtenEPS
import no.nav.su.se.bakover.test.ufullstendigEnslig
import no.nav.su.se.bakover.test.vilkår.innvilgetFormueVilkår
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import vilkår.uføre.domain.Uføregrad
import vilkår.uføre.domain.Uføregrunnlag

internal class SjekkOmGrunnlagErKonsistentTest {

    private val periode = år(2021)

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
            SjekkOmGrunnlagErKonsistent.Bosituasjon(
                listOf(
                    ufullstendigEnslig(periode),
                ),
            ).resultat shouldBe setOf(Konsistensproblem.Bosituasjon.Ufullstendig).left()
        }

        @Test
        fun `bosituasjon mangler`() {
            SjekkOmGrunnlagErKonsistent.Bosituasjon(emptyList()).resultat shouldBe setOf(Konsistensproblem.Bosituasjon.Mangler).left()
        }

        @Test
        fun `bosituasjon overlapper`() {
            SjekkOmGrunnlagErKonsistent.Bosituasjon(
                listOf(
                    fullstendigUtenEPS(år(2021)),
                    fullstendigUtenEPS(Periode.create(1.mai(2021), 31.desember(2021))),
                ),
            ).resultat shouldBe setOf(Konsistensproblem.Bosituasjon.Overlapp).left()
        }

        @Test
        fun `happy path`() {
            SjekkOmGrunnlagErKonsistent.Bosituasjon(
                listOf(
                    fullstendigUtenEPS(Periode.create(1.januar(2021), 30.april(2021))),
                    fullstendigMedEPS(Periode.create(1.mai(2021), 31.desember(2021))),
                ),
            ).resultat shouldBe Unit.right()
        }
    }

    @Nested
    inner class BosituasjonOgFradrag {

        @Test
        fun `ugyldig bosituasjon`() {
            SjekkOmGrunnlagErKonsistent.BosituasjonOgFradrag(
                listOf(ufullstendigEnslig(periode)),
                listOf(arbeidsinntekt(periode, FradragTilhører.BRUKER)),
            ).resultat shouldBe setOf(
                Konsistensproblem.BosituasjonOgFradrag.UgyldigBosituasjon(
                    setOf(Konsistensproblem.Bosituasjon.Ufullstendig),
                ),
            ).left()
        }

        @Test
        fun `fradragsperioder utenfor bosituasjonsperioder`() {
            SjekkOmGrunnlagErKonsistent.BosituasjonOgFradrag(
                listOf(fullstendigMedEPS(periode)),
                listOf(arbeidsinntekt(Periode.create(1.januar(2021), 31.mai(2023)), FradragTilhører.BRUKER)),
            ).resultat shouldBe setOf(
                Konsistensproblem.BosituasjonOgFradrag.IngenBosituasjonForFradragsperiode,
            ).left()
        }

        @Test
        fun `ikke samsvar mellom bosituasjon og formue for eps`() {
            SjekkOmGrunnlagErKonsistent.BosituasjonOgFradrag(
                listOf(fullstendigUtenEPS(periode)),
                listOf(arbeidsinntekt(periode, FradragTilhører.EPS)),
            ).resultat shouldBe setOf(
                Konsistensproblem.BosituasjonOgFradrag.KombinasjonAvBosituasjonOgFradragErUgyldig,
            ).left()
        }

        @Test
        fun `ikke samsvar mellom bosituasjon og formue for eps variant`() {
            SjekkOmGrunnlagErKonsistent.BosituasjonOgFradrag(
                listOf(
                    fullstendigUtenEPS(Periode.create(1.januar(2021), 31.mai(2021))),
                    fullstendigMedEPS(Periode.create(1.juni(2021), 31.desember(2021))),
                ),
                listOf(arbeidsinntekt(periode, FradragTilhører.EPS)),
            ).resultat shouldBe setOf(
                Konsistensproblem.BosituasjonOgFradrag.KombinasjonAvBosituasjonOgFradragErUgyldig,
            ).left()
        }

        @Test
        fun `happy path`() {
            SjekkOmGrunnlagErKonsistent.BosituasjonOgFradrag(
                listOf(
                    fullstendigUtenEPS(Periode.create(1.januar(2021), 31.mai(2021))),
                    fullstendigMedEPS(Periode.create(1.juni(2021), 31.desember(2021))),
                ),
                listOf(
                    arbeidsinntekt(periode, FradragTilhører.BRUKER),
                    arbeidsinntekt(Periode.create(1.november(2021), 31.desember(2021)), FradragTilhører.EPS),
                ),
            ).resultat shouldBe Unit.right()
        }

        @Test
        fun `fravær av fradrag er ok`() {
            SjekkOmGrunnlagErKonsistent.BosituasjonOgFradrag(
                listOf(
                    fullstendigUtenEPS(periode),
                ),
                listOf(),
            ).resultat shouldBe Unit.right()
        }

        @Test
        fun `fravær av fradrag er med eps er ok`() {
            SjekkOmGrunnlagErKonsistent.BosituasjonOgFradrag(
                listOf(
                    fullstendigMedEPS(periode),
                ),
                listOf(),
            ).resultat shouldBe Unit.right()
        }
    }

    @Nested
    inner class Fullstendig {
        @Test
        fun `fullstendig konsistenssjekk`() {
            SjekkOmGrunnlagErKonsistent(
                formuegrunnlag = emptyList(),
                uføregrunnlag = emptyList(),
                bosituasjongrunnlag = listOf(fullstendigMedEPS(periode), fullstendigUtenEPS(periode)),
                fradragsgrunnlag = listOf(arbeidsinntekt(periode, FradragTilhører.EPS)),
            ).resultat shouldBe setOf(
                Konsistensproblem.Uføre.Mangler,
                Konsistensproblem.Formue.Mangler,
                Konsistensproblem.Bosituasjon.Overlapp,
                Konsistensproblem.BosituasjonOgFradrag.UgyldigBosituasjon(setOf(Konsistensproblem.Bosituasjon.Overlapp)),
            ).left()
        }

        @Test
        fun `fullstendig sjekk variant`() {
            SjekkOmGrunnlagErKonsistent(
                formuegrunnlag = innvilgetFormueVilkår(
                    periode = periode,
                    bosituasjon = fullstendigMedEPS(periode),
                ).grunnlag,
                uføregrunnlag = emptyList(),
                bosituasjongrunnlag = listOf(fullstendigUtenEPS(periode)),
                fradragsgrunnlag = listOf(
                    arbeidsinntekt(
                        periode = Periode.create(1.januar(2021), 31.mai(2022)),
                        FradragTilhører.EPS,
                    ),
                ),
            ).resultat shouldBe setOf(
                Konsistensproblem.Uføre.Mangler,
                Konsistensproblem.BosituasjonOgFradrag.KombinasjonAvBosituasjonOgFradragErUgyldig,
                Konsistensproblem.BosituasjonOgFradrag.IngenBosituasjonForFradragsperiode,
                Konsistensproblem.BosituasjonOgFormue.KombinasjonAvBosituasjonOgFormueErUyldig,
                Konsistensproblem.BosituasjonOgFormue.FormueForEPSManglerForBosituasjonsperiode,
            ).left()
        }
    }

    @Nested
    inner class HappyCase {
        @Test
        fun `happy case`() {
            val uføregrunnlag = Uføregrunnlag(
                periode = periode,
                uføregrad = Uføregrad.parse(100),
                forventetInntekt = 0,
                opprettet = fixedTidspunkt,
            )
            val bosituasjon = fullstendigMedEPS(periode)
            SjekkOmGrunnlagErKonsistent(
                formuegrunnlag = innvilgetFormueVilkår(periode = periode, bosituasjon = bosituasjon).grunnlag,
                uføregrunnlag = listOf(uføregrunnlag),
                bosituasjongrunnlag = listOf(bosituasjon),
                fradragsgrunnlag = listOf(arbeidsinntekt(periode, FradragTilhører.EPS)),
            ).resultat shouldBe Unit.right()
        }
    }

    @Nested
    inner class BosituasjonOgFormue {
        @Test
        fun `ikke samsvar mellom perioder for bosituasjon og formue`() {
            val janApr = Periode.create(1.januar(2021), 30.april(2021))
            val maiDes = Periode.create(1.mai(2021), 31.desember(2021))

            SjekkOmGrunnlagErKonsistent.BosituasjonOgFormue(
                bosituasjon = listOf(fullstendigUtenEPS(janApr)),
                formue = innvilgetFormueVilkår(
                    periode = maiDes,
                    bosituasjon = fullstendigUtenEPS(maiDes),
                ).grunnlag,
            ).resultat shouldBe setOf(Konsistensproblem.BosituasjonOgFormue.IngenFormueForBosituasjonsperiode).left()
        }

        @Test
        fun `bosituasjon er ugyldig`() {
            val maiDes = Periode.create(1.mai(2021), 31.desember(2021))

            SjekkOmGrunnlagErKonsistent.BosituasjonOgFormue(
                bosituasjon = listOf(),
                formue = innvilgetFormueVilkår(
                    periode = maiDes,
                    bosituasjon = fullstendigUtenEPS(maiDes),
                ).grunnlag,
            ).resultat shouldBe setOf(
                Konsistensproblem.BosituasjonOgFormue.IngenFormueForBosituasjonsperiode,
                Konsistensproblem.BosituasjonOgFormue.UgyldigBosituasjon(
                    setOf(Konsistensproblem.Bosituasjon.Mangler),
                ),
            ).left()
        }

        @Test
        fun `formue er ugyldig`() {
            val maiDes = Periode.create(1.mai(2021), 31.desember(2021))

            SjekkOmGrunnlagErKonsistent.BosituasjonOgFormue(
                bosituasjon = listOf(fullstendigUtenEPS(maiDes)),
                formue = innvilgetFormueVilkår(
                    periode = maiDes,
                    bosituasjon = fullstendigUtenEPS(maiDes),
                ).grunnlag + innvilgetFormueVilkår(
                    periode = maiDes,
                    bosituasjon = fullstendigUtenEPS(maiDes),
                ).grunnlag,
            ).resultat shouldBe setOf(
                Konsistensproblem.BosituasjonOgFormue.UgyldigFormue(
                    setOf(Konsistensproblem.Formue.Overlapp),
                ),
            ).left()
        }
    }
}
