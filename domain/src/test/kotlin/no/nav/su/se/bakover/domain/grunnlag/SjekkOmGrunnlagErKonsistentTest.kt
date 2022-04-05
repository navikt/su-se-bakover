package no.nav.su.se.bakover.domain.grunnlag

import arrow.core.left
import arrow.core.right
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.april
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.juni
import no.nav.su.se.bakover.common.mai
import no.nav.su.se.bakover.common.november
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.generer
import no.nav.su.se.bakover.test.innvilgetFormueVilkår
import no.nav.su.se.bakover.test.lagFradragsgrunnlag
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.UUID

internal class SjekkOmGrunnlagErKonsistentTest {

    private val periode = Periode.create(1.januar(2021), 31.desember(2021))

    private fun ufullstendig(periode: Periode): Grunnlag.Bosituasjon.Ufullstendig.HarIkkeEps {
        return Grunnlag.Bosituasjon.Ufullstendig.HarIkkeEps(
            id = UUID.randomUUID(),
            opprettet = fixedTidspunkt,
            periode = periode,
        )
    }

    private fun utenEps(periode: Periode): Grunnlag.Bosituasjon.Fullstendig {
        return Grunnlag.Bosituasjon.Fullstendig.Enslig(
            id = UUID.randomUUID(),
            opprettet = fixedTidspunkt,
            periode = periode,
            begrunnelse = null,
        )
    }

    private fun medEps(periode: Periode): Grunnlag.Bosituasjon.Fullstendig.EktefellePartnerSamboer {
        return Grunnlag.Bosituasjon.Fullstendig.EktefellePartnerSamboer.Under67.UførFlyktning(
            id = UUID.randomUUID(),
            opprettet = fixedTidspunkt,
            periode = periode,
            fnr = Fnr.generer(),
            begrunnelse = null,
        )
    }

    private fun arbeidsinntekt(periode: Periode, tilhører: FradragTilhører): Grunnlag.Fradragsgrunnlag {
        return lagFradragsgrunnlag(
            type = Fradragstype.Arbeidsinntekt,
            månedsbeløp = 5000.0,
            periode = periode,
            utenlandskInntekt = null,
            tilhører = tilhører,
        )
    }

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
                    ufullstendig(periode),
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
                    utenEps(Periode.create(1.januar(2021), 31.desember(2021))),
                    utenEps(Periode.create(1.mai(2021), 31.desember(2021))),
                ),
            ).resultat shouldBe setOf(Konsistensproblem.Bosituasjon.Overlapp).left()
        }

        @Test
        fun `happy path`() {
            SjekkOmGrunnlagErKonsistent.Bosituasjon(
                listOf(
                    utenEps(Periode.create(1.januar(2021), 30.april(2021))),
                    medEps(Periode.create(1.mai(2021), 31.desember(2021))),
                ),
            ).resultat shouldBe Unit.right()
        }
    }

    @Nested
    inner class BosituasjonOgFradrag {

        @Test
        fun `ugyldig bosituasjon`() {
            SjekkOmGrunnlagErKonsistent.BosituasjonOgFradrag(
                listOf(ufullstendig(periode)),
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
                listOf(medEps(periode)),
                listOf(arbeidsinntekt(Periode.create(1.januar(2021), 31.mai(2023)), FradragTilhører.BRUKER)),
            ).resultat shouldBe setOf(
                Konsistensproblem.BosituasjonOgFradrag.PerioderMedFradragUtenforPerioderMedBosituasjon,
            ).left()
        }

        @Test
        fun `ikke samsvar mellom bosituasjon og formue for eps`() {
            SjekkOmGrunnlagErKonsistent.BosituasjonOgFradrag(
                listOf(utenEps(periode)),
                listOf(arbeidsinntekt(periode, FradragTilhører.EPS)),
            ).resultat shouldBe setOf(
                Konsistensproblem.BosituasjonOgFradrag.PerioderForBosituasjonEPSOgFradragEPSSamsvarerIkke,
            ).left()
        }

        @Test
        fun `ikke samsvar mellom bosituasjon og formue for eps variant`() {
            SjekkOmGrunnlagErKonsistent.BosituasjonOgFradrag(
                listOf(
                    utenEps(Periode.create(1.januar(2021), 31.mai(2021))),
                    medEps(Periode.create(1.juni(2021), 31.desember(2021))),
                ),
                listOf(arbeidsinntekt(periode, FradragTilhører.EPS)),
            ).resultat shouldBe setOf(
                Konsistensproblem.BosituasjonOgFradrag.PerioderForBosituasjonEPSOgFradragEPSSamsvarerIkke,
            ).left()
        }

        @Test
        fun `happy path`() {
            SjekkOmGrunnlagErKonsistent.BosituasjonOgFradrag(
                listOf(
                    utenEps(Periode.create(1.januar(2021), 31.mai(2021))),
                    medEps(Periode.create(1.juni(2021), 31.desember(2021))),
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
                    utenEps(periode),
                ),
                listOf(),
            ).resultat shouldBe Unit.right()
        }

        @Test
        fun `fravær av fradrag er med eps er ok`() {
            SjekkOmGrunnlagErKonsistent.BosituasjonOgFradrag(
                listOf(
                    medEps(periode),
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
                bosituasjongrunnlag = listOf(medEps(periode), utenEps(periode)),
                fradragsgrunnlag = listOf(arbeidsinntekt(periode, FradragTilhører.EPS)),
            ).resultat shouldBe setOf(
                Konsistensproblem.Uføre.Mangler,
                Konsistensproblem.Bosituasjon.Overlapp,
                Konsistensproblem.BosituasjonOgFradrag.UgyldigBosituasjon(setOf(Konsistensproblem.Bosituasjon.Overlapp)),
            ).left()
        }

        @Test
        fun `fullstendig sjekk variant`() {
            SjekkOmGrunnlagErKonsistent(
                formuegrunnlag = innvilgetFormueVilkår(periode = periode, bosituasjon = medEps(periode)).grunnlag,
                uføregrunnlag = emptyList(),
                bosituasjongrunnlag = listOf(utenEps(periode)),
                fradragsgrunnlag = listOf(
                    arbeidsinntekt(
                        periode = Periode.create(1.januar(2021), 31.mai(2022)),
                        FradragTilhører.EPS,
                    ),
                ),
            ).resultat shouldBe setOf(
                Konsistensproblem.Uføre.Mangler,
                Konsistensproblem.BosituasjonOgFradrag.PerioderForBosituasjonEPSOgFradragEPSSamsvarerIkke,
                Konsistensproblem.BosituasjonOgFradrag.PerioderMedFradragUtenforPerioderMedBosituasjon,
                Konsistensproblem.BosituasjonOgFormue.PerioderForBosituasjonEPSOgFormueEPSSamsvarerIkke,
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
            SjekkOmGrunnlagErKonsistent(
                formuegrunnlag = innvilgetFormueVilkår(periode = periode).grunnlag,
                uføregrunnlag = listOf(uføregrunnlag),
                bosituasjongrunnlag = listOf(medEps(periode)),
                fradragsgrunnlag = listOf(arbeidsinntekt(periode, FradragTilhører.EPS)),
            ).resultat shouldBe Unit.right()
        }
    }
}
