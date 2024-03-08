package no.nav.su.se.bakover.domain.søknadsbehandling

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import behandling.søknadsbehandling.domain.GrunnlagsdataOgVilkårsvurderingerSøknadsbehandling
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.erSammenhengendeSortertOgUtenDuplikater
import no.nav.su.se.bakover.domain.søknadsbehandling.grunnlag.KunneIkkeLeggeTilSkattegrunnlag
import no.nav.su.se.bakover.domain.søknadsbehandling.stønadsperiode.Aldersvurdering
import no.nav.su.se.bakover.domain.søknadsbehandling.stønadsperiode.KunneIkkeOppdatereStønadsperiode
import no.nav.su.se.bakover.domain.søknadsbehandling.vilkår.KunneIkkeLeggeTilVilkår
import no.nav.su.se.bakover.domain.søknadsbehandling.vilkår.VilkårsfeilVedSøknadsbehandling
import no.nav.su.se.bakover.domain.vilkår.InstitusjonsoppholdVilkår
import no.nav.su.se.bakover.domain.vilkår.formue.LeggTilFormuevilkårRequest
import vilkår.bosituasjon.domain.grunnlag.Bosituasjon
import vilkår.common.domain.VurdertVilkår
import vilkår.familiegjenforening.domain.FamiliegjenforeningVilkår
import vilkår.fastopphold.domain.FastOppholdINorgeVilkår
import vilkår.flyktning.domain.FlyktningVilkår
import vilkår.formue.domain.FormuegrenserFactory
import vilkår.lovligopphold.domain.LovligOppholdVilkår
import vilkår.opplysningsplikt.domain.KunneIkkeLageOpplysningspliktVilkår
import vilkår.opplysningsplikt.domain.OpplysningspliktVilkår
import vilkår.pensjon.domain.PensjonsVilkår
import vilkår.personligoppmøte.domain.PersonligOppmøteVilkår
import vilkår.uføre.domain.UføreVilkår
import vilkår.utenlandsopphold.domain.vilkår.UtenlandsoppholdVilkår
import vilkår.vurderinger.domain.EksterneGrunnlagSkatt
import vilkår.vurderinger.domain.avslåPgaOpplysningsplikt
import java.time.Clock

/**
 * TODO jah: Mulig å splitte denne inn i 3 interfaces KanOppdatereStønadsperiode, KanOppdatereBosituasjon og KanOppdatereVilkår
 */
sealed interface KanOppdaterePeriodeBosituasjonVilkår : Søknadsbehandling, KanOppdaterePeriodeGrunnlagVilkår {

    abstract override fun leggTilSkatt(skatt: EksterneGrunnlagSkatt): Either<KunneIkkeLeggeTilSkattegrunnlag, KanOppdaterePeriodeBosituasjonVilkår>

    /**
     * Validerer at vurderingsperiodene er:
     * - innenfor behandlingsperioden
     * - dekker hele behandlingsperioden
     * - sortert
     * - sammenhengende
     * - uten duplikater
     *
     * @throws IllegalArgumentException dersom periode mangler (typisk hvis man har sendt inn et vilkår som ikke er vurdert)
     */
    private fun vilkårsvurder(
        saksbehandler: NavIdentBruker.Saksbehandler,
        vilkår: VurdertVilkår,
        tidspunkt: Tidspunkt,
        handling: SøknadsbehandlingsHandling,
    ): Either<VilkårsfeilVedSøknadsbehandling, VilkårsvurdertSøknadsbehandling> {
        if (!periode.inneholder(vilkår.perioder.toNonEmptyList())) {
            return VilkårsfeilVedSøknadsbehandling.VurderingsperiodeUtenforBehandlingsperiode.left()
        }
        if (!vilkår.perioder.erSammenhengendeSortertOgUtenDuplikater()) {
            // TODO jah: Dette bør være en generell ting som gjelder for alle vilkårsvurderinger -  bør flyttes inn i VilkårsvurdertSøknadsbehandling
            throw IllegalStateException("Vilkårsvurderingens perioder er ikke sammenhengende, sortert og uten duplikater. Saksnummer: ${this.saksnummer}, behandlingId: ${this.id}")
        }
        if (!periode.fullstendigOverlapp(vilkår.perioder.toNonEmptyList())) {
            return VilkårsfeilVedSøknadsbehandling.MåVurdereHelePerioden.left()
        }
        if (!vilkår.vurderingsperioder.all {
                // TODO jah: Dette er en generell ting som bør flyttes inn i VilkårsvurdertSøknadsbehandling
                it.vurdering == vilkår.vurderingsperioder.first().vurdering
            }
        ) {
            return VilkårsfeilVedSøknadsbehandling.AlleVurderingsperioderMåHaSammeResultat.left()
        }
        // Vi aksepterer at et vilkår kan ha flere vurderingsperioder på dette nivået siden f.eks. uføre må støtte dette.
        return VilkårsvurdertSøknadsbehandling.opprett(
            forrigeTilstand = this,
            saksbehandler = saksbehandler,
            grunnlagsdataOgVilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger.oppdaterVilkår(vilkår),
            tidspunkt = tidspunkt,
            handling = handling,
        ).right()
    }

