package no.nav.su.se.bakover.domain.grunnlag

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import arrow.core.separateEither
import no.nav.su.se.bakover.common.tid.periode.inneholder
import no.nav.su.se.bakover.domain.grunnlag.Formuegrunnlag.Verdier.Companion.minsteAntallSammenhengendePerioder
import no.nav.su.se.bakover.domain.grunnlag.Fradragsgrunnlag.Companion.allePerioderMedEPS
import no.nav.su.se.bakover.domain.grunnlag.Fradragsgrunnlag.Companion.perioder
import no.nav.su.se.bakover.domain.vedtak.GjeldendeVedtaksdata
import vilkår.common.domain.grunnlag.Bosituasjon
import vilkår.common.domain.grunnlag.Bosituasjon.Companion.harOverlappende
import vilkår.common.domain.grunnlag.Bosituasjon.Companion.minsteAntallSammenhengendePerioder
import vilkår.common.domain.grunnlag.Bosituasjon.Companion.perioderMedEPS
import vilkår.uføre.domain.Uføregrunnlag

data class SjekkOmGrunnlagErKonsistent(
    private val formuegrunnlag: List<Formuegrunnlag>,
    private val uføregrunnlag: List<Uføregrunnlag>,
    private val bosituasjongrunnlag: List<vilkår.common.domain.grunnlag.Bosituasjon.Fullstendig>,
    private val fradragsgrunnlag: List<Fradragsgrunnlag>,
) {
    constructor(gjeldendeVedtaksdata: GjeldendeVedtaksdata) : this(
        formuegrunnlag = gjeldendeVedtaksdata.vilkårsvurderinger.formue.grunnlag,
        uføregrunnlag = gjeldendeVedtaksdata.vilkårsvurderinger.uføreVilkår().fold(
            {
                TODO("vilkårsvurdering_alder konsistenssjekk for alder")
            },
            {
                it.grunnlag
            },
        ),
        bosituasjongrunnlag = gjeldendeVedtaksdata.grunnlagsdata.bosituasjonSomFullstendig(),
        fradragsgrunnlag = gjeldendeVedtaksdata.grunnlagsdata.fradragsgrunnlag,
    )

    val resultat: Either<Set<Konsistensproblem>, Unit> = setOf(
        Uføre(uføregrunnlag).resultat,
        Bosituasjon(bosituasjongrunnlag).resultat,
        Formue(formuegrunnlag).resultat,
        BosituasjonOgFradrag(bosituasjongrunnlag, fradragsgrunnlag).resultat,
        BosituasjonOgFormue(bosituasjongrunnlag, formuegrunnlag).resultat,
    ).let {
        val problemer = it.separateEither().first.flatten().toSet()
        if (problemer.isEmpty()) Unit.right() else problemer.left()
    }

    data class Uføre(
        val uføregrunnlag: List<Uføregrunnlag>,
    ) {
        val resultat: Either<Set<Konsistensproblem.Uføre>, Unit> = uføregrunnlag(uføregrunnlag)

        private fun uføregrunnlag(uføregrunnlag: List<Uføregrunnlag>): Either<Set<Konsistensproblem.Uføre>, Unit> {
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
        val bosituasjon: List<vilkår.common.domain.grunnlag.Bosituasjon>,
    ) {
        val resultat: Either<Set<Konsistensproblem.Bosituasjon>, Unit> = bosituasjon(bosituasjon)

        private fun bosituasjon(bosituasjon: List<vilkår.common.domain.grunnlag.Bosituasjon>): Either<Set<Konsistensproblem.Bosituasjon>, Unit> {
            mutableSetOf<Konsistensproblem.Bosituasjon>().apply {
                if (bosituasjon.isEmpty()) {
                    add(Konsistensproblem.Bosituasjon.Mangler)
                }
                if (bosituasjon.any { it is vilkår.common.domain.grunnlag.Bosituasjon.Ufullstendig }) {
                    add(Konsistensproblem.Bosituasjon.Ufullstendig)
                }
                if (bosituasjon.harOverlappende()) {
                    add(Konsistensproblem.Bosituasjon.Overlapp)
                }
                return if (this.isEmpty()) Unit.right() else this.left()
            }
        }
    }

    data class Formue(
        val formue: List<Formuegrunnlag>,
    ) {
        val resultat: Either<Set<Konsistensproblem.Formue>, Unit> = formue(formue)

        private fun formue(formue: List<Formuegrunnlag>): Either<Set<Konsistensproblem.Formue>, Unit> {
            mutableSetOf<Konsistensproblem.Formue>().apply {
                if (formue.isEmpty()) {
                    add(Konsistensproblem.Formue.Mangler)
                }
                if (formue.harOverlappende()) {
                    add(Konsistensproblem.Formue.Overlapp)
                }
                return if (this.isEmpty()) Unit.right() else this.left()
            }
        }
    }

    /**
     * Bruk Bosituasjon og Fradrag direkte dersom du trenger å vite om de hver for seg er i henhold (typisk om de er utfylt).
     * @return Either.Right(Unit) dersom søker ikke har EPS eller ikke har fradrag.
     */
    data class BosituasjonOgFradrag(
        val bosituasjon: List<vilkår.common.domain.grunnlag.Bosituasjon>,
        val fradrag: List<Fradragsgrunnlag>,
    ) {
        val resultat: Either<Set<Konsistensproblem.BosituasjonOgFradrag>, Unit> = bosituasjonOgFradrag()

        private fun bosituasjonOgFradrag(): Either<Set<Konsistensproblem.BosituasjonOgFradrag>, Unit> {
            if (fradrag.isEmpty()) return Unit.right()

            mutableSetOf<Konsistensproblem.BosituasjonOgFradrag>().apply {
                Bosituasjon(bosituasjon).resultat.onLeft {
                    add(Konsistensproblem.BosituasjonOgFradrag.UgyldigBosituasjon(it))
                }
                if (!gyldigKombinasjonAvBosituasjonOgFradrag()) {
                    add(Konsistensproblem.BosituasjonOgFradrag.KombinasjonAvBosituasjonOgFradragErUgyldig)
                }
                if (!harBosituasjonForAllePerioder()) {
                    add(Konsistensproblem.BosituasjonOgFradrag.IngenBosituasjonForFradragsperiode)
                }
                return if (this.isEmpty()) Unit.right() else this.left()
            }
        }

        private fun gyldigKombinasjonAvBosituasjonOgFradrag(): Boolean {
            return bosituasjon.perioderMedEPS().inneholder(fradrag.allePerioderMedEPS())
        }

        private fun harBosituasjonForAllePerioder(): Boolean {
            return bosituasjon.minsteAntallSammenhengendePerioder().inneholder(fradrag.perioder())
        }
    }

    /**
     * Bruk Bosituasjon og Formue direkte dersom du trenger å vite om de hver for seg er i henhold (typisk om de er utfylt).
     * @return Either.Right(Unit) dersom søker ikke har EPS eller ikke har formue.
     */
    data class BosituasjonOgFormue(
        val bosituasjon: List<vilkår.common.domain.grunnlag.Bosituasjon>,
        val formue: List<Formuegrunnlag>,
    ) {
        val resultat: Either<Set<Konsistensproblem.BosituasjonOgFormue>, Unit> = bosituasjonOgFormue()

        private fun bosituasjonOgFormue(): Either<Set<Konsistensproblem.BosituasjonOgFormue>, Unit> {
            if (formue.isEmpty()) return Unit.right()

            mutableSetOf<Konsistensproblem.BosituasjonOgFormue>().apply {
                Bosituasjon(bosituasjon).resultat.onLeft {
                    add(Konsistensproblem.BosituasjonOgFormue.UgyldigBosituasjon(it))
                }
                Formue(formue).resultat.onLeft {
                    add(Konsistensproblem.BosituasjonOgFormue.UgyldigFormue(it))
                }
                if (!gyldigKombinasjonAvBosituasjonOgFormue()) {
                    add(Konsistensproblem.BosituasjonOgFormue.KombinasjonAvBosituasjonOgFormueErUyldig)
                }
                if (!harFormueForAllePerioder()) {
                    add(Konsistensproblem.BosituasjonOgFormue.IngenFormueForBosituasjonsperiode)
                }
                if (!harFormueEpsForAlleRelevantePerioder()) {
                    add(Konsistensproblem.BosituasjonOgFormue.FormueForEPSManglerForBosituasjonsperiode)
                }
                return if (this.isEmpty()) Unit.right() else this.left()
            }
        }

        private fun gyldigKombinasjonAvBosituasjonOgFormue(): Boolean {
            return bosituasjon.perioderMedEPS().inneholder(formue.perioderMedEPS())
        }

        private fun harFormueForAllePerioder(): Boolean {
            return bosituasjon.minsteAntallSammenhengendePerioder() == formue.minsteAntallSammenhengendePerioder()
        }

        private fun harFormueEpsForAlleRelevantePerioder(): Boolean {
            return bosituasjon.perioderMedEPS() == formue.perioderMedEPS()
        }
    }
}

sealed interface Konsistensproblem {

    /**
     * Konsistensproblemene er delt opp i 2:
     * 1. Gyldige tilstander som vi ikke støtter å revurdere enda.
     * 1. Ugyldige tilstander som kan oppstå på grunn av svak typing/domenemodell/validering
     */
    fun erGyldigTilstand(): Boolean

    sealed interface Uføre : Konsistensproblem {
        /** Da er ikke vilkåret for Uføre innfridd. Dette vil føre til avslag eller opphør. */
        data object Mangler : Uføre {
            override fun erGyldigTilstand() = true
        }
    }

    sealed interface Bosituasjon : Konsistensproblem {
        /** Du har f.eks. valgt EPS, men ikke tatt stilling til om hen bor med voksne/alene etc.  */
        data object Ufullstendig : Bosituasjon {
            override fun erGyldigTilstand() = false
        }

        /** Vi må alltid ha en utfylt bosituasjon når vi vedtar en stønadsbehandling (revurdering,søknad,regulering etc.)*/
        data object Mangler : Bosituasjon {
            override fun erGyldigTilstand() = false
        }

        /** Periodene med bosituasjon overlapper hverandre */
        data object Overlapp : Bosituasjon {
            override fun erGyldigTilstand(): Boolean = false
        }
    }

    sealed interface Formue : Konsistensproblem {
        data object Mangler : Formue {
            override fun erGyldigTilstand(): Boolean = false
        }

        data object Overlapp : Formue {
            override fun erGyldigTilstand(): Boolean = false
        }
    }

    sealed interface BosituasjonOgFradrag : Konsistensproblem {
        data object IngenBosituasjonForFradragsperiode : BosituasjonOgFradrag {
            override fun erGyldigTilstand() = false
        }

        data class UgyldigBosituasjon(val feil: Set<Bosituasjon>) : BosituasjonOgFradrag {
            override fun erGyldigTilstand() = false
        }

        data object KombinasjonAvBosituasjonOgFradragErUgyldig : BosituasjonOgFradrag {
            override fun erGyldigTilstand() = false
        }
    }

    sealed interface BosituasjonOgFormue : Konsistensproblem {
        data object IngenFormueForBosituasjonsperiode : BosituasjonOgFormue {
            override fun erGyldigTilstand() = false
        }

        data class UgyldigBosituasjon(val feil: Set<Bosituasjon>) : BosituasjonOgFormue {
            override fun erGyldigTilstand() = false
        }

        data class UgyldigFormue(val feil: Set<Formue>) : BosituasjonOgFormue {
            override fun erGyldigTilstand(): Boolean = false
        }

        data object KombinasjonAvBosituasjonOgFormueErUyldig : BosituasjonOgFormue {
            override fun erGyldigTilstand() = false
        }

        data object FormueForEPSManglerForBosituasjonsperiode : BosituasjonOgFormue {
            override fun erGyldigTilstand() = false
        }
    }
}

fun Set<Konsistensproblem>.erGyldigTilstand(): Boolean = this.all { it.erGyldigTilstand() }
