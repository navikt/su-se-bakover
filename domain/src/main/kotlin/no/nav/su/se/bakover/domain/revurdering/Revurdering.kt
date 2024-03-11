package no.nav.su.se.bakover.domain.revurdering

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import behandling.revurdering.domain.GrunnlagsdataOgVilkårsvurderingerRevurdering
import behandling.revurdering.domain.VilkårsvurderingerRevurdering
import behandling.revurdering.domain.bosituasjon.KunneIkkeLeggeTilBosituasjonForRevurdering
import behandling.revurdering.domain.formue.KunneIkkeLeggeTilFormue
import beregning.domain.Beregning
import beregning.domain.BeregningStrategyFactory
import dokument.domain.GenererDokumentCommand
import dokument.domain.brev.Brevvalg
import no.nav.su.se.bakover.behandling.BehandlingMedAttestering
import no.nav.su.se.bakover.behandling.BehandlingMedOppgave
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.attestering.Attesteringshistorikk
import no.nav.su.se.bakover.common.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.common.domain.sak.SakInfo
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.ident.NavIdentBruker.Saksbehandler
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.domain.behandling.avslag.Opphørsgrunn
import no.nav.su.se.bakover.domain.revurdering.beregning.KunneIkkeBeregneRevurdering
import no.nav.su.se.bakover.domain.revurdering.beregning.Normal
import no.nav.su.se.bakover.domain.revurdering.oppdater.KunneIkkeOppdatereRevurdering
import no.nav.su.se.bakover.domain.revurdering.opphør.OpphørVedRevurdering
import no.nav.su.se.bakover.domain.revurdering.opphør.VurderOmBeregningGirOpphørVedRevurdering
import no.nav.su.se.bakover.domain.revurdering.opphør.VurderOmVilkårGirOpphørVedRevurdering
import no.nav.su.se.bakover.domain.revurdering.revurderes.VedtakSomRevurderesMånedsvis
import no.nav.su.se.bakover.domain.revurdering.steg.InformasjonSomRevurderes
import no.nav.su.se.bakover.domain.revurdering.steg.Revurderingsteg
import no.nav.su.se.bakover.domain.revurdering.vilkår.opphold.KunneIkkeOppdatereLovligOppholdOgMarkereSomVurdert
import no.nav.su.se.bakover.domain.revurdering.årsak.Revurderingsårsak
import no.nav.su.se.bakover.domain.vedtak.GjeldendeVedtaksdata
import no.nav.su.se.bakover.domain.vilkår.InstitusjonsoppholdVilkår
import satser.domain.SatsFactory
import vilkår.bosituasjon.domain.grunnlag.Bosituasjon
import vilkår.bosituasjon.domain.grunnlag.Bosituasjon.Companion.minsteAntallSammenhengendePerioder
import vilkår.common.domain.inneholderAlle
import vilkår.fastopphold.domain.FastOppholdINorgeVilkår
import vilkår.flyktning.domain.FlyktningVilkår
import vilkår.formue.domain.FormueVilkår
import vilkår.inntekt.domain.grunnlag.Fradragsgrunnlag
import vilkår.lovligopphold.domain.LovligOppholdVilkår
import vilkår.opplysningsplikt.domain.OpplysningspliktVilkår
import vilkår.pensjon.domain.PensjonsVilkår
import vilkår.personligoppmøte.domain.PersonligOppmøteVilkår
import vilkår.uføre.domain.UføreVilkår
import vilkår.utenlandsopphold.domain.vilkår.UtenlandsoppholdVilkår
import vilkår.vurderinger.domain.BosituasjonKonsistensProblem
import vilkår.vurderinger.domain.BosituasjonOgFormue
import vilkår.vurderinger.domain.Grunnlagsdata
import vilkår.vurderinger.domain.KunneIkkeLageGrunnlagsdata
import økonomi.domain.simulering.Simulering
import økonomi.domain.utbetaling.Utbetalinger
import java.time.Clock
import java.util.UUID
import kotlin.reflect.KClass