    fun leggTilUtenlandsopphold(
        saksbehandler: NavIdentBruker.Saksbehandler,
        vilkår: UtenlandsoppholdVilkår.Vurdert,
    ): Either<KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilUtenlandsopphold, VilkårsvurdertSøknadsbehandling> {
        when {
            vilkår.vurderingsperioder.size != 1 -> {
                return KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilUtenlandsopphold.MåInneholdeKunEnVurderingsperiode.left()
            }
        }
        return vilkårsvurder(
            saksbehandler = saksbehandler,
            vilkår = vilkår,
            tidspunkt = vilkår.vurderingsperioder.first().opprettet,
            handling = SøknadsbehandlingsHandling.OppdatertUtenlandsopphold,
        ).mapLeft {
            KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilUtenlandsopphold.Vilkårsfeil(it)
        }
    }

    fun leggTilFormuegrunnlag(
        request: LeggTilFormuevilkårRequest,
        formuegrenserFactory: FormuegrenserFactory,
    ): Either<KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilFormuevilkår, VilkårsvurdertSøknadsbehandling> {
        // Valideringen gjøres i toDomain(...)
        val vilkår = request.toDomain(
            behandlingsperiode = stønadsperiode?.periode
                ?: throw IllegalStateException("Burde ha hatt en stønadsperiode på dette tidspunktet. id $id"),
            formuegrenserFactory = formuegrenserFactory,
        ).getOrElse {
            return KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilFormuevilkår.KunneIkkeMappeTilDomenet(it).left()
        }
        // TODO jah: Er det mulig å kalle vilkårsvurder(...) direkte her?
        return VilkårsvurdertSøknadsbehandling.opprett(
            forrigeTilstand = this,
            saksbehandler = saksbehandler,
            grunnlagsdataOgVilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger.oppdaterFormuevilkår(vilkår),
            tidspunkt = request.tidspunkt,
            handling = SøknadsbehandlingsHandling.OppdatertFormue,
        ).right()
    }

    /**
     * @throws IllegalArgumentException - Dersom vi ikke kan oppdatere stønadsperioden.
     */
    fun leggTilAldersvurderingOgStønadsperiodeForAvslagPgaManglendeDokumentasjon(
        aldersvurdering: Aldersvurdering.SkalIkkeVurderes,
        formuegrenserFactory: FormuegrenserFactory,
        clock: Clock,
    ): VilkårsvurdertSøknadsbehandling {
        return grunnlagsdataOgVilkårsvurderinger.oppdaterStønadsperiode(
            stønadsperiode = aldersvurdering.stønadsperiode,
            formuegrenserFactory = formuegrenserFactory,
            clock = clock,
        ).getOrElse {
            throw IllegalArgumentException("Feil ved oppdatering av stønadsperiode for grunnlagsdata og vilkårsvurderinger. id $id")
        }.let {
            VilkårsvurdertSøknadsbehandling.opprett(
                forrigeTilstand = this,
                saksbehandler = saksbehandler,
                grunnlagsdataOgVilkårsvurderinger = it,
                tidspunkt = Tidspunkt.now(clock),
                aldersvurdering = aldersvurdering,
                handling = null,
            )
        }
    }

    fun oppdaterStønadsperiode(
        saksbehandler: NavIdentBruker.Saksbehandler,
        aldersvurdering: Aldersvurdering.Vurdert,
        formuegrenserFactory: FormuegrenserFactory,
        clock: Clock,
    ): Either<KunneIkkeOppdatereStønadsperiode, VilkårsvurdertSøknadsbehandling> {
        return grunnlagsdataOgVilkårsvurderinger.oppdaterStønadsperiode(
            // TODO jah: Ta inn stønadsperiode som parameter. Valider at den er sammenhengende med den nye aldersvurderingen.
            //  Her er det også mulig å oppdatere aldersvurderingen i et eget steg.
            stønadsperiode = aldersvurdering.stønadsperiode,
            formuegrenserFactory = formuegrenserFactory,
            clock = clock,
        ).mapLeft {
            KunneIkkeOppdatereStønadsperiode.KunneIkkeOppdatereGrunnlagsdata(it)
        }.map {
            VilkårsvurdertSøknadsbehandling.opprett(
                forrigeTilstand = this,
                saksbehandler = saksbehandler,
                grunnlagsdataOgVilkårsvurderinger = it,
                tidspunkt = Tidspunkt.now(clock),
                handling = SøknadsbehandlingsHandling.OppdatertStønadsperiode,
                aldersvurdering = aldersvurdering,
            )
        }
    }

