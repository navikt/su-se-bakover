package no.nav.su.se.bakover.domain.revurdering

import arrow.core.Either
import arrow.core.NonEmptyList
import arrow.core.flatMap
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.NavIdentBruker
import no.nav.su.se.bakover.common.NavIdentBruker.Saksbehandler
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.sikkerLogg
import no.nav.su.se.bakover.common.toNonEmptyList
import no.nav.su.se.bakover.domain.avkorting.AvkortingVedRevurdering
import no.nav.su.se.bakover.domain.avkorting.Avkortingsvarsel
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.behandling.Attesteringshistorikk
import no.nav.su.se.bakover.domain.behandling.Behandling
import no.nav.su.se.bakover.domain.behandling.BehandlingMedAttestering
import no.nav.su.se.bakover.domain.behandling.BehandlingMedOppgave
import no.nav.su.se.bakover.domain.behandling.avslag.Opphørsgrunn
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.beregning.BeregningStrategyFactory
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.domain.brev.Brevvalg
import no.nav.su.se.bakover.domain.brev.LagBrevRequest
import no.nav.su.se.bakover.domain.brev.beregning.Tilbakekreving
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag.Bosituasjon.Companion.minsteAntallSammenhengendePerioder
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.grunnlag.GrunnlagsdataOgVilkårsvurderinger
import no.nav.su.se.bakover.domain.grunnlag.Konsistensproblem
import no.nav.su.se.bakover.domain.grunnlag.KunneIkkeLageGrunnlagsdata
import no.nav.su.se.bakover.domain.grunnlag.SjekkOmGrunnlagErKonsistent
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingFeilet
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.IkkeAvgjort
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.IkkeBehovForTilbakekrevingUnderBehandling
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.IkkeTilbakekrev
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.Tilbakekrev
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.Tilbakekrevingsbehandling
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.tilbakekrevingErVurdert
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.person.Person
import no.nav.su.se.bakover.domain.revurdering.beregning.BeregnRevurderingStrategyDecider
import no.nav.su.se.bakover.domain.sak.SakInfo
import no.nav.su.se.bakover.domain.sak.Sakstype
import no.nav.su.se.bakover.domain.sak.SimulerUtbetalingFeilet
import no.nav.su.se.bakover.domain.satser.SatsFactory
import no.nav.su.se.bakover.domain.søknadsbehandling.KunneIkkeLeggeTilVilkår
import no.nav.su.se.bakover.domain.vedtak.GjeldendeVedtaksdata
import no.nav.su.se.bakover.domain.vilkår.FastOppholdINorgeVilkår
import no.nav.su.se.bakover.domain.vilkår.FlyktningVilkår
import no.nav.su.se.bakover.domain.vilkår.FormueVilkår
import no.nav.su.se.bakover.domain.vilkår.Inngangsvilkår
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
import java.time.LocalDate
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
        is IverksattRevurdering.IngenEndring,
        is IverksattRevurdering.Innvilget,
        is IverksattRevurdering.Opphørt,
        is GjenopptaYtelseRevurdering.AvsluttetGjenoppta,
        is GjenopptaYtelseRevurdering.IverksattGjenopptakAvYtelse,
        is StansAvYtelseRevurdering.AvsluttetStansAvYtelse,
        is StansAvYtelseRevurdering.IverksattStansAvYtelse,
        -> false
    }
}

