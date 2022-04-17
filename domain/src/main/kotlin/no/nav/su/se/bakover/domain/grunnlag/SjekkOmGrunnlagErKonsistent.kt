package no.nav.su.se.bakover.domain.grunnlag

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import arrow.core.separateEither
import no.nav.su.se.bakover.common.periode.inneholderAlle
import no.nav.su.se.bakover.domain.grunnlag.Formuegrunnlag.Verdier.Companion.perioder
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag.Bosituasjon.Companion.harOverlappende
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag.Bosituasjon.Companion.perioder
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag.Bosituasjon.Companion.perioderMedEPS
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag.Fradragsgrunnlag.Companion.allePerioderMedEPS
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag.Fradragsgrunnlag.Companion.perioder
import no.nav.su.se.bakover.domain.vedtak.GjeldendeVedtaksdata

data class SjekkOmGrunnlagErKonsistent(
    private val formuegrunnlag: List<Formuegrunnlag>,
    private val uføregrunnlag: List<Grunnlag.Uføregrunnlag>,
    private val bosituasjongrunnlag: List<Grunnlag.Bosituasjon>,
    private val fradragsgrunnlag: List<Grunnlag.Fradragsgrunnlag>,
) {
    constructor(gjeldendeVedtaksdata: GjeldendeVedtaksdata) : this(
        formuegrunnlag = gjeldendeVedtaksdata.vilkårsvurderinger.formue.grunnlag,
        uføregrunnlag = gjeldendeVedtaksdata.vilkårsvurderinger.uføre.grunnlag,
        bosituasjongrunnlag = gjeldendeVedtaksdata.grunnlagsdata.bosituasjon,
        fradragsgrunnlag = gjeldendeVedtaksdata.grunnlagsdata.fradragsgrunnlag,
    )

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
                if (bosituasjon.isEmpty()) {
                    add(Konsistensproblem.Bosituasjon.Mangler)
                }
                if (bosituasjon.any { it is Grunnlag.Bosituasjon.Ufullstendig }) {
                    add(Konsistensproblem.Bosituasjon.Ufullstendig)
                }
                if (bosituasjon.harOverlappende()) {
                    add(Konsistensproblem.Bosituasjon.Overlapp)
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
        val resultat: Either<Set<Konsistensproblem.BosituasjonOgFradrag>, Unit> = bosituasjonOgFradrag()

        private fun bosituasjonOgFradrag(): Either<Set<Konsistensproblem.BosituasjonOgFradrag>, Unit> {
            if (fradrag.isEmpty()) return Unit.right()

            mutableSetOf<Konsistensproblem.BosituasjonOgFradrag>().apply {
                Bosituasjon(bosituasjon).resultat.tapLeft {
                    add(Konsistensproblem.BosituasjonOgFradrag.UgyldigBosituasjon(it))
                }
                if (!perioderForBosituasonEPSOgFradragEPSSamsvarer()) {
                    add(Konsistensproblem.BosituasjonOgFradrag.PerioderForBosituasjonEPSOgFradragEPSSamsvarerIkke)
                }
                if (!bosituasjonsperiodeInneholderAlleFradragsperioder()) {
                    add(Konsistensproblem.BosituasjonOgFradrag.PerioderMedFradragUtenforPerioderMedBosituasjon)
                }
                return if (this.isEmpty()) Unit.right() else this.left()
            }
        }

        private fun perioderForBosituasonEPSOgFradragEPSSamsvarer(): Boolean {
            return bosituasjon.perioderMedEPS().inneholderAlle(fradrag.allePerioderMedEPS())
        }

        private fun bosituasjonsperiodeInneholderAlleFradragsperioder(): Boolean {
            return bosituasjon.perioder().inneholderAlle(fradrag.perioder())
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
        val resultat: Either<Set<Konsistensproblem.BosituasjonOgFormue>, Unit> = bosituasjonOgFormue()

        private fun bosituasjonOgFormue(): Either<Set<Konsistensproblem.BosituasjonOgFormue>, Unit> {
            if (formue.isEmpty()) return Unit.right()

            mutableSetOf<Konsistensproblem.BosituasjonOgFormue>().apply {
                Bosituasjon(bosituasjon).resultat.tapLeft {
                    add(Konsistensproblem.BosituasjonOgFormue.UgyldigBosituasjon(it))
                }
                if (!perioderForBosituasonEPSOgFormueEPSSamsvarer()) {
                    add(Konsistensproblem.BosituasjonOgFormue.PerioderForBosituasjonEPSOgFormueEPSSamsvarerIkke)
                }
                if (!bosituasjonsperiodeInneholderAlleFormueperioder()) {
                    add(Konsistensproblem.BosituasjonOgFormue.PerioderForFormueErUtenforPerioderMedBostiuasjon)
                }
                return if (this.isEmpty()) Unit.right() else this.left()
            }
        }

        private fun perioderForBosituasonEPSOgFormueEPSSamsvarer(): Boolean {
            return bosituasjon.perioderMedEPS().inneholderAlle(formue.allePerioderMedEPS())
        }

        private fun bosituasjonsperiodeInneholderAlleFormueperioder(): Boolean {
            return bosituasjon.perioder().inneholderAlle(formue.perioder())
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
        /** Du har f.eks. valgt EPS, men ikke tatt stilling til om hen bor med voksne/alene etc.  */
        object Ufullstendig : Bosituasjon() {
            override fun erGyldigTilstand() = false
        }

        /** Vi må alltid ha en utfylt bosituasjon når vi vedtar en stønadsbehandling (revurdering,søknad,regulering etc.)*/
        object Mangler : Bosituasjon() {
            override fun erGyldigTilstand() = false
        }

        /** Periodene med bosituasjon overlapper hverandre */
        object Overlapp : Bosituasjon() {
            override fun erGyldigTilstand(): Boolean = false
        }
    }

    sealed class BosituasjonOgFradrag : Konsistensproblem() {
        /** Ugyldig case. Vi har fradragsperioder for EPS som vi mangler bosituasjonsperiode for. Disse bør være 1-1. */
        object PerioderMedFradragUtenforPerioderMedBosituasjon : BosituasjonOgFradrag() {
            override fun erGyldigTilstand() = false
        }

        /** Ugyldig case. Bosituasjon er ugyldig på egenhånd */
        data class UgyldigBosituasjon(val feil: Set<Bosituasjon>) : BosituasjonOgFradrag() {
            override fun erGyldigTilstand(): Boolean = false
        }

        /** Ugyldig case. Der er ikke samsvar mellom bosituasjon og formue for eps for alle periodene */
        object PerioderForBosituasjonEPSOgFradragEPSSamsvarerIkke : BosituasjonOgFradrag() {
            override fun erGyldigTilstand() = false
        }
    }

    sealed class BosituasjonOgFormue : Konsistensproblem() {
        /** Ugyldig case. Periode med formue er ikke innenfor periode med bosituasjon */
        object PerioderForFormueErUtenforPerioderMedBostiuasjon : BosituasjonOgFormue() {
            override fun erGyldigTilstand(): Boolean = false
        }

        /** Ugyldig case. Bosituasjon er ugyldig på egenhånd */
        data class UgyldigBosituasjon(val feil: Set<Bosituasjon>) : BosituasjonOgFormue() {
            override fun erGyldigTilstand(): Boolean = false
        }

        /** Ugyldig case. Der er ikke samsvar mellom bosituasjon og formue for eps for alle periodene */
        object PerioderForBosituasjonEPSOgFormueEPSSamsvarerIkke : BosituasjonOgFormue() {
            override fun erGyldigTilstand() = false
        }
    }
}

fun Set<Konsistensproblem>.erGyldigTilstand(): Boolean = this.all { it.erGyldigTilstand() }