    fun avslåPgaOpplysningsplikt(
        saksbehandler: NavIdentBruker.Saksbehandler,
        tidspunkt: Tidspunkt,
    ): Either<KunneIkkeLageOpplysningspliktVilkår, VilkårsvurdertSøknadsbehandling.Avslag> {
        // TODO jah: Validering med left eller throw?
        val oppdatertGrunnlagsdataOgVilkårsvurderinger =
            this.grunnlagsdataOgVilkårsvurderinger.avslåPgaOpplysningsplikt<GrunnlagsdataOgVilkårsvurderingerSøknadsbehandling>(
                tidspunkt = tidspunkt,
                periode = periode,
            ).getOrElse { return it.left() }

        return (
            VilkårsvurdertSøknadsbehandling.opprett(
                forrigeTilstand = this,
                saksbehandler = saksbehandler,
                grunnlagsdataOgVilkårsvurderinger = oppdatertGrunnlagsdataOgVilkårsvurderinger,
                tidspunkt = tidspunkt,
                handling = SøknadsbehandlingsHandling.OppdatertOpplysningsplikt,
            ) as VilkårsvurdertSøknadsbehandling.Avslag
            ).right() // TODO jah 2023-06-18: Kan vi gjøre noe med casten her?
    }

    /**
     * Denne funksjonen blir ikke kalt i praksis.
     * Merk at for søknadsbehandling er dette noe VilkårsvurdertSøknadsbehandling.opprettet(...) gjør for oss allerede.
     * Så dersom dette blir lagt til som et eget steg må vi fjerne det derfra og bare bruke denne.
     */
    fun leggTilOpplysningspliktVilkår(
        vilkår: OpplysningspliktVilkår.Vurdert,
        saksbehandler: NavIdentBruker.Saksbehandler,
    ): Either<KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilOpplysningsplikt, VilkårsvurdertSøknadsbehandling> {
        return vilkårsvurder(
            saksbehandler = saksbehandler,
            vilkår = vilkår,
            handling = SøknadsbehandlingsHandling.OppdatertOpplysningsplikt,
            tidspunkt = vilkår.vurderingsperioder.first().opprettet,
        ).mapLeft { KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilOpplysningsplikt.Vilkårsfeil(it) }
    }

    fun leggTilPensjonsVilkår(
        vilkår: PensjonsVilkår.Vurdert,
        saksbehandler: NavIdentBruker.Saksbehandler,
    ): Either<KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilPensjonsVilkår, VilkårsvurdertSøknadsbehandling> {
        if (Sakstype.ALDER != sakstype) {
            return KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilPensjonsVilkår.VilkårKunRelevantForAlder.left()
        }
        return vilkårsvurder(
            saksbehandler = saksbehandler,
            vilkår = vilkår,
            handling = SøknadsbehandlingsHandling.OppdatertPensjonsvilkår,
            tidspunkt = vilkår.vurderingsperioder.first().opprettet,
        ).mapLeft { KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilPensjonsVilkår.Vilkårsfeil(it) }
    }

    fun leggTilPersonligOppmøteVilkår(
        saksbehandler: NavIdentBruker.Saksbehandler,
        vilkår: PersonligOppmøteVilkår.Vurdert,
    ): Either<KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilPersonligOppmøteVilkår, VilkårsvurdertSøknadsbehandling> {
        return vilkårsvurder(
            saksbehandler = saksbehandler,
            vilkår = vilkår,
            handling = SøknadsbehandlingsHandling.OppdatertPersonligOppmøte,
            tidspunkt = vilkår.vurderingsperioder.first().opprettet,
        ).mapLeft { KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilPersonligOppmøteVilkår.Vilkårsfeil(it) }
    }

    fun leggTilLovligOpphold(
        saksbehandler: NavIdentBruker.Saksbehandler,
        lovligOppholdVilkår: LovligOppholdVilkår.Vurdert,
    ): Either<KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilLovligOpphold, VilkårsvurdertSøknadsbehandling> {
        return vilkårsvurder(
            saksbehandler = saksbehandler,
            vilkår = lovligOppholdVilkår,
            tidspunkt = lovligOppholdVilkår.vurderingsperioder.first().opprettet,
            handling = SøknadsbehandlingsHandling.OppdatertLovligOpphold,
        ).mapLeft { KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilLovligOpphold.Vilkårsfeil(it) }
    }

