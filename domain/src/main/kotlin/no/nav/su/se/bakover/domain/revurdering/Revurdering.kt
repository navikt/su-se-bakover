package no.nav.su.se.bakover.domain.revurdering

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.NavIdentBruker.Saksbehandler
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.avkorting.AvkortingVedRevurdering
import no.nav.su.se.bakover.domain.behandling.Behandling
import no.nav.su.se.bakover.domain.behandling.BehandlingMedAttestering
import no.nav.su.se.bakover.domain.behandling.BehandlingMedOppgave
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.beregning.BeregningStrategyFactory
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.domain.brev.Brevvalg
import no.nav.su.se.bakover.domain.brev.LagBrevRequest
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag.Bosituasjon.Companion.minsteAntallSammenhengendePerioder
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.grunnlag.GrunnlagsdataOgVilkårsvurderinger
import no.nav.su.se.bakover.domain.grunnlag.Konsistensproblem
import no.nav.su.se.bakover.domain.grunnlag.KunneIkkeLageGrunnlagsdata
import no.nav.su.se.bakover.domain.grunnlag.SjekkOmGrunnlagErKonsistent
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.person.Person
import no.nav.su.se.bakover.domain.revurdering.beregning.BeregnRevurderingStrategyDecider
import no.nav.su.se.bakover.domain.revurdering.oppdater.KunneIkkeOppdatereRevurdering
import no.nav.su.se.bakover.domain.revurdering.opphør.OpphørVedRevurdering
import no.nav.su.se.bakover.domain.revurdering.opphør.VurderOmBeregningGirOpphørVedRevurdering
import no.nav.su.se.bakover.domain.revurdering.opphør.VurderOmVilkårGirOpphørVedRevurdering
import no.nav.su.se.bakover.domain.revurdering.opphør.VurderOpphørVedRevurdering
import no.nav.su.se.bakover.domain.sak.SakInfo
import no.nav.su.se.bakover.domain.sak.Sakstype
import no.nav.su.se.bakover.domain.satser.SatsFactory
import no.nav.su.se.bakover.domain.søknadsbehandling.KunneIkkeLeggeTilVilkår
import no.nav.su.se.bakover.domain.vedtak.GjeldendeVedtaksdata
import no.nav.su.se.bakover.domain.vilkår.FastOppholdINorgeVilkår
import no.nav.su.se.bakover.domain.vilkår.FlyktningVilkår
import no.nav.su.se.bakover.domain.vilkår.FormueVilkår
import no.nav.su.se.bakover.domain.vilkår.InstitusjonsoppholdVilkår
import no.nav.su.se.bakover.domain.vilkår.LovligOppholdVilkår
import no.nav.su.se.bakover.domain.vilkår.OpplysningspliktVilkår
import no.nav.su.se.bakover.domain.vilkår.PensjonsVilkår
import no.nav.su.se.bakover.domain.vilkår.PersonligOppmøteVilkår
import no.nav.su.se.bakover.domain.vilkår.UføreVilkår
import no.nav.su.se.bakover.domain.vilkår.UtenlandsoppholdVilkår
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderingsresultat
import no.nav.su.se.bakover.domain.vilkår.inneholderAlle
import no.nav.su.se.bakover.domain.visitor.Visitable
import java.time.Clock
import java.util.UUID
import kotlin.reflect.KClass

sealed class AbstraktRevurdering : Behandling {
    val grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderinger.Revurdering
        get() = GrunnlagsdataOgVilkårsvurderinger.Revurdering(
            grunnlagsdata = grunnlagsdata,
            vilkårsvurderinger = vilkårsvurderinger,
        )

    abstract val tilRevurdering: UUID
    abstract val sakinfo: SakInfo
    override val sakId by lazy { sakinfo.sakId }
    override val saksnummer by lazy { sakinfo.saksnummer }
    override val fnr by lazy { sakinfo.fnr }
    override val sakstype: Sakstype by lazy { sakinfo.type }

    abstract override val vilkårsvurderinger: Vilkårsvurderinger.Revurdering
    fun erÅpen(): Boolean = when (this) {
        is GjenopptaYtelseRevurdering.SimulertGjenopptakAvYtelse,
        is BeregnetRevurdering,
        is OpprettetRevurdering,
        is RevurderingTilAttestering,
        is SimulertRevurdering,
        is UnderkjentRevurdering,
        is StansAvYtelseRevurdering.SimulertStansAvYtelse,
        -> true

        is AvsluttetRevurdering,
        is IverksattRevurdering.Innvilget,
        is IverksattRevurdering.Opphørt,
        is GjenopptaYtelseRevurdering.AvsluttetGjenoppta,
        is GjenopptaYtelseRevurdering.IverksattGjenopptakAvYtelse,
        is StansAvYtelseRevurdering.AvsluttetStansAvYtelse,
        is StansAvYtelseRevurdering.IverksattStansAvYtelse,
        -> false
    }

    abstract val brevvalgRevurdering: BrevvalgRevurdering
}

