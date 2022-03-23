package no.nav.su.se.bakover.domain.grunnlag

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import arrow.core.separateEither
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag.Fradragsgrunnlag.Companion.harEpsInntekt

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
                    bosituasjon.any { !it.harEPS() } -> {
                        add(Konsistensproblem.BosituasjonOgFradrag.IngenEPSMenFradragForEPS)
                    }
                    bosituasjon.any { bosituasjon ->
                        fradrag.any { fradrag ->
                            !bosituasjon.periode.inneholder(fradrag.periode)
                        }
                    } -> add(Konsistensproblem.BosituasjonOgFradrag.EPSFradragsperiodeErUtenforBosituasjonPeriode)
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
            if (!formue.harEPSFormue()) return Unit.right()
            mutableSetOf<Konsistensproblem.BosituasjonOgFormue>().apply {
                when {
                    bosituasjon.harFlerEnnEnBosituasjonsperiode() -> {
                        // Vi støtter ikke fler bosituasjoner ved innsending eller etter vilkårsvurdering.
                        // Det kan oppstår dersom man revurderer på tvers av vedtak.
                        add(Konsistensproblem.BosituasjonOgFormue.FlereBosituasjonerOgFormueForEPS)
                    }
                    bosituasjon.any { !it.harEPS() } -> {
                        add(Konsistensproblem.BosituasjonOgFormue.IngenEPSMenFormueForEPS)
                    }
                    bosituasjon.any { bosituasjon ->
                        formue.any { formue ->
                            !bosituasjon.periode.inneholder(formue.periode)
                        }
                    } -> add(Konsistensproblem.BosituasjonOgFormue.EPSFormueperiodeErUtenforBosituasjonPeriode)
                }
                return if (this.isEmpty()) Unit.right() else this.left()
            }
        }
    }
}

sealed class Konsistensproblem {

    /**
     * Konsistensproblemene er delt opp i 2:
     * 1. Gyldige tilstander som vi ikke støtter å revurdere enda.
     * 1. Ugyldige tilstander som kan oppstå på grunn av svak typing/domenemodell/validering
     */
    abstract fun erGyldigTilstand(): Boolean

    sealed class Uføre : Konsistensproblem() {
        /** Da er ikke vilkåret for Uføre innfridd. Dette vil føre til avslag eller opphør. */
        object Mangler : Uføre() {
            override fun erGyldigTilstand() = true
        }
    }

    sealed class Bosituasjon : Konsistensproblem() {
        /** Det er generelt gyldig at bosituasjon kan variere fra måned til måned, men vi støtter det ikke enda. */
        object Flere : Bosituasjon() {
            override fun erGyldigTilstand() = true
        }

        /** Du har f.eks. valgt EPS, men ikke tatt stilling til om hen bor med voksne/alene etc.  */
        object Ufullstendig : Bosituasjon() {
            override fun erGyldigTilstand() = false
        }

        /** Vi må alltid ha en utfylt bosituasjon når vi vedtar en stønadsbehandling (revurdering,søknad,regulering etc.)*/
        object Mangler : Bosituasjon() {
            override fun erGyldigTilstand() = false
        }
    }

    sealed class BosituasjonOgFradrag : Konsistensproblem() {
        /** Dette er en gyldig case som vi ikke støtter enda. Her har vi EPS-fradrag i tillegg til flere bosituasjoner */
        object FlereBosituasjonerOgFradragForEPS : BosituasjonOgFradrag() {
            override fun erGyldigTilstand() = true
        }

        /** Ugyldig case. Vi må ha EPS for å kunne ha fradrag tilhørende EPS. */
        object IngenEPSMenFradragForEPS : BosituasjonOgFradrag() {
            override fun erGyldigTilstand() = false
        }

        /** Ugyldig case. Vi har fradragsperioder for EPS som vi mangler bosituasjonsperiode for. Disse bør være 1-1. */
        object EPSFradragsperiodeErUtenforBosituasjonPeriode : BosituasjonOgFradrag() {
            override fun erGyldigTilstand() = false
        }
    }

    sealed class BosituasjonOgFormue : Konsistensproblem() {
        /** Dette er en gyldig case som vi ikke støtter enda. Her har vi EPS-formue i tillegg til flere bosituasjoner */
        object FlereBosituasjonerOgFormueForEPS : BosituasjonOgFormue() {
            override fun erGyldigTilstand() = true
        }

        /** Ugyldig case. Vi må ha EPS for å kunne ha formue tilhørende EPS. */
        object IngenEPSMenFormueForEPS : BosituasjonOgFormue() {
            override fun erGyldigTilstand() = false
        }

        /** Ugyldig case. Vi har formueperioder for EPS som vi mangler bosituasjonsperiode for. Disse bør være 1-1. */
        object EPSFormueperiodeErUtenforBosituasjonPeriode : BosituasjonOgFormue() {
            override fun erGyldigTilstand() = false
        }
    }
}

fun Set<Konsistensproblem>.erGyldigTilstand(): Boolean = this.all { it.erGyldigTilstand() }