    fun leggTilInstitusjonsoppholdVilkår(
        saksbehandler: NavIdentBruker.Saksbehandler,
        vilkår: InstitusjonsoppholdVilkår.Vurdert,
    ): Either<KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilInstitusjonsoppholdVilkår, VilkårsvurdertSøknadsbehandling> {
        require(vilkår.vurderingsperioder.size == 1) {
            // TODO jah: Flytt denne litt mer sentralt for hele søknadsbehandling eller bytt til en mer felles left.
            "Vi støtter ikke flere enn 1 vurderingsperiode for søknadsbehandling"
        }
        return vilkårsvurder(
            saksbehandler = saksbehandler,
            vilkår = vilkår,
            tidspunkt = vilkår.vurderingsperioder.first().opprettet,
            handling = SøknadsbehandlingsHandling.OppdatertInstitusjonsopphold,
        ).mapLeft { KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilInstitusjonsoppholdVilkår.Vilkårsfeil(it) }
    }

    fun oppdaterBosituasjon(
        saksbehandler: NavIdentBruker.Saksbehandler,
        bosituasjon: Bosituasjon.Fullstendig,
    ): Either<GrunnlagetMåVæreInnenforBehandlingsperioden, VilkårsvurdertSøknadsbehandling> {
        if (this.periode != bosituasjon.periode) {
            return GrunnlagetMåVæreInnenforBehandlingsperioden.left()
        }
        return VilkårsvurdertSøknadsbehandling.opprett(
            forrigeTilstand = this,
            saksbehandler = saksbehandler,
            grunnlagsdataOgVilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger.oppdaterBosituasjon(listOf(bosituasjon)),
            tidspunkt = bosituasjon.opprettet,
            handling = SøknadsbehandlingsHandling.OppdatertBosituasjon,
        ).right()
    }

    fun leggTilUførevilkår(
        saksbehandler: NavIdentBruker.Saksbehandler,
        vilkår: UføreVilkår.Vurdert,
    ): Either<KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilUførevilkår, VilkårsvurdertSøknadsbehandling> {
        return vilkårsvurder(
            saksbehandler = saksbehandler,
            vilkår = vilkår,
            tidspunkt = vilkår.vurderingsperioder.first().opprettet,
            handling = SøknadsbehandlingsHandling.OppdatertUførhet,
        ).mapLeft { KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilUførevilkår.Vilkårsfeil(it) }
    }

    fun leggTilFamiliegjenforeningvilkår(
        saksbehandler: NavIdentBruker.Saksbehandler,
        vilkår: FamiliegjenforeningVilkår.Vurdert,
    ): Either<KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilFamiliegjenforeningVilkår, VilkårsvurdertSøknadsbehandling> {
        return vilkårsvurder(
            saksbehandler = saksbehandler,
            vilkår = vilkår,
            tidspunkt = vilkår.vurderingsperioder.first().opprettet,
            handling = SøknadsbehandlingsHandling.OppdatertFamiliegjenforening,
        ).mapLeft { KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilFamiliegjenforeningVilkår.Vilkårsfeil(it) }
    }

    fun leggTilFlyktningVilkår(
        saksbehandler: NavIdentBruker.Saksbehandler,
        vilkår: FlyktningVilkår.Vurdert,
        clock: Clock,
    ): Either<KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilFlyktningVilkår, VilkårsvurdertSøknadsbehandling> {
        return vilkårsvurder(
            saksbehandler = saksbehandler,
            vilkår = vilkår,
            tidspunkt = Tidspunkt.now(clock),
            handling = SøknadsbehandlingsHandling.OppdatertFlyktning,
        ).mapLeft { KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilFlyktningVilkår.Vilkårsfeil(it) }
    }

    fun leggTilFastOppholdINorgeVilkår(
        saksbehandler: NavIdentBruker.Saksbehandler,
        vilkår: FastOppholdINorgeVilkår.Vurdert,
    ): Either<KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilFastOppholdINorgeVilkår, VilkårsvurdertSøknadsbehandling> {
        return vilkårsvurder(
            saksbehandler = saksbehandler,
            vilkår = vilkår,
            tidspunkt = vilkår.vurderingsperioder.first().opprettet,
            handling = SøknadsbehandlingsHandling.OppdatertFastOppholdINorge,
        ).mapLeft { KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilFastOppholdINorgeVilkår.Vilkårsfeil(it) }
    }
}

object GrunnlagetMåVæreInnenforBehandlingsperioden
