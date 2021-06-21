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

    /**
     * Dersom man har epsFradrag, sjekker at det ikke finnes fler enn 1 bosituasjon og at man har en EPS
     *
     * Bruk Bosituasjon og Fradrag direkte dersom du trenger å vite om de hver for seg er i henhold (typisk om de er utfylt).
     * @return Either.Right(Unit) dersom søker ikke har EPS eller ikke har fradrag.
     */
    data class BosituasjonOgFradrag(
        val bosituasjon: List<Grunnlag.Bosituasjon>,
        val fradrag: List<Grunnlag.Fradragsgrunnlag>,
    ) {
        val resultat: Either<Set<Konsistensproblem.BosituasjonOgFradrag>, Unit> =
            bosituasjonOgFradrag(bosituasjon, fradrag)

        private fun bosituasjonOgFradrag(
            bosituasjon: List<Grunnlag.Bosituasjon>,
            fradrag: List<Grunnlag.Fradragsgrunnlag>,
        ): Either<Set<Konsistensproblem.BosituasjonOgFradrag>, Unit> {
            if (fradrag.isEmpty()) return Unit.right()
            if (!fradrag.harEpsInntekt()) return Unit.right()
            mutableSetOf<Konsistensproblem.BosituasjonOgFradrag>().apply {
                when {
                    bosituasjon.harFlerEnnEnBosituasjonsperiode() -> {
                        // Vi støtter ikke fler bosituasjoner ved innsending eller etter vilkårsvurdering.
                        // Det kan oppstår dersom man revurderer på tvers av vedtak.
                        add(Konsistensproblem.BosituasjonOgFradrag.FlereBosituasjonerOgFradragForEPS)
                    }
                    bosituasjon.any { !it.harEktefelle() } -> {
                        add(Konsistensproblem.BosituasjonOgFradrag.IngenEPSMenFradragForEPS)
                    }
                    // TODO jah: Vi sjekker ikke på om epsFormue/epsInntekt er innenfor sin respektive bosituasjonsperiode
                }
                return if (this.isEmpty()) Unit.right() else this.left()
            }
        }
    }

    /**
     * Dersom man har epsFormue, sjekker at det ikke finnes fler enn 1 bosituasjon og at man har en EPS
     *
     * Bruk Bosituasjon og Formue direkte dersom du trenger å vite om de hver for seg er i henhold (typisk om de er utfylt).
     * @return Either.Right(Unit) dersom søker ikke har EPS eller ikke har formue.
     */
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
            if (formue.isEmpty()) return Unit.right()
            if (!formue.harEpsFormue()) return Unit.right()
            mutableSetOf<Konsistensproblem.BosituasjonOgFormue>().apply {
                when {
                    bosituasjon.harFlerEnnEnBosituasjonsperiode() -> {
                        // Vi støtter ikke fler bosituasjoner ved innsending eller etter vilkårsvurdering.
                        // Det kan oppstår dersom man revurderer på tvers av vedtak.
                        add(Konsistensproblem.BosituasjonOgFormue.FlereBosituasjonerOgFormueForEPS)
                    }
                    bosituasjon.any { !it.harEktefelle() } -> {
                        add(Konsistensproblem.BosituasjonOgFormue.IngenEPSMenFormueForEPS)
                    }
                    // TODO jah: Vi sjekker ikke på om epsFormue/epsInntekt er innenfor sin respektive bosituasjonsperiode
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
