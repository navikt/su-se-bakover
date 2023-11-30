package no.nav.su.se.bakover.domain.grunnlag

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.nonEmptyListOf
import arrow.core.right
import beregning.domain.fradrag.Fradragstype
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.erSammenhengendeSortertOgUtenDuplikater
import no.nav.su.se.bakover.domain.grunnlag.Bosituasjon.Companion.harFjernetEllerEndretEps
import no.nav.su.se.bakover.domain.grunnlag.Bosituasjon.Companion.perioderMedEPS
import no.nav.su.se.bakover.domain.grunnlag.Bosituasjon.Companion.perioderUtenEPS
import no.nav.su.se.bakover.domain.søknadsbehandling.stønadsperiode.Stønadsperiode
import no.nav.su.se.bakover.domain.vilkår.FormueVilkår
import no.nav.su.se.bakover.domain.vilkår.KunneIkkeLageOpplysningspliktVilkår
import no.nav.su.se.bakover.domain.vilkår.OpplysningspliktVilkår
import no.nav.su.se.bakover.domain.vilkår.Vilkår
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderingsresultat
import no.nav.su.se.bakover.domain.vilkår.VurderingsperiodeOpplysningsplikt
import vilkår.formue.domain.FormuegrenserFactory
import java.time.Clock
import java.util.UUID

sealed interface GrunnlagsdataOgVilkårsvurderinger {
    val grunnlagsdata: Grunnlagsdata
    val vilkårsvurderinger: Vilkårsvurderinger
    val eksterneGrunnlag: EksterneGrunnlag

    val eps: List<Fnr> get() = grunnlagsdata.eps

    fun periode(): Periode? {
        return grunnlagsdata.periode ?: vilkårsvurderinger.periode
    }

    fun erVurdert(): Boolean = vilkårsvurderinger.erVurdert && grunnlagsdata.erUtfylt
    fun harVurdertOpplysningsplikt(): Boolean = vilkårsvurderinger.opplysningsplikt is OpplysningspliktVilkår.Vurdert

    fun oppdaterVilkår(vilkår: Vilkår): GrunnlagsdataOgVilkårsvurderinger

    fun oppdaterGrunnlagsdata(grunnlagsdata: Grunnlagsdata): GrunnlagsdataOgVilkårsvurderinger
    fun oppdaterVilkårsvurderinger(vilkårsvurderinger: Vilkårsvurderinger): GrunnlagsdataOgVilkårsvurderinger

    /**
     * Erstatter eksisterende fradragsgrunnlag med nye.
     */
    fun oppdaterFradragsgrunnlag(fradragsgrunnlag: List<Fradragsgrunnlag>): GrunnlagsdataOgVilkårsvurderinger
    fun leggTilSkatt(skatt: EksterneGrunnlagSkatt): Søknadsbehandling

    /**
     * Fjerner EPS sin formue/fradrag dersom søker ikke har EPS.
     * */
    fun oppdaterBosituasjon(bosituasjon: List<Bosituasjon.Fullstendig>): GrunnlagsdataOgVilkårsvurderinger {
        return oppdaterBosituasjonFullstendig(bosituasjon)
    }

    private fun oppdaterBosituasjonInternal(
        bosituasjon: List<Bosituasjon.Fullstendig>,
        oppdaterGrunnlagsdata: (fradragsgrunnlag: List<Fradragsgrunnlag>, bosituasjon: List<Bosituasjon.Fullstendig>) -> Either<KunneIkkeLageGrunnlagsdata, Grunnlagsdata>,
    ): GrunnlagsdataOgVilkårsvurderinger {
        val grunnlagsdataJustertForEPS = oppdaterGrunnlagsdata(
            /*
             * Hvis vi går fra "eps" til "ingen eps" må vi fjerne fradragene for EPS for alle periodene
             * hvor det eksiterer fradrag for EPS. Ved endring fra "ingen eps" til "eps" er det umulig for
             * oss å vite om det skal eksistere fradrag, caset er derfor uhåndtert (opp til saksbehandler).
             */
            grunnlagsdata.fradragsgrunnlag.fjernFradragEPS(bosituasjon.perioderUtenEPS()),
            bosituasjon,
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
                    vilkårsvurderinger = vilkårsvurderinger.oppdaterVilkår(formueJustertForEPS),
                )
            }

