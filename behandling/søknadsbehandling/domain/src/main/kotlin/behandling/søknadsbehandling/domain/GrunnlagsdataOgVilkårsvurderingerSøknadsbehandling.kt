package behandling.søknadsbehandling.domain

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.domain.Stønadsperiode
import no.nav.su.se.bakover.common.tid.periode.erSammenhengendeSortertOgUtenDuplikater
import vilkår.bosituasjon.domain.grunnlag.Bosituasjon
import vilkår.bosituasjon.domain.grunnlag.Bosituasjon.Companion.harFjernetEllerEndretEps
import vilkår.bosituasjon.domain.grunnlag.Bosituasjon.Companion.perioderMedEPS
import vilkår.bosituasjon.domain.grunnlag.Bosituasjon.Companion.perioderUtenEPS
import vilkår.common.domain.Vilkår
import vilkår.formue.domain.FormueVilkår
import vilkår.formue.domain.FormuegrenserFactory
import vilkår.inntekt.domain.grunnlag.Fradragsgrunnlag
import vilkår.inntekt.domain.grunnlag.Fradragstype
import vilkår.opplysningsplikt.domain.OpplysningspliktVilkår
import vilkår.vurderinger.domain.BosituasjonOgFormue
import vilkår.vurderinger.domain.EksterneGrunnlag
import vilkår.vurderinger.domain.EksterneGrunnlagSkatt
import vilkår.vurderinger.domain.Grunnlagsdata
import vilkår.vurderinger.domain.GrunnlagsdataOgVilkårsvurderinger
import vilkår.vurderinger.domain.Konsistensproblem
import vilkår.vurderinger.domain.KunneIkkeLageGrunnlagsdata
import vilkår.vurderinger.domain.Vilkårsvurderinger
import vilkår.vurderinger.domain.fjernFradragEPS
import vilkår.vurderinger.domain.kastHvisPerioderIkkeErLike
import java.time.Clock

data class GrunnlagsdataOgVilkårsvurderingerSøknadsbehandling(
    override val grunnlagsdata: Grunnlagsdata,
    override val vilkårsvurderinger: VilkårsvurderingerSøknadsbehandling,
    override val eksterneGrunnlag: EksterneGrunnlag,
) : GrunnlagsdataOgVilkårsvurderinger {
    init {
        kastHvisPerioderIkkeErLike()
    }

    override fun oppdaterVilkår(vilkår: Vilkår): GrunnlagsdataOgVilkårsvurderingerSøknadsbehandling {
        return copy(vilkårsvurderinger = vilkårsvurderinger.oppdaterVilkår(vilkår))
    }

    override fun oppdaterGrunnlagsdata(grunnlagsdata: Grunnlagsdata): GrunnlagsdataOgVilkårsvurderingerSøknadsbehandling {
        return copy(grunnlagsdata = grunnlagsdata)
    }

    override fun oppdaterVilkårsvurderinger(vilkårsvurderinger: Vilkårsvurderinger): GrunnlagsdataOgVilkårsvurderingerSøknadsbehandling {
        // TODO jah: Dersom vi skal slippe cast, må vi utvide GrunnlagsdataOgVilkårsvurderinger med en generisk type for vilkårsvurderinger
        return copy(vilkårsvurderinger = vilkårsvurderinger as VilkårsvurderingerSøknadsbehandling)
    }

    override fun oppdaterFradragsgrunnlag(fradragsgrunnlag: List<Fradragsgrunnlag>): GrunnlagsdataOgVilkårsvurderingerSøknadsbehandling {
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

    override fun oppdaterEksterneGrunnlag(eksternGrunnlag: EksterneGrunnlag): GrunnlagsdataOgVilkårsvurderingerSøknadsbehandling {
        return this.copy(eksterneGrunnlag = eksternGrunnlag)
    }

    override fun oppdaterOpplysningsplikt(opplysningspliktVilkår: OpplysningspliktVilkår): GrunnlagsdataOgVilkårsvurderingerSøknadsbehandling {
        return this.copy(
            vilkårsvurderinger = vilkårsvurderinger.oppdaterVilkår(
                vilkår = opplysningspliktVilkår,
            ),
        )
    }

    override fun copyWithNewIds(): GrunnlagsdataOgVilkårsvurderingerSøknadsbehandling = this.copy(
        grunnlagsdata = grunnlagsdata.copyWithNewIds(),
        vilkårsvurderinger = vilkårsvurderinger.copyWithNewIds() as VilkårsvurderingerSøknadsbehandling,
        eksterneGrunnlag = eksterneGrunnlag.copyWithNewIds(),
    )

    fun leggTilSkatt(
        skatt: EksterneGrunnlagSkatt,
    ): GrunnlagsdataOgVilkårsvurderingerSøknadsbehandling {
        return this.copy(
            eksterneGrunnlag = eksterneGrunnlag.leggTilSkatt(skatt),
        )
    }

    /**
     * Fjerner EPS sin formue/fradrag dersom søker ikke har EPS.
     * */
    fun oppdaterBosituasjon(
        bosituasjon: List<Bosituasjon.Fullstendig>,
    ): GrunnlagsdataOgVilkårsvurderingerSøknadsbehandling {
        val grunnlagsdataJustertForEPS = Grunnlagsdata.tryCreate(
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

        return GrunnlagsdataOgVilkårsvurderingerSøknadsbehandling(
            grunnlagsdata = grunnlagsdataJustertForEPS,
            vilkårsvurderinger = vilkårsvurderinger.oppdaterVilkår(formueJustertForEPS),
            eksterneGrunnlag = if (this.grunnlagsdata.bosituasjon.harFjernetEllerEndretEps(bosituasjon)) {
                eksterneGrunnlag.fjernEps()
            } else {
                eksterneGrunnlag
            },
        )
    }

    fun oppdaterStønadsperiode(
        stønadsperiode: Stønadsperiode,
        formuegrenserFactory: FormuegrenserFactory,
        clock: Clock,
    ): Either<KunneIkkeLageGrunnlagsdata, GrunnlagsdataOgVilkårsvurderingerSøknadsbehandling> {
        return GrunnlagsdataOgVilkårsvurderingerSøknadsbehandling(
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
    ): GrunnlagsdataOgVilkårsvurderingerSøknadsbehandling {
        // TODO jah: Dette er sjekker som alltid bør gjøres før man får en instans av denne typen.
        //  både for ctor og copy (kun init som kan garantere dette).
        //  det blir litt for omfattende i denne omgangen.
        require(vilkår.perioder.erSammenhengendeSortertOgUtenDuplikater()) {
            "For søknadsbehandling krever vi sammenhengende perioder. Merk at dette ikke gjelder for andre stønadsbehandlinger som revurdering/regulering."
        }
        // TODO jah: Konsistenssjekken gjøres også av LeggTilFormuegrunnlagRequest.toDomain() så det bør være trygt å kaste her.
        //  felles sjekker bør gjøres i init og tryCreate, konsistenssjekkene bør gjøres i denne fila for det som går på tvers av grunnlagsdata og vilkårsvurderinger.
        //  Mens behandlingene bør sjekke mot sin periode og evt. andre ting som kun angår de.
        BosituasjonOgFormue(
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
}