sealed class Revurdering :
    AbstraktRevurdering(),
    BehandlingMedOppgave,
    BehandlingMedAttestering,
    Visitable<RevurderingVisitor> {
    abstract val saksbehandler: Saksbehandler

    // TODO ia: fritekst bør flyttes ut av denne klassen og til et eget konsept (som også omfatter fritekst på søknadsbehandlinger)
    abstract val fritekstTilBrev: String
    abstract val revurderingsårsak: Revurderingsårsak

    abstract val informasjonSomRevurderes: InformasjonSomRevurderes

    abstract val avkorting: AvkortingVedRevurdering
    abstract val erOpphørt: Boolean
    abstract val beregning: Beregning?
    abstract val simulering: Simulering?

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
            fritekstTilBrev = fritekstTilBrev,
            revurderingsårsak = revurderingsårsak,
            grunnlagsdata = grunnlagsdata,
            vilkårsvurderinger = vilkårsvurderinger,
            informasjonSomRevurderes = informasjonSomRevurderes,
            attesteringer = attesteringer,
            avkorting = avkorting,
            sakinfo = sakinfo,
        ).right()
    }

    sealed class KunneIkkeOppdatereRevurdering {
        data class UgyldigTilstand(
            val fra: KClass<out Revurdering>,
            val til: KClass<out Revurdering>,
        ) : KunneIkkeOppdatereRevurdering()

        object KanIkkeEndreÅrsakTilReguleringVedForhåndsvarsletRevurdering : KunneIkkeOppdatereRevurdering()
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
            fritekstTilBrev = fritekstTilBrev,
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
            fritekstTilBrev = fritekstTilBrev,
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
                fritekstTilBrev = revurdering.fritekstTilBrev,
                revurderingsårsak = revurdering.revurderingsårsak,
                grunnlagsdata = revurdering.grunnlagsdata,
                vilkårsvurderinger = revurdering.vilkårsvurderinger,
                informasjonSomRevurderes = revurdering.informasjonSomRevurderes,
                attesteringer = revurdering.attesteringer,
                avkorting = revurdering.avkorting.håndter(),
                sakinfo = sakinfo,
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
                fritekstTilBrev = revurdering.fritekstTilBrev,
                revurderingsårsak = revurdering.revurderingsårsak,
                grunnlagsdata = revurdering.grunnlagsdata,
                vilkårsvurderinger = revurdering.vilkårsvurderinger,
                informasjonSomRevurderes = revurdering.informasjonSomRevurderes,
                attesteringer = revurdering.attesteringer,
                avkorting = revurdering.avkorting.håndter(),
                sakinfo = sakinfo,
            )

        fun ingenEndring(
            revurdering: OpprettetRevurdering,
            revurdertBeregning: Beregning,
        ): BeregnetRevurdering.IngenEndring =
            BeregnetRevurdering.IngenEndring(
                id = revurdering.id,
                periode = revurdering.periode,
                opprettet = revurdering.opprettet,
                tilRevurdering = revurdering.tilRevurdering,
                saksbehandler = revurdering.saksbehandler,
                beregning = revurdertBeregning,
                oppgaveId = revurdering.oppgaveId,
                fritekstTilBrev = revurdering.fritekstTilBrev,
                revurderingsårsak = revurdering.revurderingsårsak,
                grunnlagsdata = revurdering.grunnlagsdata,
                vilkårsvurderinger = revurdering.vilkårsvurderinger,
                informasjonSomRevurderes = revurdering.informasjonSomRevurderes,
                attesteringer = revurdering.attesteringer,
                avkorting = revurdering.avkorting.håndter(),
                sakinfo = sakinfo,
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

        return when (revurdering.revurderingsårsak.årsak) {
            Revurderingsårsak.Årsak.REGULER_GRUNNBELØP -> {
                when (
                    VurderOmBeløpErForskjelligFraGjeldendeUtbetaling(
                        eksisterendeUtbetalinger = eksisterendeUtbetalinger.flatMap { it.utbetalingslinjer },
                        nyBeregning = beregning,
                    ).resultat
                ) {
                    true -> {
                        when (
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
                        }
                    }

                    false -> ingenEndring(revurdering, beregning)
                }
            }

            else -> {
                when (
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
                }
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
}

data class OpprettetRevurdering(
    override val id: UUID = UUID.randomUUID(),
    override val periode: Periode,
    override val opprettet: Tidspunkt,
    override val tilRevurdering: UUID,
    override val saksbehandler: Saksbehandler,
    override val oppgaveId: OppgaveId,
    override val fritekstTilBrev: String,
    override val revurderingsårsak: Revurderingsårsak,
    override val grunnlagsdata: Grunnlagsdata,
    override val vilkårsvurderinger: Vilkårsvurderinger.Revurdering,
    override val informasjonSomRevurderes: InformasjonSomRevurderes,
    override val attesteringer: Attesteringshistorikk = Attesteringshistorikk.empty(),
    override val avkorting: AvkortingVedRevurdering.Uhåndtert,
    override val sakinfo: SakInfo,
) : Revurdering() {
    override val erOpphørt = false
    override val beregning: Beregning? = null
    override val simulering: Simulering? = null

    override fun accept(visitor: RevurderingVisitor) {
        visitor.visit(this)
    }

    override fun oppdaterUføreOgMarkerSomVurdert(
        uføre: UføreVilkår.Vurdert,
    ) = oppdaterUføreOgMarkerSomVurdertInternal(uføre)

    override fun oppdaterUtenlandsoppholdOgMarkerSomVurdert(
        utenlandsopphold: UtenlandsoppholdVilkår.Vurdert,
    ) = oppdaterUtenlandsoppholdOgMarkerSomVurdertInternal(utenlandsopphold)

    override fun oppdaterFormueOgMarkerSomVurdert(formue: FormueVilkår.Vurdert): Either<KunneIkkeLeggeTilFormue, OpprettetRevurdering> =
        oppdaterFormueOgMarkerSomVurdertInternal(formue)

    override fun oppdaterFradragOgMarkerSomVurdert(fradragsgrunnlag: List<Grunnlag.Fradragsgrunnlag>) =
        oppdaterFradragOgMarkerSomVurdertInternal(fradragsgrunnlag)

    override fun oppdaterPensjonsvilkårOgMarkerSomVurdert(vilkår: PensjonsVilkår.Vurdert): Either<KunneIkkeLeggeTilPensjonsVilkår, OpprettetRevurdering> {
        return oppdaterPensjonsVilkårOgMarkerSomVurdertInternal(vilkår)
    }

    override fun oppdaterFlyktningvilkårOgMarkerSomVurdert(vilkår: FlyktningVilkår.Vurdert): Either<KunneIkkeLeggeTilFlyktningVilkår, OpprettetRevurdering> {
        return oppdaterFlyktningVilkårOgMarkerSomVurdertInternal(vilkår)
    }

    override fun oppdaterPersonligOppmøtevilkårOgMarkerSomVurdert(vilkår: PersonligOppmøteVilkår.Vurdert): Either<KunneIkkeLeggeTilPersonligOppmøteVilkår, OpprettetRevurdering> {
        return oppdaterPersonligOppmøteVilkårOgMarkerSomVurdertInternal(vilkår)
    }

    override fun oppdaterBosituasjonOgMarkerSomVurdert(bosituasjon: List<Grunnlag.Bosituasjon.Fullstendig>) =
        oppdaterBosituasjonOgMarkerSomVurdertInternal(bosituasjon)

    override fun oppdaterOpplysningspliktOgMarkerSomVurdert(opplysningspliktVilkår: OpplysningspliktVilkår.Vurdert): Either<KunneIkkeLeggeTilOpplysningsplikt, OpprettetRevurdering> {
        return oppdaterOpplysnigspliktOgMarkerSomVurdertInternal(opplysningspliktVilkår)
    }

    override fun oppdaterLovligOppholdOgMarkerSomVurdert(lovligOppholdVilkår: LovligOppholdVilkår.Vurdert): Either<KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilLovligOpphold, OpprettetRevurdering> {
        return oppdaterLovligOppholdOgMarkerSomVurdertInternal(lovligOppholdVilkår)
    }

    override fun oppdaterInstitusjonsoppholdOgMarkerSomVurdert(institusjonsoppholdVilkår: InstitusjonsoppholdVilkår.Vurdert): Either<KunneIkkeLeggeTilInstitusjonsoppholdVilkår, OpprettetRevurdering> {
        return oppdaterInstitusjonsoppholdOgMarkerSomVurdertInternal(institusjonsoppholdVilkår)
    }

    fun oppdaterInformasjonSomRevurderes(informasjonSomRevurderes: InformasjonSomRevurderes): OpprettetRevurdering {
        return copy(informasjonSomRevurderes = informasjonSomRevurderes)
    }

    override fun oppdaterFradrag(fradragsgrunnlag: List<Grunnlag.Fradragsgrunnlag>): Either<KunneIkkeLeggeTilFradrag, OpprettetRevurdering> {
        return oppdaterFradragInternal(fradragsgrunnlag)
    }

    override fun oppdaterFastOppholdINorgeOgMarkerSomVurdert(vilkår: FastOppholdINorgeVilkår.Vurdert): Either<KunneIkkeLeggeTilFastOppholdINorgeVilkår, OpprettetRevurdering> {
        return oppdaterFastOppholdINorgeOgMarkerSomVurdertInternal(vilkår)
    }

    override fun oppdater(
        periode: Periode,
        revurderingsårsak: Revurderingsårsak,
        grunnlagsdata: Grunnlagsdata,
        vilkårsvurderinger: Vilkårsvurderinger.Revurdering,
        informasjonSomRevurderes: InformasjonSomRevurderes,
        tilRevurdering: UUID,
        avkorting: AvkortingVedRevurdering.Uhåndtert,
        saksbehandler: Saksbehandler,
    ): Either<KunneIkkeOppdatereRevurdering, OpprettetRevurdering> {
        return oppdaterInternal(
            periode = periode,
            revurderingsårsak = revurderingsårsak,
            grunnlagsdata = grunnlagsdata,
            vilkårsvurderinger = vilkårsvurderinger,
            informasjonSomRevurderes = informasjonSomRevurderes,
            tilRevurdering = tilRevurdering,
            avkorting = avkorting,
            saksbehandler = saksbehandler,
        )
    }
}

sealed class BeregnetRevurdering : Revurdering() {
    abstract override val beregning: Beregning
    abstract override val avkorting: AvkortingVedRevurdering.DelvisHåndtert

    override fun oppdaterUføreOgMarkerSomVurdert(
        uføre: UføreVilkår.Vurdert,
    ) = oppdaterUføreOgMarkerSomVurdertInternal(uføre)

    override fun oppdaterUtenlandsoppholdOgMarkerSomVurdert(
        utenlandsopphold: UtenlandsoppholdVilkår.Vurdert,
    ) = oppdaterUtenlandsoppholdOgMarkerSomVurdertInternal(utenlandsopphold)

    override fun oppdaterFormueOgMarkerSomVurdert(formue: FormueVilkår.Vurdert): Either<KunneIkkeLeggeTilFormue, OpprettetRevurdering> =
        oppdaterFormueOgMarkerSomVurdertInternal(formue)

    override fun oppdaterFradragOgMarkerSomVurdert(fradragsgrunnlag: List<Grunnlag.Fradragsgrunnlag>) =
        oppdaterFradragOgMarkerSomVurdertInternal(fradragsgrunnlag)

    override fun oppdaterPensjonsvilkårOgMarkerSomVurdert(vilkår: PensjonsVilkår.Vurdert): Either<KunneIkkeLeggeTilPensjonsVilkår, OpprettetRevurdering> {
        return oppdaterPensjonsVilkårOgMarkerSomVurdertInternal(vilkår)
    }

    override fun oppdaterFlyktningvilkårOgMarkerSomVurdert(vilkår: FlyktningVilkår.Vurdert): Either<KunneIkkeLeggeTilFlyktningVilkår, OpprettetRevurdering> {
        return oppdaterFlyktningVilkårOgMarkerSomVurdertInternal(vilkår)
    }

    override fun oppdaterPersonligOppmøtevilkårOgMarkerSomVurdert(vilkår: PersonligOppmøteVilkår.Vurdert): Either<KunneIkkeLeggeTilPersonligOppmøteVilkår, OpprettetRevurdering> {
        return oppdaterPersonligOppmøteVilkårOgMarkerSomVurdertInternal(vilkår)
    }

    override fun oppdaterFradrag(fradragsgrunnlag: List<Grunnlag.Fradragsgrunnlag>): Either<KunneIkkeLeggeTilFradrag, OpprettetRevurdering> {
        return oppdaterFradragInternal(fradragsgrunnlag)
    }

    override fun oppdaterBosituasjonOgMarkerSomVurdert(bosituasjon: List<Grunnlag.Bosituasjon.Fullstendig>) =
        oppdaterBosituasjonOgMarkerSomVurdertInternal(bosituasjon)

    override fun oppdaterOpplysningspliktOgMarkerSomVurdert(opplysningspliktVilkår: OpplysningspliktVilkår.Vurdert): Either<KunneIkkeLeggeTilOpplysningsplikt, OpprettetRevurdering> {
        return oppdaterOpplysnigspliktOgMarkerSomVurdertInternal(opplysningspliktVilkår)
    }

    override fun oppdaterLovligOppholdOgMarkerSomVurdert(lovligOppholdVilkår: LovligOppholdVilkår.Vurdert): Either<KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilLovligOpphold, OpprettetRevurdering> {
        return oppdaterLovligOppholdOgMarkerSomVurdertInternal(lovligOppholdVilkår)
    }

    override fun oppdaterFastOppholdINorgeOgMarkerSomVurdert(vilkår: FastOppholdINorgeVilkår.Vurdert): Either<KunneIkkeLeggeTilFastOppholdINorgeVilkår, OpprettetRevurdering> {
        return oppdaterFastOppholdINorgeOgMarkerSomVurdertInternal(vilkår)
    }

    override fun oppdaterInstitusjonsoppholdOgMarkerSomVurdert(institusjonsoppholdVilkår: InstitusjonsoppholdVilkår.Vurdert): Either<KunneIkkeLeggeTilInstitusjonsoppholdVilkår, OpprettetRevurdering> {
        return oppdaterInstitusjonsoppholdOgMarkerSomVurdertInternal(institusjonsoppholdVilkår)
    }

    override fun oppdater(
        periode: Periode,
        revurderingsårsak: Revurderingsårsak,
        grunnlagsdata: Grunnlagsdata,
        vilkårsvurderinger: Vilkårsvurderinger.Revurdering,
        informasjonSomRevurderes: InformasjonSomRevurderes,
        tilRevurdering: UUID,
        avkorting: AvkortingVedRevurdering.Uhåndtert,
        saksbehandler: Saksbehandler,
    ): Either<KunneIkkeOppdatereRevurdering, OpprettetRevurdering> {
        return oppdaterInternal(
            periode = periode,
            revurderingsårsak = revurderingsårsak,
            grunnlagsdata = grunnlagsdata,
            vilkårsvurderinger = vilkårsvurderinger,
            informasjonSomRevurderes = informasjonSomRevurderes,
            tilRevurdering = tilRevurdering,
            avkorting = avkorting,
            saksbehandler = saksbehandler,
        )
    }

    data class Innvilget(
        override val id: UUID,
        override val periode: Periode,
        override val opprettet: Tidspunkt,
        override val tilRevurdering: UUID,
        override val saksbehandler: Saksbehandler,
        override val beregning: Beregning,
        override val oppgaveId: OppgaveId,
        override val fritekstTilBrev: String,
        override val revurderingsårsak: Revurderingsårsak,
        override val grunnlagsdata: Grunnlagsdata,
        override val vilkårsvurderinger: Vilkårsvurderinger.Revurdering,
        override val informasjonSomRevurderes: InformasjonSomRevurderes,
        override val attesteringer: Attesteringshistorikk,
        override val avkorting: AvkortingVedRevurdering.DelvisHåndtert,
        override val sakinfo: SakInfo,
    ) : BeregnetRevurdering() {
        override val erOpphørt = false
        override val simulering: Simulering? = null

        override fun accept(visitor: RevurderingVisitor) {
            visitor.visit(this)
        }

        fun simuler(
            saksbehandler: Saksbehandler,
            clock: Clock,
            simuler: (beregning: Beregning, uføregrunnlag: NonEmptyList<Grunnlag.Uføregrunnlag>?) -> Either<SimulerUtbetalingFeilet, Simulering>,
        ): Either<SimulerUtbetalingFeilet, SimulertRevurdering.Innvilget> {
            return simuler(
                beregning,
                when (sakstype) {
                    Sakstype.ALDER -> {
                        null
                    }
                    Sakstype.UFØRE -> {
                        vilkårsvurderinger.uføreVilkår()
                            .getOrHandle { throw IllegalStateException("Revurdering uføre: $id mangler uføregrunnlag") }
                            .grunnlag
                            .toNonEmptyList()
                    }
                },
            ).mapLeft {
                it
            }.map {
                val tilbakekrevingsbehandling = when (it.harFeilutbetalinger()) {
                    true -> {
                        IkkeAvgjort(
                            id = UUID.randomUUID(),
                            opprettet = Tidspunkt.now(clock),
                            sakId = sakId,
                            revurderingId = id,
                            periode = periode,
                        )
                    }

                    false -> {
                        IkkeBehovForTilbakekrevingUnderBehandling
                    }
                }

                SimulertRevurdering.Innvilget(
                    id = id,
                    periode = periode,
                    opprettet = opprettet,
                    tilRevurdering = tilRevurdering,
                    beregning = beregning,
                    simulering = it,
                    saksbehandler = saksbehandler,
                    oppgaveId = oppgaveId,
                    fritekstTilBrev = fritekstTilBrev,
                    revurderingsårsak = revurderingsårsak,
                    grunnlagsdata = grunnlagsdata,
                    vilkårsvurderinger = vilkårsvurderinger,
                    informasjonSomRevurderes = informasjonSomRevurderes,
                    attesteringer = attesteringer,
                    avkorting = avkorting.håndter(),
                    tilbakekrevingsbehandling = tilbakekrevingsbehandling,
                    sakinfo = sakinfo,
                )
            }
        }
    }

    data class IngenEndring(
        override val id: UUID,
        override val periode: Periode,
        override val opprettet: Tidspunkt,
        override val tilRevurdering: UUID,
        override val saksbehandler: Saksbehandler,
        override val beregning: Beregning,
        override val oppgaveId: OppgaveId,
        override val fritekstTilBrev: String,
        override val revurderingsårsak: Revurderingsårsak,
        override val grunnlagsdata: Grunnlagsdata,
        override val vilkårsvurderinger: Vilkårsvurderinger.Revurdering,
        override val informasjonSomRevurderes: InformasjonSomRevurderes,
        override val attesteringer: Attesteringshistorikk,
        override val avkorting: AvkortingVedRevurdering.DelvisHåndtert,
        override val sakinfo: SakInfo,
    ) : BeregnetRevurdering() {
        override val erOpphørt = false
        override val simulering: Simulering? = null

        fun tilAttestering(
            attesteringsoppgaveId: OppgaveId,
            saksbehandler: Saksbehandler,
            fritekstTilBrev: String,
            skalFøreTilUtsendingAvVedtaksbrev: Boolean,
        ): RevurderingTilAttestering.IngenEndring {
            return RevurderingTilAttestering.IngenEndring(
                id = id,
                periode = periode,
                opprettet = opprettet,
                tilRevurdering = tilRevurdering,
                saksbehandler = saksbehandler,
                beregning = beregning,
                oppgaveId = attesteringsoppgaveId,
                fritekstTilBrev = fritekstTilBrev,
                revurderingsårsak = revurderingsårsak,
                skalFøreTilUtsendingAvVedtaksbrev = if (revurderingsårsak.årsak == Revurderingsårsak.Årsak.REGULER_GRUNNBELØP) false else skalFøreTilUtsendingAvVedtaksbrev,
                grunnlagsdata = grunnlagsdata,
                vilkårsvurderinger = vilkårsvurderinger,
                informasjonSomRevurderes = informasjonSomRevurderes,
                attesteringer = attesteringer,
                avkorting = avkorting.håndter(),
                sakinfo = sakinfo,
            )
        }

        override fun accept(visitor: RevurderingVisitor) {
            visitor.visit(this)
        }
    }

    data class Opphørt(
        override val id: UUID,
        override val periode: Periode,
        override val opprettet: Tidspunkt,
        override val tilRevurdering: UUID,
        override val saksbehandler: Saksbehandler,
        override val beregning: Beregning,
        override val oppgaveId: OppgaveId,
        override val fritekstTilBrev: String,
        override val revurderingsårsak: Revurderingsårsak,
        override val grunnlagsdata: Grunnlagsdata,
        override val vilkårsvurderinger: Vilkårsvurderinger.Revurdering,
        override val informasjonSomRevurderes: InformasjonSomRevurderes,
        override val attesteringer: Attesteringshistorikk,
        override val avkorting: AvkortingVedRevurdering.DelvisHåndtert,
        override val sakinfo: SakInfo,
    ) : BeregnetRevurdering() {
        override val erOpphørt = true
        override val simulering: Simulering? = null

        fun simuler(
            saksbehandler: Saksbehandler,
            clock: Clock,
            simuler: (opphørsperiode: Periode, saksbehandler: Saksbehandler) -> Either<SimulerUtbetalingFeilet, Utbetaling.SimulertUtbetaling>,
        ): Either<SimulerUtbetalingFeilet, SimulertRevurdering.Opphørt> {
            val (simulertUtbetaling, håndtertAvkorting) = simuler(periode, saksbehandler)
                .getOrHandle { return it.left() }
                .let { simulering ->
                    when (val avkortingsvarsel = lagAvkortingsvarsel(simulering, clock)) {
                        is Avkortingsvarsel.Ingen -> {
                            simulering to when (avkorting) {
                                is AvkortingVedRevurdering.DelvisHåndtert.AnnullerUtestående -> {
                                    avkorting.håndter()
                                }

                                is AvkortingVedRevurdering.DelvisHåndtert.IngenUtestående -> {
                                    avkorting.håndter()
                                }

                                is AvkortingVedRevurdering.DelvisHåndtert.KanIkkeHåndtere -> {
                                    throw IllegalStateException("Skal ikke kunne skje")
                                }
                            }
                        }

                        is Avkortingsvarsel.Utenlandsopphold -> {
                            val nyOpphørsperiode = OpphørsperiodeForUtbetalinger(
                                revurdering = this,
                                avkortingsvarsel = avkortingsvarsel,
                            ).value
                            val simuleringMedNyOpphørsdato = simuler(nyOpphørsperiode, saksbehandler)
                                .getOrHandle { return it.left() }

                            if (simuleringMedNyOpphørsdato.simulering.harFeilutbetalinger()) {
                                sikkerLogg.error(
                                    "Simulering: ${objectMapper.writeValueAsString(simuleringMedNyOpphørsdato.simulering)}",
                                )
                                throw IllegalStateException("Simulering med justert opphørsdato for utbetalinger pga avkorting utenlandsopphold inneholder feilutbetaling, se sikkerlogg for detaljer")
                            }

                            simuleringMedNyOpphørsdato to when (avkorting) {
                                is AvkortingVedRevurdering.DelvisHåndtert.AnnullerUtestående -> {
                                    avkorting.håndter(avkortingsvarsel as Avkortingsvarsel.Utenlandsopphold.SkalAvkortes)
                                }

                                is AvkortingVedRevurdering.DelvisHåndtert.IngenUtestående -> {
                                    avkorting.håndter(avkortingsvarsel as Avkortingsvarsel.Utenlandsopphold.SkalAvkortes)
                                }

                                is AvkortingVedRevurdering.DelvisHåndtert.KanIkkeHåndtere -> {
                                    throw IllegalStateException("Skal ikke kunne skje")
                                }
                            }
                        }
                    }
                }

            val tilbakekrevingsbehandling = when (simulertUtbetaling.simulering.harFeilutbetalinger()) {
                true -> {
                    IkkeAvgjort(
                        id = UUID.randomUUID(),
                        opprettet = Tidspunkt.now(clock),
                        sakId = sakId,
                        revurderingId = id,
                        periode = periode,
                    )
                }

                false -> {
                    IkkeBehovForTilbakekrevingUnderBehandling
                }
            }

            unngåNyAvkortingOgNyTilbakekrevingPåSammeTid(
                avkorting = håndtertAvkorting,
                tilbakekrevingsbehandling = tilbakekrevingsbehandling,
            )

            return SimulertRevurdering.Opphørt(
                id = id,
                periode = periode,
                opprettet = opprettet,
                tilRevurdering = tilRevurdering,
                beregning = beregning,
                simulering = simulertUtbetaling.simulering,
                saksbehandler = saksbehandler,
                oppgaveId = oppgaveId,
                fritekstTilBrev = fritekstTilBrev,
                revurderingsårsak = revurderingsårsak,
                grunnlagsdata = grunnlagsdata,
                vilkårsvurderinger = vilkårsvurderinger,
                informasjonSomRevurderes = informasjonSomRevurderes,
                attesteringer = attesteringer,
                avkorting = håndtertAvkorting,
                tilbakekrevingsbehandling = tilbakekrevingsbehandling,
                sakinfo = sakinfo,
            ).right()
        }

        private fun unngåNyAvkortingOgNyTilbakekrevingPåSammeTid(
            avkorting: AvkortingVedRevurdering.Håndtert,
            tilbakekrevingsbehandling: Tilbakekrevingsbehandling.UnderBehandling,
        ) {
            val førerTilAvkorting = when (avkorting) {
                is AvkortingVedRevurdering.Håndtert.AnnullerUtestående,
                AvkortingVedRevurdering.Håndtert.IngenNyEllerUtestående,
                is AvkortingVedRevurdering.Håndtert.KanIkkeHåndteres,
                -> {
                    false
                }

                is AvkortingVedRevurdering.Håndtert.OpprettNyttAvkortingsvarsel,
                is AvkortingVedRevurdering.Håndtert.OpprettNyttAvkortingsvarselOgAnnullerUtestående,
                -> {
                    true
                }
            }
            val måBehandleTilbakekreving = when (tilbakekrevingsbehandling) {
                is Tilbakekrevingsbehandling.UnderBehandling.IkkeBehovForTilbakekreving -> {
                    false
                }

                is IkkeTilbakekrev,
                is Tilbakekrev,
                is IkkeAvgjort,
                -> {
                    true
                }
            }
            if (førerTilAvkorting && måBehandleTilbakekreving) throw IllegalStateException("Kan ikke håndtere avkorting og tilbakekreving på samme tid.")
        }

        private fun lagAvkortingsvarsel(
            simulertUtbetaling: Utbetaling.SimulertUtbetaling,
            clock: Clock,
        ): Avkortingsvarsel {
            return when (simulertUtbetaling.simulering.harFeilutbetalinger()) {
                true -> {
                    when (val vilkårsvurdering = vilkårsvurderinger.vurdering) {
                        is Vilkårsvurderingsresultat.Avslag -> {
                            when (vilkårsvurdering.erNøyaktigÅrsak(Inngangsvilkår.Utenlandsopphold)) {
                                true -> {
                                    Avkortingsvarsel.Utenlandsopphold.Opprettet(
                                        sakId = this.sakId,
                                        revurderingId = this.id,
                                        simulering = simulertUtbetaling.simulering,
                                        opprettet = Tidspunkt.now(clock),
                                    ).skalAvkortes()
                                }

                                false -> {
                                    Avkortingsvarsel.Ingen
                                }
                            }
                        }

                        is Vilkårsvurderingsresultat.Innvilget -> {
                            Avkortingsvarsel.Ingen
                        }

                        is Vilkårsvurderingsresultat.Uavklart -> {
                            throw IllegalStateException("Kan ikke vurdere avkorting før vilkår er avklart.")
                        }
                    }
                }

                else -> {
                    Avkortingsvarsel.Ingen
                }
            }
        }

        fun utledOpphørsgrunner(clock: Clock): List<Opphørsgrunn> {
            return when (
                val opphør = VurderOpphørVedRevurdering.VilkårsvurderingerOgBeregning(
                    vilkårsvurderinger = vilkårsvurderinger,
                    beregning = beregning,
                    clock = clock,
                ).resultat
            ) {
                is OpphørVedRevurdering.Ja -> opphør.opphørsgrunner
                OpphørVedRevurdering.Nei -> emptyList()
            }
        }

        override fun accept(visitor: RevurderingVisitor) {
            visitor.visit(this)
        }

        fun opphørSkyldesVilkår(): Boolean {
            return VurderOpphørVedRevurdering.Vilkårsvurderinger(vilkårsvurderinger).resultat is OpphørVedRevurdering.Ja
        }
    }
}

sealed class SimulertRevurdering : Revurdering() {

    abstract override val beregning: Beregning
    abstract override val simulering: Simulering
    abstract override val grunnlagsdata: Grunnlagsdata
    abstract val tilbakekrevingsbehandling: Tilbakekrevingsbehandling.UnderBehandling

    abstract override fun accept(visitor: RevurderingVisitor)

    fun harSimuleringFeilutbetaling() = simulering.harFeilutbetalinger()

    fun lagForhåndsvarsel(
        person: Person,
        saksbehandlerNavn: String,
        fritekst: String,
        clock: Clock,
    ): LagBrevRequest {
        return tilbakekrevingsbehandling.skalTilbakekreve().fold(
            {
                LagBrevRequest.Forhåndsvarsel(
                    person = person,
                    saksbehandlerNavn = saksbehandlerNavn,
                    fritekst = fritekst,
                    dagensDato = LocalDate.now(clock),
                    saksnummer = saksnummer,
                )
            },
            {
                LagBrevRequest.ForhåndsvarselTilbakekreving(
                    person = person,
                    saksbehandlerNavn = saksbehandlerNavn,
                    fritekst = fritekst,
                    dagensDato = LocalDate.now(clock),
                    saksnummer = saksnummer,
                    bruttoTilbakekreving = simulering.hentFeilutbetalteBeløp().sum(),
                    tilbakekreving = Tilbakekreving(simulering.hentFeilutbetalteBeløp().månedbeløp),
                )
            },
        )
    }

    override fun oppdaterUføreOgMarkerSomVurdert(
        uføre: UføreVilkår.Vurdert,
    ) = oppdaterUføreOgMarkerSomVurdertInternal(uføre)

    override fun oppdaterUtenlandsoppholdOgMarkerSomVurdert(
        utenlandsopphold: UtenlandsoppholdVilkår.Vurdert,
    ) = oppdaterUtenlandsoppholdOgMarkerSomVurdertInternal(utenlandsopphold)

    override fun oppdaterFormueOgMarkerSomVurdert(formue: FormueVilkår.Vurdert): Either<KunneIkkeLeggeTilFormue, OpprettetRevurdering> =
        oppdaterFormueOgMarkerSomVurdertInternal(formue)

    override fun oppdaterFradragOgMarkerSomVurdert(fradragsgrunnlag: List<Grunnlag.Fradragsgrunnlag>) =
        oppdaterFradragOgMarkerSomVurdertInternal(fradragsgrunnlag)

    override fun oppdaterPensjonsvilkårOgMarkerSomVurdert(vilkår: PensjonsVilkår.Vurdert): Either<KunneIkkeLeggeTilPensjonsVilkår, OpprettetRevurdering> {
        return oppdaterPensjonsVilkårOgMarkerSomVurdertInternal(vilkår)
    }

    override fun oppdaterFlyktningvilkårOgMarkerSomVurdert(vilkår: FlyktningVilkår.Vurdert): Either<KunneIkkeLeggeTilFlyktningVilkår, OpprettetRevurdering> {
        return oppdaterFlyktningVilkårOgMarkerSomVurdertInternal(vilkår)
    }

    override fun oppdaterPersonligOppmøtevilkårOgMarkerSomVurdert(vilkår: PersonligOppmøteVilkår.Vurdert): Either<KunneIkkeLeggeTilPersonligOppmøteVilkår, OpprettetRevurdering> {
        return oppdaterPersonligOppmøteVilkårOgMarkerSomVurdertInternal(vilkår)
    }

    override fun oppdaterBosituasjonOgMarkerSomVurdert(bosituasjon: List<Grunnlag.Bosituasjon.Fullstendig>) =
        oppdaterBosituasjonOgMarkerSomVurdertInternal(bosituasjon)

    override fun oppdaterOpplysningspliktOgMarkerSomVurdert(opplysningspliktVilkår: OpplysningspliktVilkår.Vurdert): Either<KunneIkkeLeggeTilOpplysningsplikt, OpprettetRevurdering> {
        return oppdaterOpplysnigspliktOgMarkerSomVurdertInternal(opplysningspliktVilkår)
    }

    override fun oppdaterLovligOppholdOgMarkerSomVurdert(lovligOppholdVilkår: LovligOppholdVilkår.Vurdert): Either<KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilLovligOpphold, OpprettetRevurdering> {
        return oppdaterLovligOppholdOgMarkerSomVurdertInternal(lovligOppholdVilkår)
    }

    override fun oppdaterInstitusjonsoppholdOgMarkerSomVurdert(institusjonsoppholdVilkår: InstitusjonsoppholdVilkår.Vurdert): Either<KunneIkkeLeggeTilInstitusjonsoppholdVilkår, OpprettetRevurdering> {
        return oppdaterInstitusjonsoppholdOgMarkerSomVurdertInternal(institusjonsoppholdVilkår)
    }

    override fun oppdaterFradrag(fradragsgrunnlag: List<Grunnlag.Fradragsgrunnlag>): Either<KunneIkkeLeggeTilFradrag, OpprettetRevurdering> {
        return oppdaterFradragInternal(fradragsgrunnlag)
    }

    override fun oppdaterFastOppholdINorgeOgMarkerSomVurdert(vilkår: FastOppholdINorgeVilkår.Vurdert): Either<KunneIkkeLeggeTilFastOppholdINorgeVilkår, OpprettetRevurdering> {
        return oppdaterFastOppholdINorgeOgMarkerSomVurdertInternal(vilkår)
    }

    abstract fun oppdaterTilbakekrevingsbehandling(tilbakekrevingsbehandling: Tilbakekrevingsbehandling.UnderBehandling): SimulertRevurdering

    sealed interface KunneIkkeSendeInnvilgetRevurderingTilAttestering {
        object ForhåndsvarslingErIkkeFerdigbehandlet : KunneIkkeSendeInnvilgetRevurderingTilAttestering
        object TilbakekrevingsbehandlingErIkkeFullstendig : KunneIkkeSendeInnvilgetRevurderingTilAttestering
    }

    override fun oppdater(
        periode: Periode,
        revurderingsårsak: Revurderingsårsak,
        grunnlagsdata: Grunnlagsdata,
        vilkårsvurderinger: Vilkårsvurderinger.Revurdering,
        informasjonSomRevurderes: InformasjonSomRevurderes,
        tilRevurdering: UUID,
        avkorting: AvkortingVedRevurdering.Uhåndtert,
        saksbehandler: Saksbehandler,
    ): Either<KunneIkkeOppdatereRevurdering, OpprettetRevurdering> {
        return oppdaterInternal(
            periode = periode,
            revurderingsårsak = revurderingsårsak,
            grunnlagsdata = grunnlagsdata,
            vilkårsvurderinger = vilkårsvurderinger,
            informasjonSomRevurderes = informasjonSomRevurderes,
            tilRevurdering = tilRevurdering,
            avkorting = avkorting,
            saksbehandler = saksbehandler,
        )
    }

    data class Innvilget(
        override val id: UUID,
        override val periode: Periode,
        override val opprettet: Tidspunkt,
        override val tilRevurdering: UUID,
        override val saksbehandler: Saksbehandler,
        override val oppgaveId: OppgaveId,
        override val revurderingsårsak: Revurderingsårsak,
        override val fritekstTilBrev: String,
        override val beregning: Beregning,
        override val simulering: Simulering,
        override val grunnlagsdata: Grunnlagsdata,
        override val vilkårsvurderinger: Vilkårsvurderinger.Revurdering,
        override val informasjonSomRevurderes: InformasjonSomRevurderes,
        override val attesteringer: Attesteringshistorikk,
        override val avkorting: AvkortingVedRevurdering.Håndtert,
        override val tilbakekrevingsbehandling: Tilbakekrevingsbehandling.UnderBehandling,
        override val sakinfo: SakInfo,
    ) : SimulertRevurdering() {
        override val erOpphørt = false

        override fun accept(visitor: RevurderingVisitor) {
            visitor.visit(this)
        }

        override fun oppdaterTilbakekrevingsbehandling(tilbakekrevingsbehandling: Tilbakekrevingsbehandling.UnderBehandling): Innvilget {
            return copy(tilbakekrevingsbehandling = tilbakekrevingsbehandling)
        }

        fun tilAttestering(
            attesteringsoppgaveId: OppgaveId,
            saksbehandler: Saksbehandler,
            fritekstTilBrev: String,
        ): Either<KunneIkkeSendeInnvilgetRevurderingTilAttestering, RevurderingTilAttestering.Innvilget> {
            val gyldigTilbakekrevingsbehandling = when (tilbakekrevingsbehandling) {
                is Tilbakekrev,
                is IkkeTilbakekrev,
                is Tilbakekrevingsbehandling.UnderBehandling.IkkeBehovForTilbakekreving,
                -> {
                    tilbakekrevingsbehandling
                }

                is IkkeAvgjort -> {
                    return KunneIkkeSendeInnvilgetRevurderingTilAttestering.TilbakekrevingsbehandlingErIkkeFullstendig.left()
                }
            }

            return RevurderingTilAttestering.Innvilget(
                id = id,
                periode = periode,
                opprettet = opprettet,
                tilRevurdering = tilRevurdering,
                saksbehandler = saksbehandler,
                beregning = beregning,
                simulering = simulering,
                oppgaveId = attesteringsoppgaveId,
                fritekstTilBrev = fritekstTilBrev,
                revurderingsårsak = revurderingsårsak,
                grunnlagsdata = grunnlagsdata,
                vilkårsvurderinger = vilkårsvurderinger,
                informasjonSomRevurderes = informasjonSomRevurderes,
                attesteringer = attesteringer,
                avkorting = avkorting,
                tilbakekrevingsbehandling = gyldigTilbakekrevingsbehandling,
                sakinfo = sakinfo,
            ).right()
        }
    }

    data class Opphørt(
        override val id: UUID,
        override val periode: Periode,
        override val opprettet: Tidspunkt,
        override val tilRevurdering: UUID,
        override val saksbehandler: Saksbehandler,
        override val oppgaveId: OppgaveId,
        override val revurderingsårsak: Revurderingsårsak,
        override val fritekstTilBrev: String,
        override val beregning: Beregning,
        override val simulering: Simulering,
        override val grunnlagsdata: Grunnlagsdata,
        override val vilkårsvurderinger: Vilkårsvurderinger.Revurdering,
        override val informasjonSomRevurderes: InformasjonSomRevurderes,
        override val attesteringer: Attesteringshistorikk,
        override val avkorting: AvkortingVedRevurdering.Håndtert,
        override val tilbakekrevingsbehandling: Tilbakekrevingsbehandling.UnderBehandling,
        override val sakinfo: SakInfo,
    ) : SimulertRevurdering() {
        override val erOpphørt = true

        override fun accept(visitor: RevurderingVisitor) {
            visitor.visit(this)
        }

        fun utledOpphørsgrunner(clock: Clock): List<Opphørsgrunn> {
            return when (
                val opphør = VurderOpphørVedRevurdering.VilkårsvurderingerOgBeregning(
                    vilkårsvurderinger = vilkårsvurderinger,
                    beregning = beregning,
                    clock = clock,
                ).resultat
            ) {
                is OpphørVedRevurdering.Ja -> opphør.opphørsgrunner
                OpphørVedRevurdering.Nei -> emptyList()
            }
        }

        override fun oppdaterTilbakekrevingsbehandling(tilbakekrevingsbehandling: Tilbakekrevingsbehandling.UnderBehandling): Opphørt {
            return copy(tilbakekrevingsbehandling = tilbakekrevingsbehandling)
        }

        sealed interface KanIkkeSendeOpphørtRevurderingTilAttestering {
            object KanIkkeSendeEnOpphørtGReguleringTilAttestering : KanIkkeSendeOpphørtRevurderingTilAttestering
            object ForhåndsvarslingErIkkeFerdigbehandlet : KanIkkeSendeOpphørtRevurderingTilAttestering
            object TilbakekrevingsbehandlingErIkkeFullstendig : KanIkkeSendeOpphørtRevurderingTilAttestering
        }

        fun tilAttestering(
            attesteringsoppgaveId: OppgaveId,
            saksbehandler: Saksbehandler,
            fritekstTilBrev: String,
        ): Either<KanIkkeSendeOpphørtRevurderingTilAttestering, RevurderingTilAttestering.Opphørt> {
            if (revurderingsårsak.årsak == Revurderingsårsak.Årsak.REGULER_GRUNNBELØP) {
                return KanIkkeSendeOpphørtRevurderingTilAttestering.KanIkkeSendeEnOpphørtGReguleringTilAttestering.left()
            }

            val gyldigTilbakekrevingsbehandling = when (tilbakekrevingsbehandling) {
                is Tilbakekrev,
                is IkkeTilbakekrev,
                is Tilbakekrevingsbehandling.UnderBehandling.IkkeBehovForTilbakekreving,
                -> {
                    tilbakekrevingsbehandling
                }

                is IkkeAvgjort -> {
                    return KanIkkeSendeOpphørtRevurderingTilAttestering.TilbakekrevingsbehandlingErIkkeFullstendig.left()
                }
            }

            return RevurderingTilAttestering.Opphørt(
                id = id,
                periode = periode,
                opprettet = opprettet,
                tilRevurdering = tilRevurdering,
                saksbehandler = saksbehandler,
                beregning = beregning,
                simulering = simulering,
                oppgaveId = attesteringsoppgaveId,
                fritekstTilBrev = fritekstTilBrev,
                revurderingsårsak = revurderingsårsak,
                grunnlagsdata = grunnlagsdata,
                vilkårsvurderinger = vilkårsvurderinger,
                informasjonSomRevurderes = informasjonSomRevurderes,
                attesteringer = attesteringer,
                avkorting = avkorting,
                tilbakekrevingsbehandling = gyldigTilbakekrevingsbehandling,
                sakinfo = sakinfo,
            ).right()
        }
    }
}

sealed class RevurderingTilAttestering : Revurdering() {
    abstract override val beregning: Beregning
    abstract override val grunnlagsdata: Grunnlagsdata

    abstract override fun accept(visitor: RevurderingVisitor)

    // TODO jah: Denne settes default til true i DB, men bør styres av domenet istedet. Se også TODO i RevurderingPostgresRepo.kt
    abstract val skalFøreTilUtsendingAvVedtaksbrev: Boolean
    abstract override val avkorting: AvkortingVedRevurdering.Håndtert

    abstract fun tilIverksatt(
        attestant: NavIdentBruker.Attestant,
        hentOpprinneligAvkorting: (id: UUID) -> Avkortingsvarsel?,
        clock: Clock,
    ): Either<KunneIkkeIverksetteRevurdering, IverksattRevurdering>

    data class Innvilget(
        override val id: UUID,
        override val periode: Periode,
        override val opprettet: Tidspunkt,
        override val tilRevurdering: UUID,
        override val saksbehandler: Saksbehandler,
        override val oppgaveId: OppgaveId,
        override val fritekstTilBrev: String,
        override val revurderingsårsak: Revurderingsårsak,
        override val beregning: Beregning,
        override val simulering: Simulering,
        override val grunnlagsdata: Grunnlagsdata,
        override val vilkårsvurderinger: Vilkårsvurderinger.Revurdering,
        override val informasjonSomRevurderes: InformasjonSomRevurderes,
        override val attesteringer: Attesteringshistorikk,
        override val avkorting: AvkortingVedRevurdering.Håndtert,
        val tilbakekrevingsbehandling: Tilbakekrevingsbehandling.UnderBehandling,
        override val sakinfo: SakInfo,
    ) : RevurderingTilAttestering() {

        override val erOpphørt = false

        override fun accept(visitor: RevurderingVisitor) {
            visitor.visit(this)
        }

        override val skalFøreTilUtsendingAvVedtaksbrev = true

        override fun tilIverksatt(
            attestant: NavIdentBruker.Attestant,
            hentOpprinneligAvkorting: (id: UUID) -> Avkortingsvarsel?,
            clock: Clock,
        ): Either<KunneIkkeIverksetteRevurdering, IverksattRevurdering.Innvilget> = validerTilIverksettOvergang(
            attestant = attestant,
            hentOpprinneligAvkorting = hentOpprinneligAvkorting,
            saksbehandler = saksbehandler,
            avkorting = avkorting,
        ).map {
            IverksattRevurdering.Innvilget(
                id = id,
                periode = periode,
                opprettet = opprettet,
                tilRevurdering = tilRevurdering,
                saksbehandler = saksbehandler,
                beregning = beregning,
                simulering = simulering,
                oppgaveId = oppgaveId,
                fritekstTilBrev = fritekstTilBrev,
                revurderingsårsak = revurderingsårsak,
                grunnlagsdata = grunnlagsdata,
                vilkårsvurderinger = vilkårsvurderinger,
                informasjonSomRevurderes = informasjonSomRevurderes,
                attesteringer = attesteringer.leggTilNyAttestering(
                    Attestering.Iverksatt(
                        attestant,
                        Tidspunkt.now(clock),
                    ),
                ),
                avkorting = avkorting.iverksett(id),
                tilbakekrevingsbehandling = tilbakekrevingsbehandling.fullførBehandling(),
                sakinfo = sakinfo,
            )
        }
    }

    data class Opphørt(
        override val id: UUID,
        override val periode: Periode,
        override val opprettet: Tidspunkt,
        override val tilRevurdering: UUID,
        override val saksbehandler: Saksbehandler,
        override val oppgaveId: OppgaveId,
        override val fritekstTilBrev: String,
        override val revurderingsårsak: Revurderingsårsak,
        override val beregning: Beregning,
        override val simulering: Simulering,
        override val grunnlagsdata: Grunnlagsdata,
        override val vilkårsvurderinger: Vilkårsvurderinger.Revurdering,
        override val informasjonSomRevurderes: InformasjonSomRevurderes,
        override val attesteringer: Attesteringshistorikk,
        override val avkorting: AvkortingVedRevurdering.Håndtert,
        val tilbakekrevingsbehandling: Tilbakekrevingsbehandling.UnderBehandling,
        override val sakinfo: SakInfo,
    ) : RevurderingTilAttestering() {
        override val erOpphørt = true

        override fun accept(visitor: RevurderingVisitor) {
            visitor.visit(this)
        }

        override val skalFøreTilUtsendingAvVedtaksbrev = true
        val opphørsperiodeForUtbetalinger = OpphørsperiodeForUtbetalinger(this).value

        fun utledOpphørsgrunner(clock: Clock): List<Opphørsgrunn> {
            return when (
                val opphør = VurderOpphørVedRevurdering.VilkårsvurderingerOgBeregning(
                    vilkårsvurderinger = vilkårsvurderinger,
                    beregning = beregning,
                    clock = clock,
                ).resultat
            ) {
                is OpphørVedRevurdering.Ja -> opphør.opphørsgrunner
                OpphørVedRevurdering.Nei -> emptyList()
            }
        }

        override fun tilIverksatt(
            attestant: NavIdentBruker.Attestant,
            hentOpprinneligAvkorting: (id: UUID) -> Avkortingsvarsel?,
            clock: Clock,
        ): Either<KunneIkkeIverksetteRevurdering, IverksattRevurdering.Opphørt> {
            return validerTilIverksettOvergang(
                attestant = attestant,
                hentOpprinneligAvkorting = hentOpprinneligAvkorting,
                saksbehandler = saksbehandler,
                avkorting = avkorting,
            ).map {
                IverksattRevurdering.Opphørt(
                    id = id,
                    periode = periode,
                    opprettet = opprettet,
                    tilRevurdering = tilRevurdering,
                    saksbehandler = saksbehandler,
                    beregning = beregning,
                    simulering = simulering,
                    oppgaveId = oppgaveId,
                    fritekstTilBrev = fritekstTilBrev,
                    revurderingsårsak = revurderingsårsak,
                    grunnlagsdata = grunnlagsdata,
                    vilkårsvurderinger = vilkårsvurderinger,
                    informasjonSomRevurderes = informasjonSomRevurderes,
                    attesteringer = attesteringer.leggTilNyAttestering(
                        Attestering.Iverksatt(
                            attestant,
                            Tidspunkt.now(clock),
                        ),
                    ),
                    avkorting = avkorting.iverksett(id),
                    tilbakekrevingsbehandling = tilbakekrevingsbehandling.fullførBehandling(),
                    sakinfo = sakinfo,
                )
            }
        }
    }

    data class IngenEndring(
        override val id: UUID,
        override val periode: Periode,
        override val opprettet: Tidspunkt,
        override val tilRevurdering: UUID,
        override val saksbehandler: Saksbehandler,
        override val beregning: Beregning,
        override val oppgaveId: OppgaveId,
        override val fritekstTilBrev: String,
        override val revurderingsårsak: Revurderingsårsak,
        override val skalFøreTilUtsendingAvVedtaksbrev: Boolean,
        override val grunnlagsdata: Grunnlagsdata,
        override val vilkårsvurderinger: Vilkårsvurderinger.Revurdering,
        override val informasjonSomRevurderes: InformasjonSomRevurderes,
        override val attesteringer: Attesteringshistorikk,
        override val avkorting: AvkortingVedRevurdering.Håndtert,
        override val sakinfo: SakInfo,
    ) : RevurderingTilAttestering() {

        override val erOpphørt = false
        override val simulering: Simulering? = null

        override fun accept(visitor: RevurderingVisitor) {
            visitor.visit(this)
        }

        override fun tilIverksatt(
            attestant: NavIdentBruker.Attestant,
            hentOpprinneligAvkorting: (id: UUID) -> Avkortingsvarsel?,
            clock: Clock,
        ): Either<KunneIkkeIverksetteRevurdering, IverksattRevurdering.IngenEndring> {
            return validerTilIverksettOvergang(
                attestant = attestant,
                hentOpprinneligAvkorting = hentOpprinneligAvkorting,
                saksbehandler = saksbehandler,
                avkorting = avkorting,
            ).map {
                return IverksattRevurdering.IngenEndring(
                    id = id,
                    periode = periode,
                    opprettet = opprettet,
                    tilRevurdering = tilRevurdering,
                    saksbehandler = saksbehandler,
                    beregning = beregning,
                    oppgaveId = oppgaveId,
                    fritekstTilBrev = fritekstTilBrev,
                    revurderingsårsak = revurderingsårsak,
                    skalFøreTilUtsendingAvVedtaksbrev = skalFøreTilUtsendingAvVedtaksbrev,
                    grunnlagsdata = grunnlagsdata,
                    vilkårsvurderinger = vilkårsvurderinger,
                    informasjonSomRevurderes = informasjonSomRevurderes,
                    attesteringer = attesteringer.leggTilNyAttestering(
                        Attestering.Iverksatt(
                            attestant,
                            Tidspunkt.now(clock),
                        ),
                    ),
                    avkorting = avkorting.iverksett(id),
                    sakinfo = sakinfo,
                ).right()
            }
        }
    }

    override fun beregn(
        eksisterendeUtbetalinger: List<Utbetaling>,
        clock: Clock,
        gjeldendeVedtaksdata: GjeldendeVedtaksdata,
        satsFactory: SatsFactory,
    ) = throw RuntimeException("Skal ikke kunne beregne når revurderingen er til attestering")

    sealed class KunneIkkeIverksetteRevurdering {
        object AttestantOgSaksbehandlerKanIkkeVæreSammePerson : KunneIkkeIverksetteRevurdering()
        object HarBlittAnnullertAvEnAnnen : KunneIkkeIverksetteRevurdering()
        object HarAlleredeBlittAvkortetAvEnAnnen : KunneIkkeIverksetteRevurdering()
        data class KunneIkkeUtbetale(val utbetalingFeilet: UtbetalingFeilet) : KunneIkkeIverksetteRevurdering()
    }

    fun underkjenn(
        attestering: Attestering.Underkjent,
        oppgaveId: OppgaveId,
    ): UnderkjentRevurdering {
        return when (this) {
            is Innvilget -> UnderkjentRevurdering.Innvilget(
                id = id,
                periode = periode,
                opprettet = opprettet,
                tilRevurdering = tilRevurdering,
                saksbehandler = saksbehandler,
                beregning = beregning,
                simulering = simulering,
                oppgaveId = oppgaveId,
                attesteringer = attesteringer.leggTilNyAttestering(attestering),
                fritekstTilBrev = fritekstTilBrev,
                revurderingsårsak = revurderingsårsak,
                grunnlagsdata = grunnlagsdata,
                vilkårsvurderinger = vilkårsvurderinger,
                informasjonSomRevurderes = informasjonSomRevurderes,
                avkorting = avkorting,
                tilbakekrevingsbehandling = tilbakekrevingsbehandling,
                sakinfo = sakinfo,
            )

            is Opphørt -> UnderkjentRevurdering.Opphørt(
                id = id,
                periode = periode,
                opprettet = opprettet,
                tilRevurdering = tilRevurdering,
                saksbehandler = saksbehandler,
                beregning = beregning,
                simulering = simulering,
                oppgaveId = oppgaveId,
                attesteringer = attesteringer.leggTilNyAttestering(attestering),
                fritekstTilBrev = fritekstTilBrev,
                revurderingsårsak = revurderingsårsak,
                grunnlagsdata = grunnlagsdata,
                vilkårsvurderinger = vilkårsvurderinger,
                informasjonSomRevurderes = informasjonSomRevurderes,
                avkorting = avkorting,
                tilbakekrevingsbehandling = tilbakekrevingsbehandling,
                sakinfo = sakinfo,
            )

            is IngenEndring -> UnderkjentRevurdering.IngenEndring(
                id = id,
                periode = periode,
                opprettet = opprettet,
                tilRevurdering = tilRevurdering,
                saksbehandler = saksbehandler,
                beregning = beregning,
                oppgaveId = oppgaveId,
                attesteringer = attesteringer.leggTilNyAttestering(attestering),
                fritekstTilBrev = fritekstTilBrev,
                revurderingsårsak = revurderingsårsak,
                skalFøreTilUtsendingAvVedtaksbrev = skalFøreTilUtsendingAvVedtaksbrev,
                grunnlagsdata = grunnlagsdata,
                vilkårsvurderinger = vilkårsvurderinger,
                informasjonSomRevurderes = informasjonSomRevurderes,
                avkorting = avkorting,
                sakinfo = sakinfo,
            )
        }
    }
}

sealed class IverksattRevurdering : Revurdering() {
    abstract override val id: UUID
    abstract override val periode: Periode
    abstract override val opprettet: Tidspunkt
    abstract override val tilRevurdering: UUID
    abstract override val saksbehandler: Saksbehandler
    abstract override val oppgaveId: OppgaveId
    abstract override val fritekstTilBrev: String
    abstract override val revurderingsårsak: Revurderingsårsak
    abstract override val beregning: Beregning
    val attestering: Attestering
        get() = attesteringer.hentSisteAttestering()
    abstract override val avkorting: AvkortingVedRevurdering.Iverksatt

    abstract override fun accept(visitor: RevurderingVisitor)

    data class Innvilget(
        override val id: UUID,
        override val periode: Periode,
        override val opprettet: Tidspunkt,
        override val tilRevurdering: UUID,
        override val saksbehandler: Saksbehandler,
        override val oppgaveId: OppgaveId,
        override val fritekstTilBrev: String,
        override val revurderingsårsak: Revurderingsårsak,
        override val beregning: Beregning,
        override val simulering: Simulering,
        override val grunnlagsdata: Grunnlagsdata,
        override val vilkårsvurderinger: Vilkårsvurderinger.Revurdering,
        override val informasjonSomRevurderes: InformasjonSomRevurderes,
        override val attesteringer: Attesteringshistorikk,
        override val avkorting: AvkortingVedRevurdering.Iverksatt,
        val tilbakekrevingsbehandling: Tilbakekrevingsbehandling.Ferdigbehandlet,
        override val sakinfo: SakInfo,
    ) : IverksattRevurdering() {

        override val erOpphørt = false

        override fun accept(visitor: RevurderingVisitor) {
            visitor.visit(this)
        }

        fun tilbakekrevingErVurdert(): Either<Unit, Tilbakekrevingsbehandling.UnderBehandling.VurderTilbakekreving.Avgjort> {
            return tilbakekrevingsbehandling.tilbakekrevingErVurdert()
        }
    }

    data class Opphørt(
        override val id: UUID,
        override val periode: Periode,
        override val opprettet: Tidspunkt,
        override val tilRevurdering: UUID,
        override val saksbehandler: Saksbehandler,
        override val oppgaveId: OppgaveId,
        override val fritekstTilBrev: String,
        override val revurderingsårsak: Revurderingsårsak,
        override val beregning: Beregning,
        override val simulering: Simulering,
        override val grunnlagsdata: Grunnlagsdata,
        override val vilkårsvurderinger: Vilkårsvurderinger.Revurdering,
        override val informasjonSomRevurderes: InformasjonSomRevurderes,
        override val attesteringer: Attesteringshistorikk,
        override val avkorting: AvkortingVedRevurdering.Iverksatt,
        val tilbakekrevingsbehandling: Tilbakekrevingsbehandling.Ferdigbehandlet,
        override val sakinfo: SakInfo,
    ) : IverksattRevurdering() {
        override val erOpphørt = true

        override fun accept(visitor: RevurderingVisitor) {
            visitor.visit(this)
        }

        fun utledOpphørsgrunner(clock: Clock): List<Opphørsgrunn> {
            val opphør = VurderOpphørVedRevurdering.VilkårsvurderingerOgBeregning(
                vilkårsvurderinger = vilkårsvurderinger,
                beregning = beregning,
                clock = clock,
            ).resultat
            return when (opphør) {
                is OpphørVedRevurdering.Ja -> opphør.opphørsgrunner
                OpphørVedRevurdering.Nei -> emptyList()
            }
        }

        fun utledOpphørsdato(clock: Clock): LocalDate? {
            val opphør = VurderOpphørVedRevurdering.VilkårsvurderingerOgBeregning(
                vilkårsvurderinger = vilkårsvurderinger,
                beregning = beregning,
                clock = clock,
            ).resultat
            return when (opphør) {
                is OpphørVedRevurdering.Ja -> opphør.opphørsdato
                OpphørVedRevurdering.Nei -> null
            }
        }

        fun tilbakekrevingErVurdert(): Either<Unit, Tilbakekrevingsbehandling.UnderBehandling.VurderTilbakekreving.Avgjort> {
            return tilbakekrevingsbehandling.tilbakekrevingErVurdert()
        }
    }

    data class IngenEndring(
        override val id: UUID,
        override val periode: Periode,
        override val opprettet: Tidspunkt,
        override val tilRevurdering: UUID,
        override val saksbehandler: Saksbehandler,
        override val beregning: Beregning,
        override val oppgaveId: OppgaveId,
        override val fritekstTilBrev: String,
        override val revurderingsårsak: Revurderingsårsak,
        val skalFøreTilUtsendingAvVedtaksbrev: Boolean,
        override val grunnlagsdata: Grunnlagsdata,
        override val vilkårsvurderinger: Vilkårsvurderinger.Revurdering,
        override val informasjonSomRevurderes: InformasjonSomRevurderes,
        override val attesteringer: Attesteringshistorikk,
        override val avkorting: AvkortingVedRevurdering.Iverksatt,
        override val sakinfo: SakInfo,
    ) : IverksattRevurdering() {
        override val erOpphørt = false
        override val simulering: Simulering? = null

        override fun accept(visitor: RevurderingVisitor) {
            visitor.visit(this)
        }
    }

    override fun beregn(
        eksisterendeUtbetalinger: List<Utbetaling>,
        clock: Clock,
        gjeldendeVedtaksdata: GjeldendeVedtaksdata,
        satsFactory: SatsFactory,
    ) = throw RuntimeException("Skal ikke kunne beregne når revurderingen er iverksatt")
}

sealed class UnderkjentRevurdering : Revurdering() {
    abstract override val beregning: Beregning
    abstract override val attesteringer: Attesteringshistorikk
    abstract override val grunnlagsdata: Grunnlagsdata
    abstract override val vilkårsvurderinger: Vilkårsvurderinger.Revurdering
    val attestering: Attestering.Underkjent
        get() = attesteringer.hentSisteAttestering() as Attestering.Underkjent

    abstract override fun accept(visitor: RevurderingVisitor)

    override fun oppdaterUføreOgMarkerSomVurdert(
        uføre: UføreVilkår.Vurdert,
    ) = oppdaterUføreOgMarkerSomVurdertInternal(uføre)

    override fun oppdaterUtenlandsoppholdOgMarkerSomVurdert(
        utenlandsopphold: UtenlandsoppholdVilkår.Vurdert,
    ) = oppdaterUtenlandsoppholdOgMarkerSomVurdertInternal(utenlandsopphold)

    override fun oppdaterFormueOgMarkerSomVurdert(formue: FormueVilkår.Vurdert): Either<KunneIkkeLeggeTilFormue, OpprettetRevurdering> =
        oppdaterFormueOgMarkerSomVurdertInternal(formue)

    override fun oppdaterFradragOgMarkerSomVurdert(fradragsgrunnlag: List<Grunnlag.Fradragsgrunnlag>) =
        oppdaterFradragOgMarkerSomVurdertInternal(fradragsgrunnlag)

    override fun oppdaterPensjonsvilkårOgMarkerSomVurdert(vilkår: PensjonsVilkår.Vurdert): Either<KunneIkkeLeggeTilPensjonsVilkår, OpprettetRevurdering> {
        return oppdaterPensjonsVilkårOgMarkerSomVurdertInternal(vilkår)
    }

    override fun oppdaterFlyktningvilkårOgMarkerSomVurdert(vilkår: FlyktningVilkår.Vurdert): Either<KunneIkkeLeggeTilFlyktningVilkår, OpprettetRevurdering> {
        return oppdaterFlyktningVilkårOgMarkerSomVurdertInternal(vilkår)
    }

    override fun oppdaterPersonligOppmøtevilkårOgMarkerSomVurdert(vilkår: PersonligOppmøteVilkår.Vurdert): Either<KunneIkkeLeggeTilPersonligOppmøteVilkår, OpprettetRevurdering> {
        return oppdaterPersonligOppmøteVilkårOgMarkerSomVurdertInternal(vilkår)
    }

    override fun oppdaterBosituasjonOgMarkerSomVurdert(bosituasjon: List<Grunnlag.Bosituasjon.Fullstendig>) =
        oppdaterBosituasjonOgMarkerSomVurdertInternal(bosituasjon)

    override fun oppdaterOpplysningspliktOgMarkerSomVurdert(opplysningspliktVilkår: OpplysningspliktVilkår.Vurdert): Either<KunneIkkeLeggeTilOpplysningsplikt, OpprettetRevurdering> {
        return oppdaterOpplysnigspliktOgMarkerSomVurdertInternal(opplysningspliktVilkår)
    }

    override fun oppdaterLovligOppholdOgMarkerSomVurdert(lovligOppholdVilkår: LovligOppholdVilkår.Vurdert): Either<KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilLovligOpphold, OpprettetRevurdering> {
        return oppdaterLovligOppholdOgMarkerSomVurdertInternal(lovligOppholdVilkår)
    }

    override fun oppdaterInstitusjonsoppholdOgMarkerSomVurdert(institusjonsoppholdVilkår: InstitusjonsoppholdVilkår.Vurdert): Either<KunneIkkeLeggeTilInstitusjonsoppholdVilkår, OpprettetRevurdering> {
        return oppdaterInstitusjonsoppholdOgMarkerSomVurdertInternal(institusjonsoppholdVilkår)
    }

    override fun oppdaterFradrag(fradragsgrunnlag: List<Grunnlag.Fradragsgrunnlag>): Either<KunneIkkeLeggeTilFradrag, OpprettetRevurdering> {
        return oppdaterFradragInternal(fradragsgrunnlag)
    }

    override fun oppdaterFastOppholdINorgeOgMarkerSomVurdert(vilkår: FastOppholdINorgeVilkår.Vurdert): Either<KunneIkkeLeggeTilFastOppholdINorgeVilkår, OpprettetRevurdering> {
        return oppdaterFastOppholdINorgeOgMarkerSomVurdertInternal(vilkår)
    }

    override fun oppdater(
        periode: Periode,
        revurderingsårsak: Revurderingsårsak,
        grunnlagsdata: Grunnlagsdata,
        vilkårsvurderinger: Vilkårsvurderinger.Revurdering,
        informasjonSomRevurderes: InformasjonSomRevurderes,
        tilRevurdering: UUID,
        avkorting: AvkortingVedRevurdering.Uhåndtert,
        saksbehandler: Saksbehandler,
    ): Either<KunneIkkeOppdatereRevurdering, OpprettetRevurdering> {
        return oppdaterInternal(
            periode = periode,
            revurderingsårsak = revurderingsårsak,
            grunnlagsdata = grunnlagsdata,
            vilkårsvurderinger = vilkårsvurderinger,
            informasjonSomRevurderes = informasjonSomRevurderes,
            tilRevurdering = tilRevurdering,
            avkorting = avkorting,
            saksbehandler = saksbehandler,
        )
    }

    data class Innvilget(
        override val id: UUID,
        override val periode: Periode,
        override val opprettet: Tidspunkt,
        override val tilRevurdering: UUID,
        override val saksbehandler: Saksbehandler,
        override val oppgaveId: OppgaveId,
        override val fritekstTilBrev: String,
        override val revurderingsårsak: Revurderingsårsak,
        override val beregning: Beregning,
        override val attesteringer: Attesteringshistorikk,
        override val simulering: Simulering,
        override val grunnlagsdata: Grunnlagsdata,
        override val vilkårsvurderinger: Vilkårsvurderinger.Revurdering,
        override val informasjonSomRevurderes: InformasjonSomRevurderes,
        override val avkorting: AvkortingVedRevurdering.Håndtert,
        val tilbakekrevingsbehandling: Tilbakekrevingsbehandling.UnderBehandling,
        override val sakinfo: SakInfo,
    ) : UnderkjentRevurdering() {
        override val erOpphørt = false

        override fun accept(visitor: RevurderingVisitor) {
            visitor.visit(this)
        }

        fun tilAttestering(
            oppgaveId: OppgaveId,
            saksbehandler: Saksbehandler,
            fritekstTilBrev: String,
        ) = RevurderingTilAttestering.Innvilget(
            id = id,
            periode = periode,
            opprettet = opprettet,
            tilRevurdering = tilRevurdering,
            saksbehandler = saksbehandler,
            beregning = beregning,
            simulering = simulering,
            oppgaveId = oppgaveId,
            fritekstTilBrev = fritekstTilBrev,
            revurderingsårsak = revurderingsårsak,
            grunnlagsdata = grunnlagsdata,
            vilkårsvurderinger = vilkårsvurderinger,
            informasjonSomRevurderes = informasjonSomRevurderes,
            attesteringer = attesteringer,
            avkorting = avkorting,
            tilbakekrevingsbehandling = tilbakekrevingsbehandling,
            sakinfo = sakinfo,
        )
    }

    data class Opphørt(
        override val id: UUID,
        override val periode: Periode,
        override val opprettet: Tidspunkt,
        override val tilRevurdering: UUID,
        override val saksbehandler: Saksbehandler,
        override val oppgaveId: OppgaveId,
        override val fritekstTilBrev: String,
        override val revurderingsårsak: Revurderingsårsak,
        override val beregning: Beregning,
        override val simulering: Simulering,
        override val grunnlagsdata: Grunnlagsdata,
        override val vilkårsvurderinger: Vilkårsvurderinger.Revurdering,
        override val informasjonSomRevurderes: InformasjonSomRevurderes,
        override val attesteringer: Attesteringshistorikk,
        override val avkorting: AvkortingVedRevurdering.Håndtert,
        val tilbakekrevingsbehandling: Tilbakekrevingsbehandling.UnderBehandling,
        override val sakinfo: SakInfo,
    ) : UnderkjentRevurdering() {
        override val erOpphørt = true

        override fun accept(visitor: RevurderingVisitor) {
            visitor.visit(this)
        }

        fun utledOpphørsgrunner(clock: Clock): List<Opphørsgrunn> {
            return when (
                val opphør = VurderOpphørVedRevurdering.VilkårsvurderingerOgBeregning(
                    vilkårsvurderinger = vilkårsvurderinger,
                    beregning = beregning,
                    clock = clock,
                ).resultat
            ) {
                is OpphørVedRevurdering.Ja -> opphør.opphørsgrunner
                OpphørVedRevurdering.Nei -> emptyList()
            }
        }

        object KanIkkeSendeEnOpphørtGReguleringTilAttestering

        fun tilAttestering(
            oppgaveId: OppgaveId,
            saksbehandler: Saksbehandler,
            fritekstTilBrev: String,
        ): Either<KanIkkeSendeEnOpphørtGReguleringTilAttestering, RevurderingTilAttestering.Opphørt> {
            if (revurderingsårsak.årsak == Revurderingsårsak.Årsak.REGULER_GRUNNBELØP) {
                return KanIkkeSendeEnOpphørtGReguleringTilAttestering.left()
            } else {
                return RevurderingTilAttestering.Opphørt(
                    id = id,
                    periode = periode,
                    opprettet = opprettet,
                    tilRevurdering = tilRevurdering,
                    saksbehandler = saksbehandler,
                    beregning = beregning,
                    simulering = simulering,
                    oppgaveId = oppgaveId,
                    fritekstTilBrev = fritekstTilBrev,
                    revurderingsårsak = revurderingsårsak,
                    grunnlagsdata = grunnlagsdata,
                    vilkårsvurderinger = vilkårsvurderinger,
                    informasjonSomRevurderes = informasjonSomRevurderes,
                    attesteringer = attesteringer,
                    avkorting = avkorting,
                    tilbakekrevingsbehandling = tilbakekrevingsbehandling,
                    sakinfo = sakinfo,
                ).right()
            }
        }
    }

    data class IngenEndring(
        override val id: UUID,
        override val periode: Periode,
        override val opprettet: Tidspunkt,
        override val tilRevurdering: UUID,
        override val saksbehandler: Saksbehandler,
        override val beregning: Beregning,
        override val oppgaveId: OppgaveId,
        override val fritekstTilBrev: String,
        override val revurderingsårsak: Revurderingsårsak,
        val skalFøreTilUtsendingAvVedtaksbrev: Boolean,
        override val grunnlagsdata: Grunnlagsdata,
        override val vilkårsvurderinger: Vilkårsvurderinger.Revurdering,
        override val informasjonSomRevurderes: InformasjonSomRevurderes,
        override val attesteringer: Attesteringshistorikk,
        override val avkorting: AvkortingVedRevurdering.Håndtert,
        override val sakinfo: SakInfo,
    ) : UnderkjentRevurdering() {
        override val erOpphørt = false
        override val simulering: Simulering? = null

        override fun accept(visitor: RevurderingVisitor) {
            visitor.visit(this)
        }

        fun tilAttestering(
            oppgaveId: OppgaveId,
            saksbehandler: Saksbehandler,
            fritekstTilBrev: String,
            skalFøreTilUtsendingAvVedtaksbrev: Boolean,
        ): RevurderingTilAttestering.IngenEndring {
            return RevurderingTilAttestering.IngenEndring(
                id = id,
                periode = periode,
                opprettet = opprettet,
                tilRevurdering = tilRevurdering,
                saksbehandler = saksbehandler,
                beregning = beregning,
                oppgaveId = oppgaveId,
                fritekstTilBrev = fritekstTilBrev,
                revurderingsårsak = revurderingsårsak,
                skalFøreTilUtsendingAvVedtaksbrev = if (revurderingsårsak.årsak == Revurderingsårsak.Årsak.REGULER_GRUNNBELØP) false else skalFøreTilUtsendingAvVedtaksbrev,
                grunnlagsdata = grunnlagsdata,
                vilkårsvurderinger = vilkårsvurderinger,
                informasjonSomRevurderes = informasjonSomRevurderes,
                attesteringer = attesteringer,
                avkorting = avkorting,
                sakinfo = sakinfo,
            )
        }
    }
}

fun Revurdering.medFritekst(fritekstTilBrev: String) =
    when (this) {
        is BeregnetRevurdering.IngenEndring -> copy(fritekstTilBrev = fritekstTilBrev)
        is BeregnetRevurdering.Innvilget -> copy(fritekstTilBrev = fritekstTilBrev)
        is IverksattRevurdering.Opphørt -> copy(fritekstTilBrev = fritekstTilBrev)
        is IverksattRevurdering.Innvilget -> copy(fritekstTilBrev = fritekstTilBrev)
        is OpprettetRevurdering -> copy(fritekstTilBrev = fritekstTilBrev)
        is RevurderingTilAttestering.Opphørt -> copy(fritekstTilBrev = fritekstTilBrev)
        is RevurderingTilAttestering.Innvilget -> copy(fritekstTilBrev = fritekstTilBrev)
        is SimulertRevurdering.Opphørt -> copy(fritekstTilBrev = fritekstTilBrev)
        is SimulertRevurdering.Innvilget -> copy(fritekstTilBrev = fritekstTilBrev)
        is UnderkjentRevurdering.Opphørt -> copy(fritekstTilBrev = fritekstTilBrev)
        is UnderkjentRevurdering.Innvilget -> copy(fritekstTilBrev = fritekstTilBrev)
        is BeregnetRevurdering.Opphørt -> copy(fritekstTilBrev = fritekstTilBrev)
        is IverksattRevurdering.IngenEndring -> copy(fritekstTilBrev = fritekstTilBrev)
        is RevurderingTilAttestering.IngenEndring -> copy(fritekstTilBrev = fritekstTilBrev)
        is UnderkjentRevurdering.IngenEndring -> copy(fritekstTilBrev = fritekstTilBrev)
        is AvsluttetRevurdering -> copy(
            brevvalg = Brevvalg.SaksbehandlersValg.SkalSendeBrev.InformasjonsbrevMedFritekst(
                fritekstTilBrev,
            ),
        )
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

private fun validerTilIverksettOvergang(
    attestant: NavIdentBruker.Attestant,
    hentOpprinneligAvkorting: (id: UUID) -> Avkortingsvarsel?,
    saksbehandler: Saksbehandler,
    avkorting: AvkortingVedRevurdering.Håndtert,
): Either<RevurderingTilAttestering.KunneIkkeIverksetteRevurdering, Unit> {
    if (saksbehandler.navIdent == attestant.navIdent) {
        return RevurderingTilAttestering.KunneIkkeIverksetteRevurdering.AttestantOgSaksbehandlerKanIkkeVæreSammePerson.left()
    }

    val avkortingId = when (avkorting) {
        is AvkortingVedRevurdering.Håndtert.AnnullerUtestående -> {
            avkorting.avkortingsvarsel.id
        }

        AvkortingVedRevurdering.Håndtert.IngenNyEllerUtestående -> {
            null
        }

        is AvkortingVedRevurdering.Håndtert.KanIkkeHåndteres -> {
            null
        }

        is AvkortingVedRevurdering.Håndtert.OpprettNyttAvkortingsvarsel -> {
            null
        }

        is AvkortingVedRevurdering.Håndtert.OpprettNyttAvkortingsvarselOgAnnullerUtestående -> {
            avkorting.annullerUtestående.id
        }
    }

    if (avkortingId != null) {
        hentOpprinneligAvkorting(avkortingId).also { avkortingsvarsel ->
            when (avkortingsvarsel) {
                Avkortingsvarsel.Ingen -> {
                    throw IllegalStateException("Prøver å iverksette avkorting uten at det finnes noe å avkorte")
                }

                is Avkortingsvarsel.Utenlandsopphold.Annullert -> {
                    return RevurderingTilAttestering.KunneIkkeIverksetteRevurdering.HarBlittAnnullertAvEnAnnen.left()
                }

                is Avkortingsvarsel.Utenlandsopphold.Avkortet -> {
                    return RevurderingTilAttestering.KunneIkkeIverksetteRevurdering.HarAlleredeBlittAvkortetAvEnAnnen.left()
                }

                is Avkortingsvarsel.Utenlandsopphold.Opprettet -> {
                    throw IllegalStateException("Prøver å iverksette avkorting uten at det finnes noe å avkorte")
                }

                is Avkortingsvarsel.Utenlandsopphold.SkalAvkortes -> {
                    // Dette er den eneste som er gyldig
                }

                null -> {
                    throw IllegalStateException("Prøver å iverksette avkorting uten at det finnes noe å avkorte")
                }
            }
        }
    }
    return Unit.right()
}
