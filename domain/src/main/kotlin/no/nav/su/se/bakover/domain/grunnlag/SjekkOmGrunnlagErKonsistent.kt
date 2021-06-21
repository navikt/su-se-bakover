package no.nav.su.se.bakover.domain.grunnlag

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import arrow.core.separateEither

data class SjekkOmGrunnlagErKonsistent(
    private val formuegrunnlag: List<Formuegrunnlag>,
    private val uføregrunnlag: List<Grunnlag.Uføregrunnlag>,
    private val bosituasjongrunnlag: List<Grunnlag.Bosituasjon>,
    private val fradragsgrunnlag: List<Grunnlag.Fradragsgrunnlag>,
) {
    val resultat: Either<Set<Konsistensproblem>, Unit> = setOf(
        Uføre(uføregrunnlag).resultat,
        Bosituasjon(bosituasjongrunnlag).resultat,
        BosituasjonOgFradrag(bosituasjongrunnlag, fradragsgrunnlag).resultat,
        BosituasjonOgFormue(bosituasjongrunnlag, formuegrunnlag).resultat,
    ).let {
        val problemer = it.separateEither().first.flatten().toSet()
        if (problemer.isEmpty()) Unit.right() else problemer.left()
    }

    data class Uføre(
        val uføregrunnlag: List<Grunnlag.Uføregrunnlag>,
    ) {
        val resultat: Either<Set<Konsistensproblem.Uføre>, Unit> = uføregrunnlag(uføregrunnlag)

        private fun uføregrunnlag(uføregrunnlag: List<Grunnlag.Uføregrunnlag>): Either<Set<Konsistensproblem.Uføre>, Unit> {
            mutableSetOf<Konsistensproblem.Uføre>().apply {
                when {
                    uføregrunnlag.isEmpty() -> {
                        add(Konsistensproblem.Uføre.Mangler)
                    }
                }
                return if (this.isEmpty()) Unit.right() else this.left()
            }
        }
    }

    data class Bosituasjon(
        val bosituasjon: List<Grunnlag.Bosituasjon>,
    ) {
        val resultat: Either<Set<Konsistensproblem.Bosituasjon>, Unit> = bosituasjon(bosituasjon)

        private fun bosituasjon(bosituasjon: List<Grunnlag.Bosituasjon>): Either<Set<Konsistensproblem.Bosituasjon>, Unit> {
            mutableSetOf<Konsistensproblem.Bosituasjon>().apply {
                when {
                    bosituasjon.isEmpty() -> {
                        add(Konsistensproblem.Bosituasjon.Mangler)
                    }
                    bosituasjon.any { it is Grunnlag.Bosituasjon.Ufullstendig } -> {
                        add(Konsistensproblem.Bosituasjon.Ufullstendig)
                    }
                    bosituasjon.harFlerEnnEnBosituasjonsperiode() -> {
                        add(Konsistensproblem.Bosituasjon.Flere)
                    }
                }
                return if (this.isEmpty()) Unit.right() else this.left()
            }
        }
    }

    data class BosituasjonOgFradrag(
        val bosituasjon: List<Grunnlag.Bosituasjon>,
        val fradrag: List<Grunnlag.Fradragsgrunnlag>,
    ) {
        val resultat: Either<Set<Konsistensproblem.BosituasjonOgFradrag>, Unit> = bosituasjonOgFradrag(bosituasjon, fradrag)

        private fun bosituasjonOgFradrag(bosituasjon: List<Grunnlag.Bosituasjon>, fradrag: List<Grunnlag.Fradragsgrunnlag>): Either<Set<Konsistensproblem.BosituasjonOgFradrag>, Unit> {
            mutableSetOf<Konsistensproblem.BosituasjonOgFradrag>().apply {
                when {
                    bosituasjon.harFlerEnnEnBosituasjonsperiode() && fradrag.harEpsInntekt() -> {
                        add(Konsistensproblem.BosituasjonOgFradrag.FlereBosituasjonerOgFradragForEPS)
                    }
                    !bosituasjon.harFlerEnnEnBosituasjonsperiode() && !bosituasjon.harEktefelle() && fradrag.harEpsInntekt() -> {
                        add(Konsistensproblem.BosituasjonOgFradrag.IngenEPSMenFradragForEPS)
                    }
                }
                return if (this.isEmpty()) Unit.right() else this.left()
            }
        }
    }

    data class BosituasjonOgFormue(
        val bosituasjon: List<Grunnlag.Bosituasjon>,
        val formue: List<Formuegrunnlag>,
    ) {
        val resultat: Either<Set<Konsistensproblem.BosituasjonOgFormue>, Unit> =
            bosituasjonOgFormue(bosituasjon, formue)

        private fun bosituasjonOgFormue(
            bosituasjon: List<Grunnlag.Bosituasjon>,
            formue: List<Formuegrunnlag>,
        ): Either<Set<Konsistensproblem.BosituasjonOgFormue>, Unit> {
            mutableSetOf<Konsistensproblem.BosituasjonOgFormue>().apply {
                when {
                    bosituasjon.harFlerEnnEnBosituasjonsperiode() && formue.harEpsFormue() -> {
                        add(Konsistensproblem.BosituasjonOgFormue.FlereBosituasjonerOgFormueForEPS)
                    }
                    !bosituasjon.harFlerEnnEnBosituasjonsperiode() && !bosituasjon.harEktefelle() && formue.harEpsFormue() -> {
                        add(Konsistensproblem.BosituasjonOgFormue.IngenEPSMenFormueForEPS)
                    }
                }
                return if (this.isEmpty()) Unit.right() else this.left()
            }
        }
    }
}

sealed class Konsistensproblem {

    sealed class Uføre : Konsistensproblem() {
        object Mangler : Uføre()
    }

    sealed class Bosituasjon : Konsistensproblem() {
        object Flere : Bosituasjon()
        object Ufullstendig : Bosituasjon()
        object Mangler : Bosituasjon()
    }

    sealed class BosituasjonOgFradrag : Konsistensproblem() {
        object FlereBosituasjonerOgFradragForEPS : BosituasjonOgFradrag()
        object IngenEPSMenFradragForEPS : BosituasjonOgFradrag()
    }

    sealed class BosituasjonOgFormue : Konsistensproblem() {
        object FlereBosituasjonerOgFormueForEPS : BosituasjonOgFormue()
        object IngenEPSMenFormueForEPS : BosituasjonOgFormue()
    }
}
