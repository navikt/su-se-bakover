package no.nav.su.se.bakover.domain.grunnlag

import arrow.core.Either
import arrow.core.getOrHandle
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.periode.erSammenhengendeSortertOgUtenDuplikater
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag.Bosituasjon.Companion.perioderMedEPS
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag.Bosituasjon.Companion.perioderUtenEPS
import no.nav.su.se.bakover.domain.søknadsbehandling.Stønadsperiode
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.vilkår.FormuegrenserFactory
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
    @Suppress("UNCHECKED_CAST")
    open fun oppdaterBosituasjon(bosituasjon: List<Grunnlag.Bosituasjon>): GrunnlagsdataOgVilkårsvurderinger {
        return when {
            bosituasjon.all { it is Grunnlag.Bosituasjon.Ufullstendig } -> oppdaterBosituasjonUfullstendig(bosituasjon as List<Grunnlag.Bosituasjon.Ufullstendig>)
            bosituasjon.all { it is Grunnlag.Bosituasjon.Fullstendig } -> oppdaterBosituasjonFullstendig(bosituasjon as List<Grunnlag.Bosituasjon.Fullstendig>)
            else -> {
                throw IllegalArgumentException("Alle elementer i listen må ha samme grad av kompletthet")
            }
        }
    }

    private fun oppdaterBosituasjonInternal(
        bosituasjon: List<Grunnlag.Bosituasjon>,
        oppdaterGrunnlagsdata: (fradragsgrunnlag: List<Grunnlag.Fradragsgrunnlag>, bosituasjon: List<Grunnlag.Bosituasjon>) -> Either<KunneIkkeLageGrunnlagsdata, Grunnlagsdata>,
    ): GrunnlagsdataOgVilkårsvurderinger {
        val grunnlagsdataJustertForEPS = oppdaterGrunnlagsdata(
            /**
             * Hvis vi går fra "eps" til "ingen eps" må vi fjerne fradragene for EPS for alle periodene
             * hvor det eksiterer fradrag for EPS. Ved endring fra "ingen eps" til "eps" er det umulig for
             * oss å vite om det skal eksistere fradrag, caset er derfor uhåndtert (opp til saksbehandler).
             */
            grunnlagsdata.fradragsgrunnlag.fjernFradragEPS(bosituasjon.perioderUtenEPS()),
            bosituasjon,
        ).getOrHandle {
            throw IllegalStateException(it.toString())
        }

        val formueJustertForEPS = vilkårsvurderinger.formue
            /**
             * Hvis vi går fra "ingen eps" til "eps" må vi fylle på med tomme verdier for EPS formue for
             * periodene hvor vi tidligere ikke hadde eps.
             */
            .leggTilTomEPSFormueHvisDetMangler(bosituasjon.perioderMedEPS())
            /**
             * Hvis vi går fra "eps" til "ingen eps" må vi fjerne formue for alle periodene hvor vi
             * ikke lenger har eps.
             */
            .fjernEPSFormue(bosituasjon.perioderUtenEPS())
            .slåSammenLikePerioder()

        return when (this) {
            is Revurdering -> {
                Revurdering(
                    grunnlagsdata = grunnlagsdataJustertForEPS,
                    vilkårsvurderinger = vilkårsvurderinger.leggTil(formueJustertForEPS),
                )
            }
            is Søknadsbehandling -> {
                Søknadsbehandling(
                    grunnlagsdata = grunnlagsdataJustertForEPS,
                    vilkårsvurderinger = vilkårsvurderinger.leggTil(formueJustertForEPS),
                )
            }
        }
    }

    /**
     * Oppdaterer bosituasjon ufullstendig.
     * Tenkt brukt i steget der man velger om søker har EPS eller ikke, men før man tar stilling til om EPS er ufør/67+ eller om man bor med voksne/er enslig.
     *
     * Fjerner EPS sin formue/fradrag dersom søker ikke har EPS.
     * */
    private fun oppdaterBosituasjonUfullstendig(
        bosituasjon: List<Grunnlag.Bosituasjon.Ufullstendig>,
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
    private fun oppdaterBosituasjonFullstendig(
        bosituasjon: List<Grunnlag.Bosituasjon.Fullstendig>,
    ): GrunnlagsdataOgVilkårsvurderinger {
        return oppdaterBosituasjonInternal(
            bosituasjon = bosituasjon,
            oppdaterGrunnlagsdata = Grunnlagsdata::tryCreate,
        )
    }

    data class Søknadsbehandling(
        override val grunnlagsdata: Grunnlagsdata,
        override val vilkårsvurderinger: Vilkårsvurderinger.Søknadsbehandling,
    ) : GrunnlagsdataOgVilkårsvurderinger() {

        override fun oppdaterBosituasjon(bosituasjon: List<Grunnlag.Bosituasjon>): Søknadsbehandling {
            return super.oppdaterBosituasjon(bosituasjon) as Søknadsbehandling
        }

        fun oppdaterStønadsperiode(
            stønadsperiode: Stønadsperiode,
            formuegrenserFactory: FormuegrenserFactory,
        ): Søknadsbehandling {
            return Søknadsbehandling(
                grunnlagsdata = grunnlagsdata.oppdaterGrunnlagsperioder(
                    oppdatertPeriode = stønadsperiode.periode,
                ).getOrHandle { throw IllegalStateException(it.toString()) },
                vilkårsvurderinger = vilkårsvurderinger.oppdaterStønadsperiode(
                    stønadsperiode = stønadsperiode,
                    formuegrenserFactory = formuegrenserFactory,
                ),
            )
        }

        /**
         * Bytt til en Either dersom man ikke ønsker kaste exceptions herfra lenger.
         */
        fun oppdaterFormuevilkår(
            vilkår: Vilkår.Formue.Vurdert,
        ): Søknadsbehandling {
            // TODO jah: Dette er sjekker som alltid bør gjøres før man får en instans av denne typen.
            //  både for ctor og copy (kun init som kan garantere dette).
            //  det blir litt for omfattende i denne omgangen.
            require(vilkår.perioder.erSammenhengendeSortertOgUtenDuplikater()) {
                "For søknadsbehandling krever vi sammenhengende perioder. Merk at dette ikke gjelder for andre stønadsbehandlinger som revurdering/regulering."
            }
            // TODO jah: Konsistenssjekken gjøres også av LeggTilFormuegrunnlagRequest.toDomain() så det bør være trygt å kaste her.
            //  felles sjekker bør gjøres i init og tryCreate, konsistenssjekkene bør gjøres i denne fila for det som går på tvers av grunnlagsdata og vilkårsvurderinger.
            //  Mens behandlingene bør sjekke mot sin periode og evt. andre ting som kun angår de.
            SjekkOmGrunnlagErKonsistent.BosituasjonOgFormue(
                bosituasjon = grunnlagsdata.bosituasjon,
                formue = vilkår.grunnlag,
            ).resultat.tapLeft { alleFeil ->
                alleFeil.forEach { konsistensproblem ->
                    when (konsistensproblem) {
                        Konsistensproblem.BosituasjonOgFormue.FormueForEPSManglerForBosituasjonsperiode,
                        Konsistensproblem.BosituasjonOgFormue.IngenFormueForBosituasjonsperiode,
                        Konsistensproblem.BosituasjonOgFormue.KombinasjonAvBosituasjonOgFormueErUyldig,
                        is Konsistensproblem.BosituasjonOgFormue.UgyldigFormue,
                        -> throw IllegalArgumentException(
                            "Konsistenssjekk mellom bosituasjon og formue feilet: $konsistensproblem",
                        )
                        is Konsistensproblem.BosituasjonOgFormue.UgyldigBosituasjon -> konsistensproblem.feil.forEach {
                            when (it) {
                                Konsistensproblem.Bosituasjon.Mangler,
                                Konsistensproblem.Bosituasjon.Overlapp,
                                -> throw IllegalArgumentException(
                                    "Konsistenssjekk mellom bosituasjon og formue feilet: $it",
                                )
                                Konsistensproblem.Bosituasjon.Ufullstendig -> Unit // Bosituasjon trenger ikke være fullstendig på dette tidspunktet.
                            }
                        }
                    }
                }
            }

            return vilkårsvurderinger.leggTil(vilkår).let {
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

        fun oppdaterBosituasjon(bosituasjon: List<Grunnlag.Bosituasjon.Fullstendig>): Revurdering {
            return super.oppdaterBosituasjon(bosituasjon) as Revurdering
        }

        init {
            kastHvisPerioderIkkeErLike()
        }
    }
}