sealed class Revurdering :
    AbstraktRevurdering(),
    BehandlingMedOppgave,
    BehandlingMedAttestering,
    Visitable<RevurderingVisitor> {
    abstract val saksbehandler: Saksbehandler
    abstract val revurderingsårsak: Revurderingsårsak
    abstract val informasjonSomRevurderes: InformasjonSomRevurderes
    abstract val avkorting: AvkortingVedRevurdering
    abstract val erOpphørt: Boolean
    abstract val beregning: Beregning?
    abstract val simulering: Simulering?

    fun årsakErGRegulering(): Boolean {
        return revurderingsårsak.årsak == Revurderingsårsak.Årsak.REGULER_GRUNNBELØP
    }

    open fun lagForhåndsvarsel(
        person: Person,
        saksbehandlerNavn: String,
        fritekst: String,
        clock: Clock,
    ): Either<UgyldigTilstand, LagBrevRequest> {
        return UgyldigTilstand(this::class, this::class).left()
    }

    fun vilkårsvurderingsResultat(): Vilkårsvurderingsresultat {
        return vilkårsvurderinger.vurdering
    }

    data class UgyldigTilstand(val fra: KClass<out Revurdering>, val til: KClass<out Revurdering>)

    /**
     * @param brevvalg Saksbehandler velger selv om man skal sende brev eller ikke når man avslutter en revurdering som har blitt forhåndsvarslet. Merk at man ikke kan gjøre dette valget dersom det ikke har blitt forhåndsvarslet, siden vi da ikke skal sende brev.
     */
    fun avslutt(
        begrunnelse: String,
        brevvalg: Brevvalg.SaksbehandlersValg?,
        tidspunktAvsluttet: Tidspunkt,
    ): Either<KunneIkkeLageAvsluttetRevurdering, AvsluttetRevurdering> {
        return AvsluttetRevurdering.tryCreate(
            underliggendeRevurdering = this,
            begrunnelse = begrunnelse,
            brevvalg = brevvalg,
            tidspunktAvsluttet = tidspunktAvsluttet,
        )
    }

    sealed class KunneIkkeLeggeTilFradrag {
        data class Valideringsfeil(val feil: KunneIkkeLageGrunnlagsdata) : KunneIkkeLeggeTilFradrag()
        data class UgyldigTilstand(
            val fra: KClass<out Revurdering>,
            val til: KClass<out Revurdering> = OpprettetRevurdering::class,
        ) : KunneIkkeLeggeTilFradrag()
    }

    sealed class KunneIkkeLeggeTilBosituasjon {
        data class Valideringsfeil(val feil: KunneIkkeLageGrunnlagsdata) : KunneIkkeLeggeTilBosituasjon()
        data class UgyldigTilstand(val fra: KClass<out Revurdering>, val til: KClass<out Revurdering>) :
            KunneIkkeLeggeTilBosituasjon()

        data class Konsistenssjekk(val feil: Konsistensproblem.Bosituasjon) : KunneIkkeLeggeTilBosituasjon()
        data class KunneIkkeOppdatereFormue(val feil: KunneIkkeLeggeTilFormue) : KunneIkkeLeggeTilBosituasjon()
        object PerioderMangler : KunneIkkeLeggeTilBosituasjon()
    }

    open fun oppdater(
        periode: Periode,
        revurderingsårsak: Revurderingsårsak,
        grunnlagsdata: Grunnlagsdata,
        vilkårsvurderinger: Vilkårsvurderinger.Revurdering,
        informasjonSomRevurderes: InformasjonSomRevurderes,
        tilRevurdering: UUID,
        avkorting: AvkortingVedRevurdering.Uhåndtert,
        saksbehandler: Saksbehandler,
    ): Either<KunneIkkeOppdatereRevurdering, OpprettetRevurdering> {
        return KunneIkkeOppdatereRevurdering.UgyldigTilstand(
            fra = this::class,
            til = OpprettetRevurdering::class,
        ).left()
    }

    protected fun oppdaterInternal(
        periode: Periode,
        revurderingsårsak: Revurderingsårsak,
        grunnlagsdata: Grunnlagsdata,
        vilkårsvurderinger: Vilkårsvurderinger.Revurdering,
        informasjonSomRevurderes: InformasjonSomRevurderes,
        tilRevurdering: UUID,
        avkorting: AvkortingVedRevurdering.Uhåndtert,
        saksbehandler: Saksbehandler,
    ): Either<KunneIkkeOppdatereRevurdering, OpprettetRevurdering> {
        return OpprettetRevurdering(
            id = id,
            periode = periode,
            opprettet = opprettet,
            tilRevurdering = tilRevurdering,
            saksbehandler = saksbehandler,
            oppgaveId = oppgaveId,
            revurderingsårsak = revurderingsårsak,
            grunnlagsdata = grunnlagsdata,
            vilkårsvurderinger = vilkårsvurderinger,
            informasjonSomRevurderes = informasjonSomRevurderes,
            attesteringer = attesteringer,
            avkorting = avkorting,
            sakinfo = sakinfo,
            brevvalgRevurdering = brevvalgRevurdering,
        ).right()
    }

    open fun oppdaterUføreOgMarkerSomVurdert(uføre: UføreVilkår.Vurdert): Either<UgyldigTilstand, OpprettetRevurdering> =
        UgyldigTilstand(this::class, OpprettetRevurdering::class).left()

    open fun oppdaterUtenlandsoppholdOgMarkerSomVurdert(utenlandsopphold: UtenlandsoppholdVilkår.Vurdert): Either<KunneIkkeLeggeTilUtenlandsopphold, OpprettetRevurdering> =
        KunneIkkeLeggeTilUtenlandsopphold.UgyldigTilstand(this::class, OpprettetRevurdering::class).left()

    sealed interface KunneIkkeLeggeTilFormue {
        data class UgyldigTilstand(
            val fra: KClass<out Revurdering>,
            val til: KClass<out Revurdering> = OpprettetRevurdering::class,
        ) : KunneIkkeLeggeTilFormue

        data class Konsistenssjekk(val feil: Konsistensproblem.BosituasjonOgFormue) : KunneIkkeLeggeTilFormue
    }

    open fun oppdaterFormueOgMarkerSomVurdert(formue: FormueVilkår.Vurdert): Either<KunneIkkeLeggeTilFormue, OpprettetRevurdering> =
        KunneIkkeLeggeTilFormue.UgyldigTilstand(this::class, OpprettetRevurdering::class).left()

    open fun oppdaterFradragOgMarkerSomVurdert(fradragsgrunnlag: List<Grunnlag.Fradragsgrunnlag>): Either<KunneIkkeLeggeTilFradrag, OpprettetRevurdering> =
        KunneIkkeLeggeTilFradrag.UgyldigTilstand(this::class, OpprettetRevurdering::class).left()

    open fun oppdaterFradrag(fradragsgrunnlag: List<Grunnlag.Fradragsgrunnlag>): Either<KunneIkkeLeggeTilFradrag, OpprettetRevurdering> {
        return KunneIkkeLeggeTilFradrag.UgyldigTilstand(this::class, OpprettetRevurdering::class).left()
    }

    open fun oppdaterOpplysningsplikt(opplysningspliktVilkår: OpplysningspliktVilkår.Vurdert): Either<KunneIkkeLeggeTilOpplysningsplikt, OpprettetRevurdering> {
        return KunneIkkeLeggeTilOpplysningsplikt.UgyldigTilstand(this::class, OpprettetRevurdering::class).left()
    }

    open fun oppdaterOpplysningspliktOgMarkerSomVurdert(opplysningspliktVilkår: OpplysningspliktVilkår.Vurdert): Either<KunneIkkeLeggeTilOpplysningsplikt, OpprettetRevurdering> {
        return KunneIkkeLeggeTilOpplysningsplikt.UgyldigTilstand(this::class, OpprettetRevurdering::class).left()
    }

    open fun oppdaterPensjonsvilkårOgMarkerSomVurdert(vilkår: PensjonsVilkår.Vurdert): Either<KunneIkkeLeggeTilPensjonsVilkår, OpprettetRevurdering> {
        return KunneIkkeLeggeTilPensjonsVilkår.UgyldigTilstand(this::class, OpprettetRevurdering::class).left()
    }

    open fun oppdaterFlyktningvilkårOgMarkerSomVurdert(vilkår: FlyktningVilkår.Vurdert): Either<KunneIkkeLeggeTilFlyktningVilkår, OpprettetRevurdering> {
        return KunneIkkeLeggeTilFlyktningVilkår.UgyldigTilstand(this::class, OpprettetRevurdering::class).left()
    }

    open fun oppdaterPersonligOppmøtevilkårOgMarkerSomVurdert(vilkår: PersonligOppmøteVilkår.Vurdert): Either<KunneIkkeLeggeTilPersonligOppmøteVilkår, OpprettetRevurdering> {
        return KunneIkkeLeggeTilPersonligOppmøteVilkår.UgyldigTilstand(this::class, OpprettetRevurdering::class).left()
    }

    open fun oppdaterLovligOppholdOgMarkerSomVurdert(lovligOppholdVilkår: LovligOppholdVilkår.Vurdert): Either<KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilLovligOpphold, OpprettetRevurdering> {
        return KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilLovligOpphold.UgyldigTilstand.Revurdering(
            this::class,
            OpprettetRevurdering::class,
        ).left()
    }

    open fun oppdaterInstitusjonsoppholdOgMarkerSomVurdert(
        institusjonsoppholdVilkår: InstitusjonsoppholdVilkår.Vurdert,
    ): Either<KunneIkkeLeggeTilInstitusjonsoppholdVilkår, OpprettetRevurdering> {
        return KunneIkkeLeggeTilInstitusjonsoppholdVilkår.UgyldigTilstand(
            this::class,
            OpprettetRevurdering::class,
        ).left()
    }

    sealed interface KunneIkkeLeggeTilOpplysningsplikt {
        data class UgyldigTilstand(
            val fra: KClass<out Revurdering>,
            val til: KClass<out Revurdering> = OpprettetRevurdering::class,
        ) : KunneIkkeLeggeTilOpplysningsplikt

        object HeleBehandlingsperiodenErIkkeVurdert : KunneIkkeLeggeTilOpplysningsplikt
    }

    sealed interface KunneIkkeLeggeTilPensjonsVilkår {
        data class UgyldigTilstand(
            val fra: KClass<out Revurdering>,
            val til: KClass<out Revurdering> = OpprettetRevurdering::class,
        ) : KunneIkkeLeggeTilPensjonsVilkår

        object HeleBehandlingsperiodenErIkkeVurdert : KunneIkkeLeggeTilPensjonsVilkår
        object VilkårKunRelevantForAlder : KunneIkkeLeggeTilPensjonsVilkår
    }

    sealed interface KunneIkkeLeggeTilFlyktningVilkår {
        data class UgyldigTilstand(
            val fra: KClass<out Revurdering>,
            val til: KClass<out Revurdering> = OpprettetRevurdering::class,
        ) : KunneIkkeLeggeTilFlyktningVilkår

        object HeleBehandlingsperiodenErIkkeVurdert : KunneIkkeLeggeTilFlyktningVilkår
        object VilkårKunRelevantForUføre : KunneIkkeLeggeTilFlyktningVilkår
    }

    sealed interface KunneIkkeLeggeTilPersonligOppmøteVilkår {
        data class UgyldigTilstand(
            val fra: KClass<out Revurdering>,
            val til: KClass<out Revurdering> = OpprettetRevurdering::class,
        ) : KunneIkkeLeggeTilPersonligOppmøteVilkår

        object HeleBehandlingsperiodenErIkkeVurdert : KunneIkkeLeggeTilPersonligOppmøteVilkår
    }

    sealed interface KunneIkkeLeggeTilInstitusjonsoppholdVilkår {
        data class UgyldigTilstand(
            val fra: KClass<out Revurdering>,
            val til: KClass<out Revurdering> = OpprettetRevurdering::class,
        ) : KunneIkkeLeggeTilInstitusjonsoppholdVilkår

        object HeleBehandlingsperiodenErIkkeVurdert : KunneIkkeLeggeTilInstitusjonsoppholdVilkår
    }

    private fun oppdaterOpplysnigspliktInternal(opplysningspliktVilkår: OpplysningspliktVilkår.Vurdert): Either<KunneIkkeLeggeTilOpplysningsplikt, OpprettetRevurdering> {
        if (!periode.fullstendigOverlapp(opplysningspliktVilkår.minsteAntallSammenhengendePerioder())) {
            return KunneIkkeLeggeTilOpplysningsplikt.HeleBehandlingsperiodenErIkkeVurdert.left()
        }
        return oppdaterVilkårsvurderinger(vilkårsvurderinger = vilkårsvurderinger.leggTil(opplysningspliktVilkår)).right()
    }

    protected fun oppdaterOpplysnigspliktOgMarkerSomVurdertInternal(opplysningspliktVilkår: OpplysningspliktVilkår.Vurdert): Either<KunneIkkeLeggeTilOpplysningsplikt, OpprettetRevurdering> {
        return oppdaterOpplysnigspliktInternal(opplysningspliktVilkår).map {
            it.oppdaterInformasjonSomRevurderes(informasjonSomRevurderes.markerSomVurdert(Revurderingsteg.Opplysningsplikt))
        }
    }

    protected fun oppdaterPensjonsVilkårOgMarkerSomVurdertInternal(vilkår: PensjonsVilkår.Vurdert): Either<KunneIkkeLeggeTilPensjonsVilkår, OpprettetRevurdering> {
        return oppdaterPensjonsVilkårInternal(vilkår)
            .map { it.oppdaterInformasjonSomRevurderes(informasjonSomRevurderes.markerSomVurdert(Revurderingsteg.Pensjon)) }
    }

    private fun oppdaterPensjonsVilkårInternal(vilkår: PensjonsVilkår.Vurdert): Either<KunneIkkeLeggeTilPensjonsVilkår, OpprettetRevurdering> {
        if (!periode.fullstendigOverlapp(vilkår.minsteAntallSammenhengendePerioder())) {
            return KunneIkkeLeggeTilPensjonsVilkår.HeleBehandlingsperiodenErIkkeVurdert.left()
        }
        if (Sakstype.ALDER != sakstype) {
            return KunneIkkeLeggeTilPensjonsVilkår.VilkårKunRelevantForAlder.left()
        }
        return oppdaterVilkårsvurderinger(vilkårsvurderinger = vilkårsvurderinger.leggTil(vilkår)).right()
    }

    protected fun oppdaterFlyktningVilkårOgMarkerSomVurdertInternal(vilkår: FlyktningVilkår.Vurdert): Either<KunneIkkeLeggeTilFlyktningVilkår, OpprettetRevurdering> {
        return oppdaterFlyktningVilkårInternal(vilkår)
            .map { it.oppdaterInformasjonSomRevurderes(informasjonSomRevurderes.markerSomVurdert(Revurderingsteg.Flyktning)) }
    }

    private fun oppdaterFlyktningVilkårInternal(vilkår: FlyktningVilkår.Vurdert): Either<KunneIkkeLeggeTilFlyktningVilkår, OpprettetRevurdering> {
        if (!periode.fullstendigOverlapp(vilkår.perioder)) {
            return KunneIkkeLeggeTilFlyktningVilkår.HeleBehandlingsperiodenErIkkeVurdert.left()
        }
        if (Sakstype.UFØRE != sakstype) {
            return KunneIkkeLeggeTilFlyktningVilkår.VilkårKunRelevantForUføre.left()
        }
        return oppdaterVilkårsvurderinger(vilkårsvurderinger = vilkårsvurderinger.leggTil(vilkår)).right()
    }

    private fun oppdaterLovligOpphold(lovligOppholdVilkår: LovligOppholdVilkår.Vurdert): Either<KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilLovligOpphold, OpprettetRevurdering> {
        if (!periode.fullstendigOverlapp(lovligOppholdVilkår.minsteAntallSammenhengendePerioder())) {
            return KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilLovligOpphold.HeleBehandlingsperiodenErIkkeVurdert.left()
        }

        return oppdaterVilkårsvurderinger(vilkårsvurderinger = vilkårsvurderinger.leggTil(lovligOppholdVilkår)).right()
    }

    protected fun oppdaterLovligOppholdOgMarkerSomVurdertInternal(lovligOppholdVilkår: LovligOppholdVilkår.Vurdert) =
        oppdaterLovligOpphold(lovligOppholdVilkår).map {
            it.oppdaterInformasjonSomRevurderes(informasjonSomRevurderes.markerSomVurdert(Revurderingsteg.Oppholdstillatelse))
        }

    open fun oppdaterBosituasjonOgMarkerSomVurdert(bosituasjon: List<Grunnlag.Bosituasjon.Fullstendig>): Either<KunneIkkeLeggeTilBosituasjon, OpprettetRevurdering> =
        KunneIkkeLeggeTilBosituasjon.UgyldigTilstand(this::class, OpprettetRevurdering::class).left()

    protected fun oppdaterUføreOgMarkerSomVurdertInternal(
        uføre: UføreVilkår.Vurdert,
    ): Either<UgyldigTilstand, OpprettetRevurdering> {
        return oppdaterVilkårsvurderinger(
            vilkårsvurderinger = vilkårsvurderinger.leggTil(uføre),
        ).oppdaterInformasjonSomRevurderes(
            informasjonSomRevurderes = informasjonSomRevurderes.markerSomVurdert(Revurderingsteg.Uførhet),
        ).right()
    }

    protected fun oppdaterPersonligOppmøteVilkårOgMarkerSomVurdertInternal(vilkår: PersonligOppmøteVilkår.Vurdert): Either<KunneIkkeLeggeTilPersonligOppmøteVilkår, OpprettetRevurdering> {
        return oppdaterPersonligOppmøteVilkårInternal(vilkår)
            .map { it.oppdaterInformasjonSomRevurderes(informasjonSomRevurderes.markerSomVurdert(Revurderingsteg.PersonligOppmøte)) }
    }

    private fun oppdaterPersonligOppmøteVilkårInternal(vilkår: PersonligOppmøteVilkår.Vurdert): Either<KunneIkkeLeggeTilPersonligOppmøteVilkår, OpprettetRevurdering> {
        if (!periode.fullstendigOverlapp(vilkår.perioder)) {
            return KunneIkkeLeggeTilPersonligOppmøteVilkår.HeleBehandlingsperiodenErIkkeVurdert.left()
        }
        return oppdaterVilkårsvurderinger(vilkårsvurderinger = vilkårsvurderinger.leggTil(vilkår)).right()
    }

    sealed interface KunneIkkeLeggeTilUtenlandsopphold {
        data class UgyldigTilstand(val fra: KClass<out Revurdering>, val til: KClass<out Revurdering>) :
            KunneIkkeLeggeTilUtenlandsopphold

        object VurderingsperiodeUtenforBehandlingsperiode : KunneIkkeLeggeTilUtenlandsopphold
        object AlleVurderingsperioderMåHaSammeResultat : KunneIkkeLeggeTilUtenlandsopphold
        object MåVurdereHelePerioden : KunneIkkeLeggeTilUtenlandsopphold
    }

    protected fun oppdaterUtenlandsoppholdOgMarkerSomVurdertInternal(
        vilkår: UtenlandsoppholdVilkår.Vurdert,
    ): Either<KunneIkkeLeggeTilUtenlandsopphold, OpprettetRevurdering> {
        return valider(vilkår).map {
            oppdaterVilkårsvurderinger(
                vilkårsvurderinger = vilkårsvurderinger.leggTil(vilkår),
            ).oppdaterInformasjonSomRevurderes(
                informasjonSomRevurderes = informasjonSomRevurderes.markerSomVurdert(Revurderingsteg.Utenlandsopphold),
            )
        }
    }

    protected open fun valider(utenlandsopphold: UtenlandsoppholdVilkår.Vurdert): Either<KunneIkkeLeggeTilUtenlandsopphold, Unit> {
        return when {
            !periode.inneholderAlle(utenlandsopphold.vurderingsperioder) -> {
                KunneIkkeLeggeTilUtenlandsopphold.VurderingsperiodeUtenforBehandlingsperiode.left()
            }

            !utenlandsopphold.vurderingsperioder.all {
                it.vurdering == utenlandsopphold.vurderingsperioder.first().vurdering
            } -> {
                KunneIkkeLeggeTilUtenlandsopphold.AlleVurderingsperioderMåHaSammeResultat.left()
            }

            !periode.fullstendigOverlapp(utenlandsopphold.vurderingsperioder.map { it.periode }) -> {
                KunneIkkeLeggeTilUtenlandsopphold.MåVurdereHelePerioden.left()
            }

            else -> Unit.right()
        }
    }

    protected fun oppdaterFormueOgMarkerSomVurdertInternal(formue: FormueVilkår.Vurdert): Either<KunneIkkeLeggeTilFormue, OpprettetRevurdering> {
        return oppdaterFormueInternal(formue)
            .map { it.oppdaterInformasjonSomRevurderes(informasjonSomRevurderes.markerSomVurdert(Revurderingsteg.Formue)) }
    }

    protected fun oppdaterFormueInternal(formue: FormueVilkår): Either<KunneIkkeLeggeTilFormue, OpprettetRevurdering> {
        return SjekkOmGrunnlagErKonsistent.BosituasjonOgFormue(
            bosituasjon = grunnlagsdata.bosituasjon,
            formue = formue.grunnlag,
        ).resultat.mapLeft {
            KunneIkkeLeggeTilFormue.Konsistenssjekk(it.first())
        }.map {
            oppdaterVilkårsvurderinger(vilkårsvurderinger = vilkårsvurderinger.leggTil(formue))
        }
    }

    protected fun oppdaterFradragOgMarkerSomVurdertInternal(fradragsgrunnlag: List<Grunnlag.Fradragsgrunnlag>): Either<KunneIkkeLeggeTilFradrag, OpprettetRevurdering> {
        return oppdaterFradragInternal(fradragsgrunnlag).getOrHandle { return it.left() }
            .oppdaterInformasjonSomRevurderes(
                informasjonSomRevurderes = informasjonSomRevurderes.markerSomVurdert(
                    Revurderingsteg.Inntekt,
                ),
            ).right()
    }

    protected fun oppdaterFradragInternal(fradragsgrunnlag: List<Grunnlag.Fradragsgrunnlag>): Either<KunneIkkeLeggeTilFradrag, OpprettetRevurdering> {
        require(fradragsgrunnlag.all { periode inneholder it.periode })
        return Grunnlagsdata.tryCreate(
            bosituasjon = grunnlagsdata.bosituasjon,
            fradragsgrunnlag = fradragsgrunnlag,
        ).mapLeft {
            KunneIkkeLeggeTilFradrag.Valideringsfeil(it)
        }.map {
            oppdaterGrunnlag(it)
        }
    }

    protected fun oppdaterBosituasjonOgMarkerSomVurdertInternal(bosituasjon: List<Grunnlag.Bosituasjon.Fullstendig>): Either<KunneIkkeLeggeTilBosituasjon, OpprettetRevurdering> {
        return oppdaterBosituasjonInternal(bosituasjon)
            .map { it.oppdaterInformasjonSomRevurderes(informasjonSomRevurderes.markerSomVurdert(Revurderingsteg.Bosituasjon)) }
    }

    private fun oppdaterBosituasjonInternal(bosituasjon: List<Grunnlag.Bosituasjon.Fullstendig>): Either<KunneIkkeLeggeTilBosituasjon, OpprettetRevurdering> {
        if (!periode.fullstendigOverlapp(bosituasjon.minsteAntallSammenhengendePerioder())) {
            return KunneIkkeLeggeTilBosituasjon.PerioderMangler.left()
        }
        return SjekkOmGrunnlagErKonsistent.Bosituasjon(bosituasjon).resultat
            .mapLeft { KunneIkkeLeggeTilBosituasjon.Konsistenssjekk(it.first()) }
            .flatMap {
                grunnlagsdataOgVilkårsvurderinger.oppdaterBosituasjon(bosituasjon).let { grunnlagOgVilkår ->
                    oppdaterGrunnlag(grunnlagOgVilkår.grunnlagsdata)
                        .oppdaterFormueInternal(grunnlagOgVilkår.vilkårsvurderinger.formue)
                        .mapLeft {
                            KunneIkkeLeggeTilBosituasjon.KunneIkkeOppdatereFormue(it)
                        }
                }
            }
    }

    open fun oppdaterFastOppholdINorgeOgMarkerSomVurdert(vilkår: FastOppholdINorgeVilkår.Vurdert): Either<KunneIkkeLeggeTilFastOppholdINorgeVilkår, OpprettetRevurdering> {
        return KunneIkkeLeggeTilFastOppholdINorgeVilkår.UgyldigTilstand(this::class, OpprettetRevurdering::class).left()
    }

    sealed interface KunneIkkeLeggeTilFastOppholdINorgeVilkår {
        data class UgyldigTilstand(
            val fra: KClass<out Revurdering>,
            val til: KClass<out Revurdering> = OpprettetRevurdering::class,
        ) : KunneIkkeLeggeTilFastOppholdINorgeVilkår

        object HeleBehandlingsperiodenErIkkeVurdert : KunneIkkeLeggeTilFastOppholdINorgeVilkår
        object AlleVurderingsperioderMåHaSammeResultat : KunneIkkeLeggeTilFastOppholdINorgeVilkår
    }

    protected fun oppdaterFastOppholdINorgeOgMarkerSomVurdertInternal(vilkår: FastOppholdINorgeVilkår.Vurdert): Either<KunneIkkeLeggeTilFastOppholdINorgeVilkår, OpprettetRevurdering> {
        return oppdaterFastOppholdINorgeInternal(vilkår)
            .map { it.oppdaterInformasjonSomRevurderes(informasjonSomRevurderes.markerSomVurdert(Revurderingsteg.FastOppholdINorge)) }
    }

    private fun oppdaterFastOppholdINorgeInternal(vilkår: FastOppholdINorgeVilkår.Vurdert): Either<KunneIkkeLeggeTilFastOppholdINorgeVilkår, OpprettetRevurdering> {
        if (!periode.fullstendigOverlapp(vilkår.perioder)) {
            return KunneIkkeLeggeTilFastOppholdINorgeVilkår.HeleBehandlingsperiodenErIkkeVurdert.left()
        }
        if (!vilkår.vurderingsperioder.all {
            it.vurdering == vilkår.vurderingsperioder.first().vurdering
        }
        ) {
            return KunneIkkeLeggeTilFastOppholdINorgeVilkår.AlleVurderingsperioderMåHaSammeResultat.left()
        }
        return oppdaterVilkårsvurderinger(vilkårsvurderinger = vilkårsvurderinger.leggTil(vilkår)).right()
    }

    protected fun oppdaterInstitusjonsoppholdOgMarkerSomVurdertInternal(
        vilkår: InstitusjonsoppholdVilkår.Vurdert,
    ): Either<KunneIkkeLeggeTilInstitusjonsoppholdVilkår.HeleBehandlingsperiodenErIkkeVurdert, OpprettetRevurdering> {
        if (vilkår.perioder.size != 1 || vilkår.perioder.first() != periode) {
            // TODO jah: vilkår.perioder.size != 1 - Vi støtter foreløpig ikke hull i revurderingsperioden
            return KunneIkkeLeggeTilInstitusjonsoppholdVilkår.HeleBehandlingsperiodenErIkkeVurdert.left()
        }
        return oppdaterVilkårsvurderinger(
            vilkårsvurderinger = vilkårsvurderinger.leggTil(vilkår),
        ).oppdaterInformasjonSomRevurderes(
            informasjonSomRevurderes = informasjonSomRevurderes.markerSomVurdert(Revurderingsteg.Institusjonsopphold),
        ).right()
    }

    private fun oppdaterVilkårsvurderinger(
        vilkårsvurderinger: Vilkårsvurderinger.Revurdering,
    ): OpprettetRevurdering {
        return OpprettetRevurdering(
            id = id,
            periode = periode,
            opprettet = opprettet,
            tilRevurdering = tilRevurdering,
            saksbehandler = saksbehandler,
            oppgaveId = oppgaveId,
            revurderingsårsak = revurderingsårsak,
            grunnlagsdata = grunnlagsdata,
            vilkårsvurderinger = vilkårsvurderinger,
            informasjonSomRevurderes = informasjonSomRevurderes,
            attesteringer = attesteringer,
            avkorting = avkorting.let {
                when (it) {
                    is AvkortingVedRevurdering.DelvisHåndtert -> {
                        it.uhåndtert()
                    }

                    is AvkortingVedRevurdering.Håndtert -> {
                        it.uhåndtert()
                    }

                    is AvkortingVedRevurdering.Uhåndtert -> {
                        it
                    }

                    is AvkortingVedRevurdering.Iverksatt -> {
                        throw IllegalStateException("Kan ikke oppdatere vilkårsvurderinger for ferdigstilt håndtering av avkorting")
                    }
                }
            },
            sakinfo = sakinfo,
            brevvalgRevurdering = brevvalgRevurdering,
        )
    }

    private fun oppdaterGrunnlag(grunnlagsdata: Grunnlagsdata): OpprettetRevurdering {
        return OpprettetRevurdering(
            id = id,
            periode = periode,
            opprettet = opprettet,
            tilRevurdering = tilRevurdering,
            saksbehandler = saksbehandler,
            oppgaveId = oppgaveId,
            revurderingsårsak = revurderingsårsak,
            grunnlagsdata = grunnlagsdata,
            vilkårsvurderinger = vilkårsvurderinger,
            informasjonSomRevurderes = informasjonSomRevurderes,
            attesteringer = attesteringer,
            avkorting = avkorting.let {
                when (it) {
                    is AvkortingVedRevurdering.DelvisHåndtert -> {
                        it.uhåndtert()
                    }

                    is AvkortingVedRevurdering.Håndtert -> {
                        it.uhåndtert()
                    }

                    is AvkortingVedRevurdering.Uhåndtert -> {
                        it
                    }

                    is AvkortingVedRevurdering.Iverksatt -> {
                        throw IllegalStateException("Kan ikke oppdatere grunnlag for ferdigstilt håndtering av avkorting")
                    }
                }
            },
            sakinfo = sakinfo,
            brevvalgRevurdering = brevvalgRevurdering,
        )
    }

    open fun beregn(
        eksisterendeUtbetalinger: List<Utbetaling>,
        clock: Clock,
        gjeldendeVedtaksdata: GjeldendeVedtaksdata,
        satsFactory: SatsFactory,
    ): Either<KunneIkkeBeregneRevurdering, BeregnetRevurdering> {
        val (revurdering, beregning) = BeregnRevurderingStrategyDecider(
            revurdering = this,
            gjeldendeVedtaksdata = gjeldendeVedtaksdata,
            clock = clock,
            beregningStrategyFactory = BeregningStrategyFactory(
                clock = clock,
                satsFactory = satsFactory,
            ),
        ).decide().beregn().getOrHandle { return it.left() }

        fun opphør(revurdering: OpprettetRevurdering, revurdertBeregning: Beregning): BeregnetRevurdering.Opphørt =
            BeregnetRevurdering.Opphørt(
                tilRevurdering = revurdering.tilRevurdering,
                id = revurdering.id,
                periode = revurdering.periode,
                opprettet = revurdering.opprettet,
                beregning = revurdertBeregning,
                saksbehandler = revurdering.saksbehandler,
                oppgaveId = revurdering.oppgaveId,
                revurderingsårsak = revurdering.revurderingsårsak,
                grunnlagsdata = revurdering.grunnlagsdata,
                vilkårsvurderinger = revurdering.vilkårsvurderinger,
                informasjonSomRevurderes = revurdering.informasjonSomRevurderes,
                attesteringer = revurdering.attesteringer,
                avkorting = revurdering.avkorting.håndter(),
                sakinfo = sakinfo,
                brevvalgRevurdering = brevvalgRevurdering,
            )

        fun innvilget(revurdering: OpprettetRevurdering, revurdertBeregning: Beregning): BeregnetRevurdering.Innvilget =
            BeregnetRevurdering.Innvilget(
                id = revurdering.id,
                periode = revurdering.periode,
                opprettet = revurdering.opprettet,
                tilRevurdering = revurdering.tilRevurdering,
                saksbehandler = revurdering.saksbehandler,
                beregning = revurdertBeregning,
                oppgaveId = revurdering.oppgaveId,
                revurderingsårsak = revurdering.revurderingsårsak,
                grunnlagsdata = revurdering.grunnlagsdata,
                vilkårsvurderinger = revurdering.vilkårsvurderinger,
                informasjonSomRevurderes = revurdering.informasjonSomRevurderes,
                attesteringer = revurdering.attesteringer,
                avkorting = revurdering.avkorting.håndter(),
                sakinfo = sakinfo,
                brevvalgRevurdering = brevvalgRevurdering,
            )

        fun kontrollerOpphørAvFremtidigAvkorting(): Either<KunneIkkeBeregneRevurdering.OpphørAvYtelseSomSkalAvkortes, Unit> {
            val erOpphør = VurderOpphørVedRevurdering.VilkårsvurderingerOgBeregning(
                vilkårsvurderinger = vilkårsvurderinger,
                beregning = beregning,
                clock = clock,
            ).resultat

            return when (erOpphør) {
                is OpphørVedRevurdering.Ja -> {
                    /**
                     * Kontroller er at vi ikke opphører noe som inneholder planlagte avkortinger, da dette vil føre til at
                     * beløpene aldri vil avkortes. //TODO må sannsynligvis støtte dette på et eller annet tidspunkt
                     */
                    if (beregning.getFradrag().any { it.fradragstype == Fradragstype.AvkortingUtenlandsopphold }) {
                        KunneIkkeBeregneRevurdering.OpphørAvYtelseSomSkalAvkortes.left()
                    } else {
                        Unit.right()
                    }
                }

                is OpphørVedRevurdering.Nei -> {
                    Unit.right()
                }
            }
        }

        // TODO jm: sjekk av vilkår og verifisering av dette bør sannsynligvis legges til et tidspunkt før selve beregningen finner sted. Snarvei inntil videre, da vi mangeler "infrastruktur" for dette pt.  Bør være en tydeligere del av modellen for revurdering.
        if (VurderOmVilkårGirOpphørVedRevurdering(revurdering.vilkårsvurderinger).resultat is OpphørVedRevurdering.Ja) {
            kontrollerOpphørAvFremtidigAvkorting().getOrHandle {
                return it.left()
            }
            return opphør(revurdering, beregning).right()
        }

        return when (
            VurderOmBeregningGirOpphørVedRevurdering(
                beregning = beregning,
                clock = clock,
            ).resultat
        ) {
            is OpphørVedRevurdering.Ja -> {
                kontrollerOpphørAvFremtidigAvkorting().getOrHandle {
                    return it.left()
                }
                opphør(revurdering, beregning)
            }

            is OpphørVedRevurdering.Nei -> {
                innvilget(revurdering, beregning)
            }
        }.right()
    }

    sealed class KunneIkkeBeregneRevurdering {
        object KanIkkeVelgeSisteMånedVedNedgangIStønaden : KunneIkkeBeregneRevurdering()

        data class UgyldigBeregningsgrunnlag(
            val reason: no.nav.su.se.bakover.domain.beregning.UgyldigBeregningsgrunnlag,
        ) : KunneIkkeBeregneRevurdering()

        object KanIkkeHaFradragSomTilhørerEpsHvisBrukerIkkeHarEps : KunneIkkeBeregneRevurdering()
        object AvkortingErUfullstendig : KunneIkkeBeregneRevurdering()
        object OpphørAvYtelseSomSkalAvkortes : KunneIkkeBeregneRevurdering()
    }

    abstract fun skalSendeBrev(): Boolean

    fun leggTilBrevvalg(brevvalgRevurdering: BrevvalgRevurdering): Either<KunneIkkeLeggeTilBrevvalg, Revurdering> {
        return when (brevvalgRevurdering) {
            BrevvalgRevurdering.IkkeValgt -> KunneIkkeLeggeTilBrevvalg.UgyldigBrevvalg.left()
            is BrevvalgRevurdering.Valgt.IkkeSendBrev -> this.leggTilBrevvalgInternal(brevvalgRevurdering)
            is BrevvalgRevurdering.Valgt.SendBrev -> this.leggTilBrevvalgInternal(brevvalgRevurdering)
        }
    }

    protected open fun Revurdering.leggTilBrevvalgInternal(brevvalgRevurdering: BrevvalgRevurdering.Valgt): Either<KunneIkkeLeggeTilBrevvalg, Revurdering> {
        return KunneIkkeLeggeTilBrevvalg.UgyldigTilstand(this::class).left()
    }

    sealed interface KunneIkkeLeggeTilBrevvalg {
        object UgyldigBrevvalg : KunneIkkeLeggeTilBrevvalg
        data class UgyldigTilstand(
            val tilstand: KClass<out Revurdering>,
        ) : KunneIkkeLeggeTilBrevvalg
    }
}

enum class Vurderingstatus(val status: String) {
    Vurdert("Vurdert"),
    IkkeVurdert("IkkeVurdert"),
}

enum class Revurderingsteg(val vilkår: String) {
    // BorOgOppholderSegINorge("BorOgOppholderSegINorge"),
    Flyktning("Flyktning"),
    Formue("Formue"),

    Oppholdstillatelse("Oppholdstillatelse"),

    PersonligOppmøte("PersonligOppmøte"),
    Uførhet("Uførhet"),
    Bosituasjon("Bosituasjon"),

    Institusjonsopphold("Institusjonsopphold"),
    Utenlandsopphold("Utenlandsopphold"),
    Inntekt("Inntekt"),
    Opplysningsplikt("Opplysningsplikt"),
    Pensjon("Pensjon"),
    FastOppholdINorge("FastOppholdINorge"),
    ;
}