            is Søknadsbehandling -> {
                Søknadsbehandling(
                    grunnlagsdata = grunnlagsdataJustertForEPS,
                    vilkårsvurderinger = vilkårsvurderinger.oppdaterVilkår(formueJustertForEPS),
                    eksterneGrunnlag = if (this.grunnlagsdata.bosituasjon.harFjernetEllerEndretEps(bosituasjon)) {
                        eksterneGrunnlag.fjernEps()
                    } else {
                        eksterneGrunnlag
                    },
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
        bosituasjon: List<Bosituasjon.Fullstendig>,
    ): GrunnlagsdataOgVilkårsvurderinger {
        return oppdaterBosituasjonInternal(
            bosituasjon = bosituasjon,
            oppdaterGrunnlagsdata = Grunnlagsdata::tryCreate,

        )
    }

    /* jah: Beholdes så lenge command-typene støtter fradragstypen avkorting. En mulighet er å splitte fradragstypene i command/query slik at vi ikke trenger bekymre oss for at ugyldige fradrag sniker seg inn i beregningen. */
    fun harAvkortingsfradrag(): Boolean
    fun oppdaterEksterneGrunnlag(eksternGrunnlag: EksterneGrunnlag): GrunnlagsdataOgVilkårsvurderinger
    fun oppdaterOpplysningsplikt(opplysningspliktVilkår: OpplysningspliktVilkår): GrunnlagsdataOgVilkårsvurderinger

    data class Søknadsbehandling(
        override val grunnlagsdata: Grunnlagsdata,
        override val vilkårsvurderinger: Vilkårsvurderinger.Søknadsbehandling,
        override val eksterneGrunnlag: EksterneGrunnlag,
    ) : GrunnlagsdataOgVilkårsvurderinger {
        override fun oppdaterVilkår(vilkår: Vilkår): Søknadsbehandling {
            return copy(vilkårsvurderinger = vilkårsvurderinger.oppdaterVilkår(vilkår))
        }

        override fun oppdaterGrunnlagsdata(grunnlagsdata: Grunnlagsdata): Søknadsbehandling {
            return copy(grunnlagsdata = grunnlagsdata)
        }

        override fun oppdaterVilkårsvurderinger(vilkårsvurderinger: Vilkårsvurderinger): Søknadsbehandling {
            // TODO jah: Dersom vi skal slippe cast, må vi utvide GrunnlagsdataOgVilkårsvurderinger med en generisk type for vilkårsvurderinger
            return copy(vilkårsvurderinger = vilkårsvurderinger as Vilkårsvurderinger.Søknadsbehandling)
        }

        override fun oppdaterFradragsgrunnlag(fradragsgrunnlag: List<Fradragsgrunnlag>): Søknadsbehandling {
            return copy(
                grunnlagsdata = Grunnlagsdata.tryCreate(
                    fradragsgrunnlag = fradragsgrunnlag,
                    bosituasjon = grunnlagsdata.bosituasjonSomFullstendig(),
                ).getOrElse { throw IllegalArgumentException(it.toString()) },
            )
        }

        /* jah: Beholdes så lenge command-typene støtter fradragstypen avkorting. En mulighet er å splitte fradragstypene i command/query slik at vi ikke trenger bekymre oss for at ugyldige fradrag sniker seg inn i beregningen. */
        override fun harAvkortingsfradrag(): Boolean {
            return grunnlagsdata.fradragsgrunnlag.any { it.fradrag.fradragstype == Fradragstype.AvkortingUtenlandsopphold }
        }

        override fun oppdaterEksterneGrunnlag(eksternGrunnlag: EksterneGrunnlag): Søknadsbehandling {
            return this.copy(eksterneGrunnlag = eksternGrunnlag)
        }

        override fun oppdaterOpplysningsplikt(opplysningspliktVilkår: OpplysningspliktVilkår): Søknadsbehandling {
            return this.copy(
                vilkårsvurderinger = vilkårsvurderinger.oppdaterVilkår(
                    vilkår = opplysningspliktVilkår,
                ),
            )
        }

        override fun leggTilSkatt(
            skatt: EksterneGrunnlagSkatt,
        ): Søknadsbehandling {
            return this.copy(
                eksterneGrunnlag = eksterneGrunnlag.leggTilSkatt(skatt),
            )
        }

        override fun oppdaterBosituasjon(bosituasjon: List<Bosituasjon.Fullstendig>): Søknadsbehandling {
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
                eksterneGrunnlag = eksterneGrunnlag,
            ).right()
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

            return oppdaterVilkår(vilkår)
        }

        init {
            kastHvisPerioderIkkeErLike()
        }
    }