sealed interface Revurdering :
    AbstraktRevurdering,
    BehandlingMedOppgave,
    BehandlingMedAttestering {

    val saksbehandler: Saksbehandler
    val revurderingsårsak: Revurderingsårsak
    val informasjonSomRevurderes: InformasjonSomRevurderes
    val erOpphørt: Boolean
    abstract override val beregning: Beregning?
    abstract override val simulering: Simulering?
    abstract override val oppgaveId: OppgaveId
    abstract override val attesteringer: Attesteringshistorikk
    abstract override val vedtakSomRevurderesMånedsvis: VedtakSomRevurderesMånedsvis
    abstract override val sakinfo: SakInfo
    abstract override val oppdatert: Tidspunkt
    abstract override val grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderingerRevurdering
    override val sakId: UUID get() = sakinfo.sakId
    override val saksnummer: Saksnummer get() = sakinfo.saksnummer
    override val fnr: Fnr get() = sakinfo.fnr
    override val sakstype: Sakstype get() = sakinfo.type

    /**
     * Har saksbehandler vurdert saken dithen at penger skal tilbakekreves?
     */
    fun skalTilbakekreve(): Boolean

    fun lagForhåndsvarsel(
        utførtAv: Saksbehandler,
        fritekst: String,
    ): Either<UgyldigTilstand, GenererDokumentCommand> {
        return UgyldigTilstand(this::class, this::class).left()
    }

    data class UgyldigTilstand(val fra: KClass<out Revurdering>, val til: KClass<out Revurdering>)

    /**
     * @param brevvalg Saksbehandler velger selv om man skal sende brev eller ikke når man avslutter en revurdering som har blitt forhåndsvarslet. Merk at man ikke kan gjøre dette valget dersom det ikke har blitt forhåndsvarslet, siden vi da ikke skal sende brev.
     */
    fun avslutt(
        begrunnelse: String,
        brevvalg: Brevvalg.SaksbehandlersValg?,
        tidspunktAvsluttet: Tidspunkt,
        avsluttetAv: NavIdentBruker,
    ): Either<KunneIkkeLageAvsluttetRevurdering, AvsluttetRevurdering> {
        return AvsluttetRevurdering.tryCreate(
            underliggendeRevurdering = this,
            begrunnelse = begrunnelse,
            brevvalg = brevvalg,
            tidspunktAvsluttet = tidspunktAvsluttet,
            avsluttetAv = avsluttetAv,
        )
    }

    sealed interface KunneIkkeLeggeTilFradrag {
        data class Valideringsfeil(val feil: KunneIkkeLageGrunnlagsdata) : KunneIkkeLeggeTilFradrag
        data class UgyldigTilstand(
            val fra: KClass<out Revurdering>,
            val til: KClass<out Revurdering> = OpprettetRevurdering::class,
        ) : KunneIkkeLeggeTilFradrag
    }

    fun oppdater(
        clock: Clock,
        periode: Periode,
        revurderingsårsak: Revurderingsårsak,
        grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderingerRevurdering,
        informasjonSomRevurderes: InformasjonSomRevurderes,
        vedtakSomRevurderesMånedsvis: VedtakSomRevurderesMånedsvis,
        tilRevurdering: UUID,
        saksbehandler: Saksbehandler,
    ): Either<KunneIkkeOppdatereRevurdering, OpprettetRevurdering> {
        return KunneIkkeOppdatereRevurdering.UgyldigTilstand(
            fra = this::class,
            til = OpprettetRevurdering::class,
        ).left()
    }

    /**
     * Protected
     * TODO jah: Denne var protected, men byttet til sealed interface og gjorde den public. Denne bør vel uansett fjernes og erstattes med direktekall til tryCreate/create.
     */
    fun oppdaterInternal(
        clock: Clock,
        periode: Periode,
        revurderingsårsak: Revurderingsårsak,
        grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderingerRevurdering,
        informasjonSomRevurderes: InformasjonSomRevurderes,
        vedtakSomRevurderesMånedsvis: VedtakSomRevurderesMånedsvis,
        tilRevurdering: UUID,
        saksbehandler: Saksbehandler,
    ): Either<KunneIkkeOppdatereRevurdering, OpprettetRevurdering> {
        return OpprettetRevurdering(
            id = id,
            periode = periode,
            opprettet = opprettet,
            oppdatert = Tidspunkt.now(clock),
            tilRevurdering = tilRevurdering,
            saksbehandler = saksbehandler,
            oppgaveId = oppgaveId,
            revurderingsårsak = revurderingsårsak,
            grunnlagsdataOgVilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger,
            informasjonSomRevurderes = informasjonSomRevurderes,
            vedtakSomRevurderesMånedsvis = vedtakSomRevurderesMånedsvis,
            attesteringer = attesteringer,
            sakinfo = sakinfo,
            brevvalgRevurdering = brevvalgRevurdering,
        ).right()
    }

    fun oppdaterUføreOgMarkerSomVurdert(uføre: UføreVilkår.Vurdert): Either<UgyldigTilstand, OpprettetRevurdering> =
        UgyldigTilstand(this::class, OpprettetRevurdering::class).left()

    fun oppdaterUtenlandsoppholdOgMarkerSomVurdert(utenlandsopphold: UtenlandsoppholdVilkår.Vurdert): Either<KunneIkkeLeggeTilUtenlandsopphold, OpprettetRevurdering> =
        KunneIkkeLeggeTilUtenlandsopphold.UgyldigTilstand(this::class, OpprettetRevurdering::class).left()

    fun oppdaterFormueOgMarkerSomVurdert(formue: FormueVilkår.Vurdert): Either<KunneIkkeLeggeTilFormue, OpprettetRevurdering> =
        KunneIkkeLeggeTilFormue.UgyldigTilstand(this::class, OpprettetRevurdering::class).left()

    fun oppdaterFradragOgMarkerSomVurdert(fradragsgrunnlag: List<Fradragsgrunnlag>): Either<KunneIkkeLeggeTilFradrag, OpprettetRevurdering> =
        KunneIkkeLeggeTilFradrag.UgyldigTilstand(this::class, OpprettetRevurdering::class).left()

    fun oppdaterFradrag(fradragsgrunnlag: List<Fradragsgrunnlag>): Either<KunneIkkeLeggeTilFradrag, OpprettetRevurdering> {
        return KunneIkkeLeggeTilFradrag.UgyldigTilstand(this::class, OpprettetRevurdering::class).left()
    }

    fun oppdaterOpplysningspliktOgMarkerSomVurdert(opplysningspliktVilkår: OpplysningspliktVilkår.Vurdert): Either<KunneIkkeLeggeTilOpplysningsplikt, OpprettetRevurdering> {
        return KunneIkkeLeggeTilOpplysningsplikt.UgyldigTilstand(this::class, OpprettetRevurdering::class).left()
    }

    fun oppdaterPensjonsvilkårOgMarkerSomVurdert(vilkår: PensjonsVilkår.Vurdert): Either<KunneIkkeLeggeTilPensjonsVilkår, OpprettetRevurdering> {
        return KunneIkkeLeggeTilPensjonsVilkår.UgyldigTilstand(this::class, OpprettetRevurdering::class).left()
    }

    fun oppdaterFlyktningvilkårOgMarkerSomVurdert(vilkår: FlyktningVilkår.Vurdert): Either<KunneIkkeLeggeTilFlyktningVilkår, OpprettetRevurdering> {
        return KunneIkkeLeggeTilFlyktningVilkår.UgyldigTilstand(this::class, OpprettetRevurdering::class).left()
    }

    fun oppdaterPersonligOppmøtevilkårOgMarkerSomVurdert(vilkår: PersonligOppmøteVilkår.Vurdert): Either<KunneIkkeLeggeTilPersonligOppmøteVilkår, OpprettetRevurdering> {
        return KunneIkkeLeggeTilPersonligOppmøteVilkår.UgyldigTilstand(this::class, OpprettetRevurdering::class).left()
    }

    fun oppdaterLovligOppholdOgMarkerSomVurdert(
        lovligOppholdVilkår: LovligOppholdVilkår.Vurdert,
    ): Either<KunneIkkeOppdatereLovligOppholdOgMarkereSomVurdert, OpprettetRevurdering> {
        return KunneIkkeOppdatereLovligOppholdOgMarkereSomVurdert.UgyldigTilstand(
            this::class,
            OpprettetRevurdering::class,
        ).left()
    }

    fun oppdaterInstitusjonsoppholdOgMarkerSomVurdert(
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

        data object HeleBehandlingsperiodenErIkkeVurdert : KunneIkkeLeggeTilOpplysningsplikt
    }

    sealed interface KunneIkkeLeggeTilPensjonsVilkår {
        data class UgyldigTilstand(
            val fra: KClass<out Revurdering>,
            val til: KClass<out Revurdering> = OpprettetRevurdering::class,
        ) : KunneIkkeLeggeTilPensjonsVilkår

        data object HeleBehandlingsperiodenErIkkeVurdert : KunneIkkeLeggeTilPensjonsVilkår
        data object VilkårKunRelevantForAlder : KunneIkkeLeggeTilPensjonsVilkår
    }

    sealed interface KunneIkkeLeggeTilFlyktningVilkår {
        data class UgyldigTilstand(
            val fra: KClass<out Revurdering>,
            val til: KClass<out Revurdering> = OpprettetRevurdering::class,
        ) : KunneIkkeLeggeTilFlyktningVilkår

        data object HeleBehandlingsperiodenErIkkeVurdert : KunneIkkeLeggeTilFlyktningVilkår
        data object VilkårKunRelevantForUføre : KunneIkkeLeggeTilFlyktningVilkår
    }

    sealed interface KunneIkkeLeggeTilPersonligOppmøteVilkår {
        data class UgyldigTilstand(
            val fra: KClass<out Revurdering>,
            val til: KClass<out Revurdering> = OpprettetRevurdering::class,
        ) : KunneIkkeLeggeTilPersonligOppmøteVilkår

        data object HeleBehandlingsperiodenErIkkeVurdert : KunneIkkeLeggeTilPersonligOppmøteVilkår
    }

    sealed interface KunneIkkeLeggeTilInstitusjonsoppholdVilkår {
        data class UgyldigTilstand(
            val fra: KClass<out Revurdering>,
            val til: KClass<out Revurdering> = OpprettetRevurdering::class,
        ) : KunneIkkeLeggeTilInstitusjonsoppholdVilkår

        data object HeleBehandlingsperiodenErIkkeVurdert : KunneIkkeLeggeTilInstitusjonsoppholdVilkår
    }

    private fun oppdaterOpplysnigspliktInternal(opplysningspliktVilkår: OpplysningspliktVilkår.Vurdert): Either<KunneIkkeLeggeTilOpplysningsplikt, OpprettetRevurdering> {
        if (!periode.fullstendigOverlapp(opplysningspliktVilkår.minsteAntallSammenhengendePerioder())) {
            return KunneIkkeLeggeTilOpplysningsplikt.HeleBehandlingsperiodenErIkkeVurdert.left()
        }
        return oppdaterVilkårsvurderinger(vilkårsvurderinger = vilkårsvurderinger.oppdaterVilkår(opplysningspliktVilkår)).right()
    }

    /**
     * Protected
     * TODO jah: Denne var protected. Bør kanskje ikke ligge på dette nivået.
     */
    fun oppdaterOpplysnigspliktOgMarkerSomVurdertInternal(opplysningspliktVilkår: OpplysningspliktVilkår.Vurdert): Either<KunneIkkeLeggeTilOpplysningsplikt, OpprettetRevurdering> {
        return oppdaterOpplysnigspliktInternal(opplysningspliktVilkår).map {
            it.oppdaterInformasjonSomRevurderes(informasjonSomRevurderes.markerSomVurdert(Revurderingsteg.Opplysningsplikt))
        }
    }

    /**
     * Protected
     * TODO jah: Denne var protected. Bør kanskje ikke ligge på dette nivået.
     */
    fun oppdaterPensjonsVilkårOgMarkerSomVurdertInternal(vilkår: PensjonsVilkår.Vurdert): Either<KunneIkkeLeggeTilPensjonsVilkår, OpprettetRevurdering> {
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
        return oppdaterVilkårsvurderinger(vilkårsvurderinger = vilkårsvurderinger.oppdaterVilkår(vilkår)).right()
    }

    /**
     * Protected
     * TODO jah: Denne var protected. Bør kanskje ikke ligge på dette nivået.
     */
    fun oppdaterFlyktningVilkårOgMarkerSomVurdertInternal(vilkår: FlyktningVilkår.Vurdert): Either<KunneIkkeLeggeTilFlyktningVilkår, OpprettetRevurdering> {
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
        return oppdaterVilkårsvurderinger(vilkårsvurderinger = vilkårsvurderinger.oppdaterVilkår(vilkår)).right()
    }

    private fun oppdaterLovligOpphold(
        lovligOppholdVilkår: LovligOppholdVilkår.Vurdert,
    ): Either<KunneIkkeOppdatereLovligOppholdOgMarkereSomVurdert, OpprettetRevurdering> {
        if (!periode.fullstendigOverlapp(lovligOppholdVilkår.minsteAntallSammenhengendePerioder())) {
            return KunneIkkeOppdatereLovligOppholdOgMarkereSomVurdert.HeleBehandlingsperiodenErIkkeVurdert.left()
        }

        return oppdaterVilkårsvurderinger(vilkårsvurderinger = vilkårsvurderinger.oppdaterVilkår(lovligOppholdVilkår)).right()
    }

    /**
     * Protected
     * TODO jah: Denne var protected. Bør kanskje ikke ligge på dette nivået.
     */
    fun oppdaterLovligOppholdOgMarkerSomVurdertInternal(
        lovligOppholdVilkår: LovligOppholdVilkår.Vurdert,
    ): Either<KunneIkkeOppdatereLovligOppholdOgMarkereSomVurdert, OpprettetRevurdering> {
        return oppdaterLovligOpphold(lovligOppholdVilkår).map {
            it.oppdaterInformasjonSomRevurderes(informasjonSomRevurderes.markerSomVurdert(Revurderingsteg.Oppholdstillatelse))
        }
    }

    fun oppdaterBosituasjonOgMarkerSomVurdert(bosituasjon: List<Bosituasjon.Fullstendig>): Either<KunneIkkeLeggeTilBosituasjonForRevurdering, OpprettetRevurdering> =
        KunneIkkeLeggeTilBosituasjonForRevurdering.UgyldigTilstand(this::class, OpprettetRevurdering::class).left()

    /**
     * Protected
     * TODO jah: Denne var protected. Bør kanskje ikke ligge på dette nivået.
     */
    fun oppdaterUføreOgMarkerSomVurdertInternal(
        uføre: UføreVilkår.Vurdert,
    ): Either<UgyldigTilstand, OpprettetRevurdering> {
        return oppdaterVilkårsvurderinger(
            vilkårsvurderinger = vilkårsvurderinger.oppdaterVilkår(uføre),
        ).oppdaterInformasjonSomRevurderes(
            informasjonSomRevurderes = informasjonSomRevurderes.markerSomVurdert(Revurderingsteg.Uførhet),
        ).right()
    }

    /**
     * Protected
     * TODO jah: Denne var protected. Bør kanskje ikke ligge på dette nivået.
     */
    fun oppdaterPersonligOppmøteVilkårOgMarkerSomVurdertInternal(vilkår: PersonligOppmøteVilkår.Vurdert): Either<KunneIkkeLeggeTilPersonligOppmøteVilkår, OpprettetRevurdering> {
        return oppdaterPersonligOppmøteVilkårInternal(vilkår)
            .map { it.oppdaterInformasjonSomRevurderes(informasjonSomRevurderes.markerSomVurdert(Revurderingsteg.PersonligOppmøte)) }
    }

    private fun oppdaterPersonligOppmøteVilkårInternal(vilkår: PersonligOppmøteVilkår.Vurdert): Either<KunneIkkeLeggeTilPersonligOppmøteVilkår, OpprettetRevurdering> {
        if (!periode.fullstendigOverlapp(vilkår.perioder)) {
            return KunneIkkeLeggeTilPersonligOppmøteVilkår.HeleBehandlingsperiodenErIkkeVurdert.left()
        }
        return oppdaterVilkårsvurderinger(vilkårsvurderinger = vilkårsvurderinger.oppdaterVilkår(vilkår)).right()
    }

    sealed interface KunneIkkeLeggeTilUtenlandsopphold {
        data class UgyldigTilstand(val fra: KClass<out Revurdering>, val til: KClass<out Revurdering>) :
            KunneIkkeLeggeTilUtenlandsopphold

        data object VurderingsperiodeUtenforBehandlingsperiode : KunneIkkeLeggeTilUtenlandsopphold
        data object AlleVurderingsperioderMåHaSammeResultat : KunneIkkeLeggeTilUtenlandsopphold
        data object MåVurdereHelePerioden : KunneIkkeLeggeTilUtenlandsopphold
    }

    /**
     * Protected
     * TODO jah: Denne var protected. Bør kanskje ikke ligge på dette nivået.
     */
    fun oppdaterUtenlandsoppholdOgMarkerSomVurdertInternal(
        vilkår: UtenlandsoppholdVilkår.Vurdert,
    ): Either<KunneIkkeLeggeTilUtenlandsopphold, OpprettetRevurdering> {
        return valider(vilkår).map {
            oppdaterVilkårsvurderinger(
                vilkårsvurderinger = vilkårsvurderinger.oppdaterVilkår(vilkår),
            ).oppdaterInformasjonSomRevurderes(
                informasjonSomRevurderes = informasjonSomRevurderes.markerSomVurdert(Revurderingsteg.Utenlandsopphold),
            )
        }
    }

    /**
     * Protected
     * TODO jah: Denne var protected. Bør kanskje ikke ligge på dette nivået.
     */
    fun valider(utenlandsopphold: UtenlandsoppholdVilkår.Vurdert): Either<KunneIkkeLeggeTilUtenlandsopphold, Unit> {
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

    /**
     * Protected
     * TODO jah: Denne var protected. Bør kanskje ikke ligge på dette nivået.
     */
    fun oppdaterFormueOgMarkerSomVurdertInternal(formue: FormueVilkår.Vurdert): Either<KunneIkkeLeggeTilFormue, OpprettetRevurdering> {
        return oppdaterFormueInternal(formue)
            .map { it.oppdaterInformasjonSomRevurderes(informasjonSomRevurderes.markerSomVurdert(Revurderingsteg.Formue)) }
    }

    /**
     * Protected
     * TODO jah: Denne var protected. Bør kanskje ikke ligge på dette nivået.
     */
    fun oppdaterFormueInternal(formue: FormueVilkår): Either<KunneIkkeLeggeTilFormue.Konsistenssjekk, OpprettetRevurdering> {
        return BosituasjonOgFormue(
            bosituasjon = grunnlagsdata.bosituasjon,
            formue = formue.grunnlag,
        ).resultat.mapLeft {
            KunneIkkeLeggeTilFormue.Konsistenssjekk(it.first())
        }.map {
            oppdaterVilkårsvurderinger(vilkårsvurderinger = vilkårsvurderinger.oppdaterVilkår(formue))
        }
    }

    /**
     * Protected
     * TODO jah: Denne var protected. Bør kanskje ikke ligge på dette nivået.
     */
    fun oppdaterFradragOgMarkerSomVurdertInternal(fradragsgrunnlag: List<Fradragsgrunnlag>): Either<KunneIkkeLeggeTilFradrag, OpprettetRevurdering> {
        return oppdaterFradragInternal(fradragsgrunnlag).getOrElse { return it.left() }
            .oppdaterInformasjonSomRevurderes(
                informasjonSomRevurderes = informasjonSomRevurderes.markerSomVurdert(
                    Revurderingsteg.Inntekt,
                ),
            ).right()
    }

    /**
     * Protected
     * TODO jah: Denne var protected. Bør kanskje ikke ligge på dette nivået.
     */
    fun oppdaterFradragInternal(fradragsgrunnlag: List<Fradragsgrunnlag>): Either<KunneIkkeLeggeTilFradrag, OpprettetRevurdering> {
        require(fradragsgrunnlag.all { periode inneholder it.periode })
        return Grunnlagsdata.tryCreate(
            bosituasjon = grunnlagsdata.bosituasjonSomFullstendig(),
            fradragsgrunnlag = fradragsgrunnlag,
        ).mapLeft {
            KunneIkkeLeggeTilFradrag.Valideringsfeil(it)
        }.map {
            oppdaterGrunnlag(it)
        }
    }

    /**
     * Protected
     * TODO jah: Denne var protected. Bør kanskje ikke ligge på dette nivået.
     */
    fun oppdaterBosituasjonOgMarkerSomVurdertInternal(bosituasjon: List<Bosituasjon.Fullstendig>): Either<KunneIkkeLeggeTilBosituasjonForRevurdering, OpprettetRevurdering> {
        return oppdaterBosituasjonInternal(bosituasjon)
            .map { it.oppdaterInformasjonSomRevurderes(informasjonSomRevurderes.markerSomVurdert(Revurderingsteg.Bosituasjon)) }
    }

    private fun oppdaterBosituasjonInternal(bosituasjon: List<Bosituasjon.Fullstendig>): Either<KunneIkkeLeggeTilBosituasjonForRevurdering, OpprettetRevurdering> {
        if (!periode.fullstendigOverlapp(bosituasjon.minsteAntallSammenhengendePerioder())) {
            return KunneIkkeLeggeTilBosituasjonForRevurdering.PerioderMangler.left()
        }
        return BosituasjonKonsistensProblem(bosituasjon).resultat
            .mapLeft { KunneIkkeLeggeTilBosituasjonForRevurdering.Konsistenssjekk(it.first()) }
            .flatMap {
                grunnlagsdataOgVilkårsvurderinger.oppdaterBosituasjon(bosituasjon).let { grunnlagOgVilkår ->
                    oppdaterGrunnlag(grunnlagOgVilkår.grunnlagsdata)
                        .oppdaterFormueInternal(grunnlagOgVilkår.vilkårsvurderinger.formue)
                        .mapLeft {
                            KunneIkkeLeggeTilBosituasjonForRevurdering.KunneIkkeOppdatereFormue(it.feil)
                        }
                }
            }
    }

    fun oppdaterFastOppholdINorgeOgMarkerSomVurdert(vilkår: FastOppholdINorgeVilkår.Vurdert): Either<KunneIkkeLeggeTilFastOppholdINorgeVilkår, OpprettetRevurdering> {
        return KunneIkkeLeggeTilFastOppholdINorgeVilkår.UgyldigTilstand(this::class, OpprettetRevurdering::class).left()
    }

    sealed interface KunneIkkeLeggeTilFastOppholdINorgeVilkår {
        data class UgyldigTilstand(
            val fra: KClass<out Revurdering>,
            val til: KClass<out Revurdering> = OpprettetRevurdering::class,
        ) : KunneIkkeLeggeTilFastOppholdINorgeVilkår

        data object HeleBehandlingsperiodenErIkkeVurdert : KunneIkkeLeggeTilFastOppholdINorgeVilkår
        data object AlleVurderingsperioderMåHaSammeResultat : KunneIkkeLeggeTilFastOppholdINorgeVilkår
    }

    /**
     * Protected
     * TODO jah: Denne var protected. Bør kanskje ikke ligge på dette nivået.
     */
    fun oppdaterFastOppholdINorgeOgMarkerSomVurdertInternal(vilkår: FastOppholdINorgeVilkår.Vurdert): Either<KunneIkkeLeggeTilFastOppholdINorgeVilkår, OpprettetRevurdering> {
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
        return oppdaterVilkårsvurderinger(vilkårsvurderinger = vilkårsvurderinger.oppdaterVilkår(vilkår)).right()
    }

    /**
     * Protected
     * TODO jah: Denne var protected. Bør kanskje ikke ligge på dette nivået.
     */
    fun oppdaterInstitusjonsoppholdOgMarkerSomVurdertInternal(
        vilkår: InstitusjonsoppholdVilkår.Vurdert,
    ): Either<KunneIkkeLeggeTilInstitusjonsoppholdVilkår.HeleBehandlingsperiodenErIkkeVurdert, OpprettetRevurdering> {
        if (vilkår.perioder.size != 1 || vilkår.perioder.first() != periode) {
            // TODO jah: vilkår.perioder.size != 1 - Vi støtter foreløpig ikke hull i revurderingsperioden
            return KunneIkkeLeggeTilInstitusjonsoppholdVilkår.HeleBehandlingsperiodenErIkkeVurdert.left()
        }
        return oppdaterVilkårsvurderinger(
            vilkårsvurderinger = vilkårsvurderinger.oppdaterVilkår(vilkår),
        ).oppdaterInformasjonSomRevurderes(
            informasjonSomRevurderes = informasjonSomRevurderes.markerSomVurdert(Revurderingsteg.Institusjonsopphold),
        ).right()
    }

    private fun oppdaterVilkårsvurderinger(
        vilkårsvurderinger: VilkårsvurderingerRevurdering,
    ): OpprettetRevurdering {
        return OpprettetRevurdering(
            id = id,
            periode = periode,
            opprettet = opprettet,
            oppdatert = oppdatert,
            tilRevurdering = tilRevurdering,
            saksbehandler = saksbehandler,
            oppgaveId = oppgaveId,
            revurderingsårsak = revurderingsårsak,
            grunnlagsdataOgVilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger.oppdaterVilkårsvurderinger(
                vilkårsvurderinger,
            ),
            informasjonSomRevurderes = informasjonSomRevurderes,
            vedtakSomRevurderesMånedsvis = vedtakSomRevurderesMånedsvis,
            attesteringer = attesteringer,
            sakinfo = sakinfo,
            brevvalgRevurdering = brevvalgRevurdering,
        )
    }

    private fun oppdaterGrunnlag(grunnlagsdata: Grunnlagsdata): OpprettetRevurdering {
        return OpprettetRevurdering(
            id = id,
            periode = periode,
            opprettet = opprettet,
            oppdatert = oppdatert,
            tilRevurdering = tilRevurdering,
            vedtakSomRevurderesMånedsvis = vedtakSomRevurderesMånedsvis,
            saksbehandler = saksbehandler,
            oppgaveId = oppgaveId,
            revurderingsårsak = revurderingsårsak,
            grunnlagsdataOgVilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger.oppdaterGrunnlagsdata(grunnlagsdata),
            informasjonSomRevurderes = informasjonSomRevurderes,
            attesteringer = attesteringer,
            sakinfo = sakinfo,
            brevvalgRevurdering = brevvalgRevurdering,
        )
    }

    fun beregn(
        eksisterendeUtbetalinger: Utbetalinger,
        clock: Clock,
        gjeldendeVedtaksdata: GjeldendeVedtaksdata,
        satsFactory: SatsFactory,
    ): Either<KunneIkkeBeregneRevurdering, BeregnetRevurdering> {
        val (revurdering, beregning) = Normal(
            revurdering = this,
            beregningStrategyFactory = BeregningStrategyFactory(
                clock = clock,
                satsFactory = satsFactory,
            ),
        ).beregn().getOrElse { return it.left() }

        fun opphør(revurdering: Revurdering, revurdertBeregning: Beregning): BeregnetRevurdering.Opphørt {
            if (revurdering !is RevurderingKanBeregnes) {
                throw IllegalStateException("Kan ikke beregne en revurdering i feil tilstand. Må være en av opprettet, beregnet, simulert eller underkjent; men var ${revurdering::class}")
            }
            return BeregnetRevurdering.Opphørt(
                tilRevurdering = revurdering.tilRevurdering,
                id = revurdering.id,
                periode = revurdering.periode,
                opprettet = revurdering.opprettet,
                oppdatert = revurdering.oppdatert,
                beregning = revurdertBeregning,
                saksbehandler = revurdering.saksbehandler,
                oppgaveId = revurdering.oppgaveId,
                revurderingsårsak = revurdering.revurderingsårsak,
                grunnlagsdataOgVilkårsvurderinger = revurdering.grunnlagsdataOgVilkårsvurderinger,
                informasjonSomRevurderes = revurdering.informasjonSomRevurderes,
                vedtakSomRevurderesMånedsvis = vedtakSomRevurderesMånedsvis,
                attesteringer = revurdering.attesteringer,
                sakinfo = sakinfo,
                brevvalgRevurdering = brevvalgRevurdering,
            )
        }

        fun innvilget(revurdering: Revurdering, revurdertBeregning: Beregning): BeregnetRevurdering.Innvilget {
            if (revurdering !is RevurderingKanBeregnes) {
                throw IllegalStateException("Kan ikke beregne en revurdering i feil tilstand. Må være en av opprettet, beregnet, simulert eller underkjent; men var ${revurdering::class}")
            }
            return BeregnetRevurdering.Innvilget(
                id = revurdering.id,
                periode = revurdering.periode,
                opprettet = revurdering.opprettet,
                oppdatert = revurdering.oppdatert,
                tilRevurdering = revurdering.tilRevurdering,
                vedtakSomRevurderesMånedsvis = vedtakSomRevurderesMånedsvis,
                saksbehandler = revurdering.saksbehandler,
                beregning = revurdertBeregning,
                oppgaveId = revurdering.oppgaveId,
                revurderingsårsak = revurdering.revurderingsårsak,
                grunnlagsdataOgVilkårsvurderinger = revurdering.grunnlagsdataOgVilkårsvurderinger,
                informasjonSomRevurderes = revurdering.informasjonSomRevurderes,
                attesteringer = revurdering.attesteringer,
                sakinfo = sakinfo,
                brevvalgRevurdering = brevvalgRevurdering,
            )
        }

        // TODO jm: sjekk av vilkår og verifisering av dette bør sannsynligvis legges til et tidspunkt før selve beregningen finner sted. Snarvei inntil videre, da vi mangeler "infrastruktur" for dette pt.  Bør være en tydeligere del av modellen for revurdering.
        if (VurderOmVilkårGirOpphørVedRevurdering(revurdering.vilkårsvurderinger).resultat is OpphørVedRevurdering.Ja) {
            return opphør(revurdering, beregning).right()
        }

        return when (
            VurderOmBeregningGirOpphørVedRevurdering(
                beregning = beregning,
                clock = clock,
            ).resultat
        ) {
            is OpphørVedRevurdering.Ja -> {
                opphør(revurdering, beregning)
            }

            is OpphørVedRevurdering.Nei -> {
                innvilget(revurdering, beregning)
            }
        }.right()
    }

    abstract override fun skalSendeVedtaksbrev(): Boolean

    fun utledOpphørsgrunner(clock: Clock): List<Opphørsgrunn>
}
