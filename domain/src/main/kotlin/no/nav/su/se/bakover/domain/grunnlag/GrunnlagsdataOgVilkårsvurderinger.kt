package no.nav.su.se.bakover.domain.grunnlag

import arrow.core.Either
import arrow.core.getOrHandle
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.periode.erSammenhengendeSortertOgUtenDuplikater
import no.nav.su.se.bakover.domain.vilkår.Vilkår
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger

sealed class GrunnlagsdataOgVilkårsvurderinger {
    abstract val grunnlagsdata: Grunnlagsdata
    abstract val vilkårsvurderinger: Vilkårsvurderinger

    fun periode(): Periode? {
        return grunnlagsdata.periode ?: vilkårsvurderinger.periode
    }

    fun erVurdert(): Boolean = vilkårsvurderinger.erVurdert && grunnlagsdata.erUtfylt

    protected fun kastHvisPerioderIkkeErLike() {
        if (grunnlagsdata.periode != null && vilkårsvurderinger.periode != null) {
            require(grunnlagsdata.periode == vilkårsvurderinger.periode) {
                "Grunnlagsdataperioden (${grunnlagsdata.periode}) må være lik vilkårsvurderingerperioden (${vilkårsvurderinger.periode})"
            }
            // TODO jah: Dersom periodene ikke er sammenhengende bør vi heller sjekke at periodene er like.
        }
        // TODO jah: Grunnlagsdata og Vilkårsvurderinger bør ha tilsvarende sjekk og vurderingsperiodene bør sjekke at de er sortert og uten duplikater
    }

    /**
     * Litt forskjellig valideringskrav avhengig om bosituasjonen er ufullstendig/fullstendig.
     *
     * Fjerner EPS sin formue/fradrag dersom søker ikke har EPS.
     * */
    open fun oppdaterBosituasjon(
        bosituasjon: Grunnlag.Bosituasjon,
    ): GrunnlagsdataOgVilkårsvurderinger {
        return when (bosituasjon) {
            is Grunnlag.Bosituasjon.Fullstendig -> oppdaterBosituasjon(bosituasjon)
            is Grunnlag.Bosituasjon.Ufullstendig -> oppdaterBosituasjon(bosituasjon)
        }
    }

    /**
     * Oppdaterer bosituasjon ufullstendig.
     * Tenkt brukt i steget der man velger om søker har EPS eller ikke, men før man tar stilling til om EPS er ufør/67+ eller om man bor med voksne/er enslig.
     *
     * Fjerner EPS sin formue/fradrag dersom søker ikke har EPS.
     * */
    private fun oppdaterBosituasjon(
        bosituasjon: Grunnlag.Bosituasjon.Ufullstendig,
    ): GrunnlagsdataOgVilkårsvurderinger {
        if (this is Revurdering) throw IllegalArgumentException("Kan ikke oppdatere med en ufullstendig bosituasjon for revurdering.")
        return oppdaterBosituasjonInternal(
            bosituasjon = bosituasjon,
            oppdaterGrunnlagsdata = Grunnlagsdata::tryCreateTillatUfullstendigBosituasjon,
        )
    }

    /**
     * Oppdaterer bosituasjon med en fullstendig.
     * Tenkt brukt når man fullfører bosituasjonssteget i søknadsbehandling og for revurdering og evt. regulering/o.l.
     *
     * Fjerner EPS sin formue/fradrag dersom søker ikke har EPS.
     * */
    private fun oppdaterBosituasjon(
        bosituasjon: Grunnlag.Bosituasjon.Fullstendig,
    ): GrunnlagsdataOgVilkårsvurderinger {
        return oppdaterBosituasjonInternal(
            bosituasjon = bosituasjon,
            oppdaterGrunnlagsdata = Grunnlagsdata::tryCreate,
        )
    }

    private fun oppdaterBosituasjonInternal(
        bosituasjon: Grunnlag.Bosituasjon,
        oppdaterGrunnlagsdata: (fradragsgrunnlag: List<Grunnlag.Fradragsgrunnlag>, bosituasjon: List<Grunnlag.Bosituasjon>) -> Either<KunneIkkeLageGrunnlagsdata, Grunnlagsdata>,
    ): GrunnlagsdataOgVilkårsvurderinger {
        val oppdatertGrunnlagsdata = oppdaterGrunnlagsdata(
            grunnlagsdata.fradragsgrunnlag.fjernFradragForEPSHvisEnslig(bosituasjon),
            listOf(bosituasjon),
        ).getOrHandle {
            when (it) {
                KunneIkkeLageGrunnlagsdata.FradragForEPSMenBosituasjonUtenEPS -> throw IllegalStateException("Fradrag for EPS skulle ha vært fjernet.")
                KunneIkkeLageGrunnlagsdata.FradragManglerBosituasjon -> throw IllegalStateException("Bosituasjonsperioden har blitt satt feil sammenlignet med fradrag")
                KunneIkkeLageGrunnlagsdata.MåLeggeTilBosituasjonFørFradrag -> throw IllegalStateException("Dette er metoden for å oppdatere bosituasjon. Vi har en implementasjonsfeil ved at fradrag blir lagt til uten bosituasjon")
                is KunneIkkeLageGrunnlagsdata.UgyldigFradragsgrunnlag -> throw IllegalStateException("Eneste endringen vi potensialt har gjort, er å fjerne fradrag for EPS")
                is KunneIkkeLageGrunnlagsdata.Konsistenssjekk -> throw IllegalStateException("Inkonsistens mellom bosituasjon og fradrag: ${it.feil::class}")
            }
        }
        return when (this) {
            is Revurdering -> Revurdering(
                grunnlagsdata = oppdatertGrunnlagsdata,
                vilkårsvurderinger = vilkårsvurderinger.nullstillEpsFormueHvisIngenEps(bosituasjon),
            )
            is Søknadsbehandling -> Søknadsbehandling(
                grunnlagsdata = oppdatertGrunnlagsdata,
                vilkårsvurderinger = vilkårsvurderinger.nullstillEpsFormueHvisIngenEps(bosituasjon),
            )
        }
    }

