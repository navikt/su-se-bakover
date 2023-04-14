package no.nav.su.se.bakover.domain.grunnlag

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.Fnr
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.periode.erSammenhengendeSortertOgUtenDuplikater
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag.Bosituasjon.Companion.harFjernetEllerEndretEps
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag.Bosituasjon.Companion.perioderMedEPS
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag.Bosituasjon.Companion.perioderUtenEPS
import no.nav.su.se.bakover.domain.skatt.Skattereferanser
import no.nav.su.se.bakover.domain.søknadsbehandling.stønadsperiode.Stønadsperiode
import no.nav.su.se.bakover.domain.vilkår.FormueVilkår
import no.nav.su.se.bakover.domain.vilkår.FormuegrenserFactory
import no.nav.su.se.bakover.domain.vilkår.Vilkår
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderingsresultat
import java.time.Clock

sealed class GrunnlagsdataOgVilkårsvurderinger {
    abstract val grunnlagsdata: Grunnlagsdata
    abstract val vilkårsvurderinger: Vilkårsvurderinger

    val eps: List<Fnr> get() = grunnlagsdata.eps

    fun periode(): Periode? {
        return grunnlagsdata.periode ?: vilkårsvurderinger.periode
    }

    fun erVurdert(): Boolean = vilkårsvurderinger.erVurdert && grunnlagsdata.erUtfylt

    abstract fun leggTil(vilkår: Vilkår): GrunnlagsdataOgVilkårsvurderinger
    abstract fun leggTilFradragsgrunnlag(grunnlag: List<Grunnlag.Fradragsgrunnlag>): GrunnlagsdataOgVilkårsvurderinger

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
     * Fjerner EPS sin formue/fradrag dersom søker ikke har EPS.
     * */
    open fun oppdaterBosituasjon(bosituasjon: List<Grunnlag.Bosituasjon.Fullstendig>): GrunnlagsdataOgVilkårsvurderinger {
        return oppdaterBosituasjonFullstendig(bosituasjon)
    }