    data class Revurdering(
        override val grunnlagsdata: Grunnlagsdata,
        override val vilkårsvurderinger: Vilkårsvurderinger.Revurdering,
        override val eksterneGrunnlag: EksterneGrunnlag = StøtterIkkeHentingAvEksternGrunnlag,
    ) : GrunnlagsdataOgVilkårsvurderinger {
        init {
            kastHvisPerioderIkkeErLike()
        }

        override fun oppdaterBosituasjon(bosituasjon: List<Bosituasjon.Fullstendig>): Revurdering {
            return super.oppdaterBosituasjon(bosituasjon) as Revurdering
        }

        override fun oppdaterVilkår(vilkår: Vilkår): Revurdering {
            return copy(vilkårsvurderinger = vilkårsvurderinger.oppdaterVilkår(vilkår))
        }

        override fun oppdaterGrunnlagsdata(grunnlagsdata: Grunnlagsdata): Revurdering {
            return copy(grunnlagsdata = grunnlagsdata)
        }

        override fun oppdaterVilkårsvurderinger(vilkårsvurderinger: Vilkårsvurderinger): Revurdering {
            // TODO jah: Dersom vi skal slippe cast, må vi utvide GrunnlagsdataOgVilkårsvurderinger med en generisk type for vilkårsvurderinger
            return copy(vilkårsvurderinger = vilkårsvurderinger as Vilkårsvurderinger.Revurdering)
        }

        override fun oppdaterFradragsgrunnlag(fradragsgrunnlag: List<Fradragsgrunnlag>): Revurdering {
            return copy(grunnlagsdata = grunnlagsdata.copy(fradragsgrunnlag = fradragsgrunnlag))
        }

        /* jah: Beholdes så lenge command-typene støtter fradragstypen avkorting. En mulighet er å splitte fradragstypene i command/query slik at vi ikke trenger bekymre oss for at ugyldige fradrag sniker seg inn i beregningen. */
        override fun harAvkortingsfradrag(): Boolean {
            return grunnlagsdata.fradragsgrunnlag.any { it.fradrag.fradragstype == Fradragstype.AvkortingUtenlandsopphold }
        }

        override fun oppdaterEksterneGrunnlag(eksternGrunnlag: EksterneGrunnlag): Revurdering {
            throw UnsupportedOperationException("Støtter ikke å legge til eksterne grunnlag for revurdering")
        }

        override fun oppdaterOpplysningsplikt(opplysningspliktVilkår: OpplysningspliktVilkår): Revurdering {
            return this.copy(
                vilkårsvurderinger = vilkårsvurderinger.oppdaterVilkår(
                    vilkår = opplysningspliktVilkår,
                ),
            )
        }

        override fun leggTilSkatt(skatt: EksterneGrunnlagSkatt): Nothing {
            throw UnsupportedOperationException("Støtter ikke å legge til skatt fra ekstern kilde for revurdering")
        }
    }
}

inline fun <reified T : GrunnlagsdataOgVilkårsvurderinger> GrunnlagsdataOgVilkårsvurderinger.avslåPgaOpplysningsplikt(
    tidspunkt: Tidspunkt,
    periode: Periode,
): Either<KunneIkkeLageOpplysningspliktVilkår, T> {
    return OpplysningspliktVilkår.Vurdert.tryCreate(
        vurderingsperioder = nonEmptyListOf(
            VurderingsperiodeOpplysningsplikt.create(
                id = UUID.randomUUID(),
                opprettet = tidspunkt,
                periode = periode,
                grunnlag = Opplysningspliktgrunnlag(
                    id = UUID.randomUUID(),
                    opprettet = tidspunkt,
                    periode = periode,
                    beskrivelse = OpplysningspliktBeskrivelse.UtilstrekkeligDokumentasjon,
                ),
            ),
        ),
    ).map { oppdaterOpplysningsplikt(it) as T }
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

/**
 * Skal kun kastes fra undertyper av [GrunnlagsdataOgVilkårsvurderinger]
 */
internal fun GrunnlagsdataOgVilkårsvurderinger.kastHvisPerioderIkkeErLike() {
    if (grunnlagsdata.periode != null && vilkårsvurderinger.periode != null) {
        require(grunnlagsdata.periode == vilkårsvurderinger.periode) {
            "Grunnlagsdataperioden (${grunnlagsdata.periode}) må være lik vilkårsvurderingerperioden (${vilkårsvurderinger.periode})"
        }
        // TODO jah: Dersom periodene ikke er sammenhengende bør vi heller sjekke at periodene er like.
    }
    // TODO jah: Grunnlagsdata og Vilkårsvurderinger bør ha tilsvarende sjekk og vurderingsperiodene bør sjekke at de er sortert og uten duplikater
}