    data class Søknadsbehandling(
        override val grunnlagsdata: Grunnlagsdata,
        override val vilkårsvurderinger: Vilkårsvurderinger.Søknadsbehandling,
    ) : GrunnlagsdataOgVilkårsvurderinger() {

        override fun oppdaterBosituasjon(bosituasjon: Grunnlag.Bosituasjon): Søknadsbehandling {
            return super.oppdaterBosituasjon(bosituasjon) as Søknadsbehandling
        }
        /**
         * Bytt til en Either dersom man ikke ønsker kaste exceptions herfra lenger.
         */
        fun oppdaterFormuegrunnlag(
            grunnlag: Vilkår.Formue.Vurdert,
        ): Søknadsbehandling {
            // TODO jah: Dette er sjekker som alltid bør gjøres før man får en instans av denne typen.
            //  både for ctor og copy (kun init som kan garantere dette).
            //  det blir litt for omfattende i denne omgangen.
            require(grunnlag.perioder.erSammenhengendeSortertOgUtenDuplikater()) {
                "For søknadsbehandling krever vi sammenhengende perioder. Merk at dette ikke gjelder for andre stønadsbehandlinger som revurdering/regulering."
            }
            // TODO jah: Konsistenssjekken gjøres også av LeggTilFormuegrunnlagRequest.toDomain() så det bør være trygt å kaste her.
            //  felles sjekker bør gjøres i init og tryCreate, konsistenssjekkene bør gjøres i denne fila for det som går på tvers av grunnlagsdata og vilkårsvurderinger.
            //  Mens behandlingene bør sjekke mot sin periode og evt. andre ting som kun angår de.
            SjekkOmGrunnlagErKonsistent.BosituasjonOgFormue(
                bosituasjon = grunnlagsdata.bosituasjon,
                formue = grunnlag.vurderingsperioder.map { it.grunnlag },
            ).resultat.tapLeft {
                it.forEach {
                    when (it) {
                        Konsistensproblem.BosituasjonOgFormue.FormueForEPSManglerForBosituasjonsperiode,
                        Konsistensproblem.BosituasjonOgFormue.IngenFormueForBosituasjonsperiode,
                        Konsistensproblem.BosituasjonOgFormue.KombinasjonAvBosituasjonOgFormueErUyldig,
                        is Konsistensproblem.BosituasjonOgFormue.UgyldigFormue,
                        -> throw IllegalArgumentException(
                            "Konsistenssjekk mellom bosituasjon og formue feilet: $it",
                        )
                        is Konsistensproblem.BosituasjonOgFormue.UgyldigBosituasjon -> it.feil.forEach {
                            when (it) {
                                Konsistensproblem.Bosituasjon.Mangler,
                                Konsistensproblem.Bosituasjon.Overlapp -> throw IllegalArgumentException(
                                    "Konsistenssjekk mellom bosituasjon og formue feilet: $it",
                                )
                                Konsistensproblem.Bosituasjon.Ufullstendig -> Unit // Bosituasjon trenger ikke være fullstendig på dette tidspunktet.
                            }
                        }
                    }
                }
            }

            return vilkårsvurderinger.leggTil(grunnlag).let {
                this.copy(
                    vilkårsvurderinger = it,
                )
            }
        }

        init {
            kastHvisPerioderIkkeErLike()
        }
    }

    data class Revurdering(
        override val grunnlagsdata: Grunnlagsdata,
        override val vilkårsvurderinger: Vilkårsvurderinger.Revurdering,
    ) : GrunnlagsdataOgVilkårsvurderinger() {

        override fun oppdaterBosituasjon(bosituasjon: Grunnlag.Bosituasjon): Revurdering {
            return super.oppdaterBosituasjon(bosituasjon) as Revurdering
        }

        init {
            kastHvisPerioderIkkeErLike()
        }
    }
}