    private fun oppdaterBosituasjonInternal(
        bosituasjon: List<Grunnlag.Bosituasjon.Fullstendig>,
        oppdaterGrunnlagsdata: (fradragsgrunnlag: List<Grunnlag.Fradragsgrunnlag>, bosituasjon: List<Grunnlag.Bosituasjon.Fullstendig>, skattereferanser: Skattereferanser?) -> Either<KunneIkkeLageGrunnlagsdata, Grunnlagsdata>,
    ): GrunnlagsdataOgVilkårsvurderinger {
        val skattereferanser = this.grunnlagsdata.skattereferanser

        val grunnlagsdataJustertForEPS = oppdaterGrunnlagsdata(
            /*
             * Hvis vi går fra "eps" til "ingen eps" må vi fjerne fradragene for EPS for alle periodene
             * hvor det eksiterer fradrag for EPS. Ved endring fra "ingen eps" til "eps" er det umulig for
             * oss å vite om det skal eksistere fradrag, caset er derfor uhåndtert (opp til saksbehandler).
             */
            grunnlagsdata.fradragsgrunnlag.fjernFradragEPS(bosituasjon.perioderUtenEPS()),
            bosituasjon,
            /**
             * TODO: Ved revurdering vil vi fjerne bare den EPS'en som har blitt tatt vekk.
             */
            if (this.grunnlagsdata.bosituasjon.harFjernetEllerEndretEps(bosituasjon)) skattereferanser?.fjernEps() else this.grunnlagsdata.skattereferanser,
        ).getOrElse {
            throw IllegalStateException(it.toString())
        }

        val formueJustertForEPS = vilkårsvurderinger.formue
            /*
             * Hvis vi går fra "ingen eps" til "eps" må vi fylle på med tomme verdier for EPS formue for
             * periodene hvor vi tidligere ikke hadde eps.
             */
            .leggTilTomEPSFormueHvisDetMangler(bosituasjon.perioderMedEPS())
            /*
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
        override fun leggTil(vilkår: Vilkår): Søknadsbehandling {
            return copy(vilkårsvurderinger = vilkårsvurderinger.leggTil(vilkår))
        }

        override fun leggTilFradragsgrunnlag(grunnlag: List<Grunnlag.Fradragsgrunnlag>): Søknadsbehandling {
            return copy(
                grunnlagsdata = Grunnlagsdata.tryCreate(
                    fradragsgrunnlag = grunnlag,
                    bosituasjon = grunnlagsdata.bosituasjonSomFullstendig(),
                    skattereferanser = grunnlagsdata.skattereferanser,
                ).getOrElse { throw IllegalArgumentException(it.toString()) },
            )
        }

        override fun oppdaterBosituasjon(bosituasjon: List<Grunnlag.Bosituasjon.Fullstendig>): Søknadsbehandling {
            return super.oppdaterBosituasjon(bosituasjon) as Søknadsbehandling
        }

        fun oppdaterStønadsperiode(
            stønadsperiode: Stønadsperiode,
            formuegrenserFactory: FormuegrenserFactory,
            clock: Clock,
        ): Either<KunneIkkeLageGrunnlagsdata, Søknadsbehandling> {
            return Søknadsbehandling(
                grunnlagsdata = grunnlagsdata.oppdaterGrunnlagsperioder(
                    oppdatertPeriode = stønadsperiode.periode,
                    clock = clock,
                ).getOrElse { return it.left() },
                vilkårsvurderinger = vilkårsvurderinger.oppdaterStønadsperiode(
                    stønadsperiode = stønadsperiode,
                    formuegrenserFactory = formuegrenserFactory,
                ),
            ).right()
        }

        fun leggTilSkattereferanser(skattereferanser: Skattereferanser): Søknadsbehandling {
            return this.copy(
                grunnlagsdata = this.grunnlagsdata.leggTilSkattereferanser(
                    skattereferanser,
                ),
            )
        }

        /**
         * Bytt til en Either dersom man ikke ønsker kaste exceptions herfra lenger.
         */
        fun oppdaterFormuevilkår(
            vilkår: FormueVilkår.Vurdert,
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
            ).resultat.onLeft { alleFeil ->
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

                                Konsistensproblem.Bosituasjon.Ufullstendig -> throw IllegalStateException("Bosituasjon kan ikke være")
                            }
                        }
                    }
                }
            }

            return leggTil(vilkår)
        }

        init {
            kastHvisPerioderIkkeErLike()
        }
    }

    data class Revurdering(
        override val grunnlagsdata: Grunnlagsdata,
        override val vilkårsvurderinger: Vilkårsvurderinger.Revurdering,
    ) : GrunnlagsdataOgVilkårsvurderinger() {

        override fun oppdaterBosituasjon(bosituasjon: List<Grunnlag.Bosituasjon.Fullstendig>): Revurdering {
            return super.oppdaterBosituasjon(bosituasjon) as Revurdering
        }

        init {
            kastHvisPerioderIkkeErLike()
        }

        override fun leggTil(vilkår: Vilkår): Revurdering {
            return copy(vilkårsvurderinger = vilkårsvurderinger.leggTil(vilkår))
        }

        override fun leggTilFradragsgrunnlag(grunnlag: List<Grunnlag.Fradragsgrunnlag>): Revurdering {
            return copy(grunnlagsdata = grunnlagsdata.copy(fradragsgrunnlag = grunnlag))
        }
    }
}

fun GrunnlagsdataOgVilkårsvurderinger.krevAlleVilkårInnvilget() {
    vilkårsvurderinger.vurdering.also {
        check(it is Vilkårsvurderingsresultat.Innvilget) { "Ugyldig tilstand, alle vilkår må være innvilget, var: $it" }
    }
}

fun GrunnlagsdataOgVilkårsvurderinger.krevMinstEttAvslag() {
    vilkårsvurderinger.vurdering.also {
        check(it is Vilkårsvurderingsresultat.Avslag) { "Ugyldig tilstand, minst et vilkår må være avslått, var: $it" }
    }
}
