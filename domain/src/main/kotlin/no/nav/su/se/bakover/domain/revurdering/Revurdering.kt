package no.nav.su.se.bakover.domain.revurdering

import arrow.core.Either
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.nonEmptyListOf
import arrow.core.right
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.sikkerLogg
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.NavIdentBruker.Saksbehandler
import no.nav.su.se.bakover.domain.avkorting.AvkortingVedRevurdering
import no.nav.su.se.bakover.domain.avkorting.Avkortingsvarsel
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.behandling.Attesteringshistorikk
import no.nav.su.se.bakover.domain.behandling.Behandling
import no.nav.su.se.bakover.domain.behandling.BehandlingMedAttestering
import no.nav.su.se.bakover.domain.behandling.BehandlingMedOppgave
import no.nav.su.se.bakover.domain.behandling.avslag.Opphørsgrunn
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.grunnlag.KunneIkkeLageGrunnlagsdata
import no.nav.su.se.bakover.domain.grunnlag.singleOrThrow
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingFeilet
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringFeilet
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.revurdering.beregning.BeregnRevurderingStrategyDecider
import no.nav.su.se.bakover.domain.vedtak.GjeldendeVedtaksdata
import no.nav.su.se.bakover.domain.vedtak.VedtakSomKanRevurderes
import no.nav.su.se.bakover.domain.vilkår.Inngangsvilkår
import no.nav.su.se.bakover.domain.vilkår.UtenlandsoppholdVilkår
import no.nav.su.se.bakover.domain.vilkår.Vilkår
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderingsresultat
import no.nav.su.se.bakover.domain.vilkår.inneholderAlle
import no.nav.su.se.bakover.domain.visitor.Visitable
import java.time.Clock
import java.time.LocalDate
import java.util.UUID
import kotlin.reflect.KClass

sealed class AbstraktRevurdering : Behandling {
    abstract val tilRevurdering: VedtakSomKanRevurderes
    override val sakId by lazy { tilRevurdering.behandling.sakId }
    override val saksnummer by lazy { tilRevurdering.behandling.saksnummer }
    override val fnr by lazy { tilRevurdering.behandling.fnr }
    abstract override val vilkårsvurderinger: Vilkårsvurderinger.Revurdering
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

    abstract val forhåndsvarsel: Forhåndsvarsel?
    abstract val avkorting: AvkortingVedRevurdering

    fun vilkårsvurderingsResultat(): Vilkårsvurderingsresultat {
        return vilkårsvurderinger.resultat
    }

    data class UgyldigTilstand(val fra: KClass<out Revurdering>, val til: KClass<out Revurdering>)

    fun avslutt(
        begrunnelse: String,
        fritekst: String?,
        tidspunktAvsluttet: Tidspunkt,
    ): Either<KunneIkkeLageAvsluttetRevurdering, AvsluttetRevurdering> {
        return AvsluttetRevurdering.tryCreate(
            underliggendeRevurdering = this,
            begrunnelse = begrunnelse,
            fritekst = fritekst,
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
        data class Valideringsfeil(val feil: KunneIkkeLageGrunnlagsdata) :
            KunneIkkeLeggeTilBosituasjon()

        data class UgyldigTilstand(val fra: KClass<out Revurdering>, val til: KClass<out Revurdering>) :
            KunneIkkeLeggeTilBosituasjon()
    }

    open fun oppdaterUføreOgMarkerSomVurdert(uføre: Vilkår.Uførhet.Vurdert): Either<UgyldigTilstand, OpprettetRevurdering> =
        UgyldigTilstand(this::class, OpprettetRevurdering::class).left()

    open fun oppdaterUtenlandsoppholdOgMarkerSomVurdert(utenlandsopphold: UtenlandsoppholdVilkår.Vurdert): Either<KunneIkkeLeggeTilUtenlandsopphold, OpprettetRevurdering> =
        KunneIkkeLeggeTilUtenlandsopphold.UgyldigTilstand(this::class, OpprettetRevurdering::class).left()

    open fun oppdaterFormueOgMarkerSomVurdert(formue: Vilkår.Formue.Vurdert): Either<UgyldigTilstand, OpprettetRevurdering> =
        UgyldigTilstand(this::class, OpprettetRevurdering::class).left()

    open fun oppdaterFradragOgMarkerSomVurdert(fradragsgrunnlag: List<Grunnlag.Fradragsgrunnlag>): Either<KunneIkkeLeggeTilFradrag, OpprettetRevurdering> =
        KunneIkkeLeggeTilFradrag.UgyldigTilstand(this::class, OpprettetRevurdering::class).left()

    open fun oppdaterFradrag(fradragsgrunnlag: List<Grunnlag.Fradragsgrunnlag>): Either<KunneIkkeLeggeTilFradrag, OpprettetRevurdering> {
        return KunneIkkeLeggeTilFradrag.UgyldigTilstand(this::class, OpprettetRevurdering::class).left()
    }

    open fun oppdaterBosituasjonOgMarkerSomVurdert(bosituasjon: Grunnlag.Bosituasjon.Fullstendig): Either<KunneIkkeLeggeTilBosituasjon, OpprettetRevurdering> =
        KunneIkkeLeggeTilBosituasjon.UgyldigTilstand(this::class, OpprettetRevurdering::class).left()

    protected fun oppdaterUføreOgMarkerSomVurdertInternal(
        uføre: Vilkår.Uførhet.Vurdert,
    ): Either<UgyldigTilstand, OpprettetRevurdering> {
        return oppdaterVilkårsvurderinger(
            vilkårsvurderinger = vilkårsvurderinger.leggTil(uføre),
        ).oppdaterInformasjonSomRevurderes(
            informasjonSomRevurderes = informasjonSomRevurderes.markerSomVurdert(Revurderingsteg.Uførhet),
        ).right()
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
                it.resultat == utenlandsopphold.vurderingsperioder.first().resultat
            } -> {
                KunneIkkeLeggeTilUtenlandsopphold.AlleVurderingsperioderMåHaSammeResultat.left()
            }
            !periode.fullstendigOverlapp(utenlandsopphold.vurderingsperioder.map { it.periode }) -> {
                KunneIkkeLeggeTilUtenlandsopphold.MåVurdereHelePerioden.left()
            }
            else -> Unit.right()
        }
    }

    protected fun oppdaterFormueOgMarkerSomVurdertInternal(formue: Vilkår.Formue.Vurdert) =
        oppdaterVilkårsvurderinger(vilkårsvurderinger = vilkårsvurderinger.leggTil(formue))
            .oppdaterInformasjonSomRevurderes(informasjonSomRevurderes.markerSomVurdert(Revurderingsteg.Formue))
            .right()

    protected fun oppdaterFradragOgMarkerSomVurdertInternal(fradragsgrunnlag: List<Grunnlag.Fradragsgrunnlag>): Either<KunneIkkeLeggeTilFradrag, OpprettetRevurdering> {
        return oppdaterFradragInternal(fradragsgrunnlag).getOrHandle { return it.left() }
            .oppdaterInformasjonSomRevurderes(
                informasjonSomRevurderes = informasjonSomRevurderes.markerSomVurdert(
                    Revurderingsteg.Inntekt,
                ),
            ).right()
    }

    protected fun oppdaterFradragInternal(fradragsgrunnlag: List<Grunnlag.Fradragsgrunnlag>): Either<KunneIkkeLeggeTilFradrag, OpprettetRevurdering> {
        return oppdaterGrunnlag(
            grunnlagsdata = Grunnlagsdata.tryCreate(
                bosituasjon = grunnlagsdata.bosituasjon,
                fradragsgrunnlag = fradragsgrunnlag,
            ).getOrHandle { return KunneIkkeLeggeTilFradrag.Valideringsfeil(it).left() },
        ).right()
    }

    protected fun oppdaterBosituasjonOgMarkerSomVurdertInternal(bosituasjon: Grunnlag.Bosituasjon.Fullstendig): Either<KunneIkkeLeggeTilBosituasjon, OpprettetRevurdering> {
        val gjeldendeBosituasjon = tilRevurdering.behandling.grunnlagsdata.bosituasjon.singleOrThrow()
        return oppdaterGrunnlag(
            grunnlagsdata = Grunnlagsdata.tryCreate(
                fradragsgrunnlag = grunnlagsdata.fradragsgrunnlag,
                bosituasjon = nonEmptyListOf(bosituasjon),
            ).getOrHandle { return KunneIkkeLeggeTilBosituasjon.Valideringsfeil(it).left() },
        ).oppdaterInformasjonSomRevurderes(
            informasjonSomRevurderes = informasjonSomRevurderes.markerSomVurdert(Revurderingsteg.Bosituasjon).let {
                if (bosituasjon.harEndretEllerFjernetEktefelle(gjeldendeBosituasjon)) {
                    it.markerSomIkkeVurdert(Revurderingsteg.Inntekt).markerSomIkkeVurdert(Revurderingsteg.Formue)
                } else {
                    it
                }
            },
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
            forhåndsvarsel = forhåndsvarsel,
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
            forhåndsvarsel = forhåndsvarsel,
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
        )
    }

    open fun beregn(
        eksisterendeUtbetalinger: List<Utbetaling>,
        clock: Clock,
        gjeldendeVedtaksdata: GjeldendeVedtaksdata,
    ): Either<KunneIkkeBeregneRevurdering, BeregnetRevurdering> {
        val (revurdering, beregning) = BeregnRevurderingStrategyDecider(
            revurdering = this,
            gjeldendeVedtaksdata = gjeldendeVedtaksdata,
            clock = clock,
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
                forhåndsvarsel = revurdering.forhåndsvarsel,
                grunnlagsdata = revurdering.grunnlagsdata,
                vilkårsvurderinger = revurdering.vilkårsvurderinger,
                informasjonSomRevurderes = revurdering.informasjonSomRevurderes,
                attesteringer = revurdering.attesteringer,
                avkorting = revurdering.avkorting.håndter(),
            )

        fun innvilget(revurdering: OpprettetRevurdering, revurdertBeregning: Beregning): BeregnetRevurdering.Innvilget =
            BeregnetRevurdering.Innvilget(
                tilRevurdering = revurdering.tilRevurdering,
                id = revurdering.id,
                periode = revurdering.periode,
                opprettet = revurdering.opprettet,
                beregning = revurdertBeregning,
                saksbehandler = revurdering.saksbehandler,
                oppgaveId = revurdering.oppgaveId,
                fritekstTilBrev = revurdering.fritekstTilBrev,
                revurderingsårsak = revurdering.revurderingsårsak,
                forhåndsvarsel = revurdering.forhåndsvarsel,
                grunnlagsdata = revurdering.grunnlagsdata,
                vilkårsvurderinger = revurdering.vilkårsvurderinger,
                informasjonSomRevurderes = revurdering.informasjonSomRevurderes,
                attesteringer = revurdering.attesteringer,
                avkorting = revurdering.avkorting.håndter(),
            )

        fun ingenEndring(
            revurdering: OpprettetRevurdering,
            revurdertBeregning: Beregning,
        ): BeregnetRevurdering.IngenEndring =
            BeregnetRevurdering.IngenEndring(
                tilRevurdering = revurdering.tilRevurdering,
                id = revurdering.id,
                periode = revurdering.periode,
                opprettet = revurdering.opprettet,
                beregning = revurdertBeregning,
                saksbehandler = revurdering.saksbehandler,
                oppgaveId = revurdering.oppgaveId,
                fritekstTilBrev = revurdering.fritekstTilBrev,
                revurderingsårsak = revurdering.revurderingsårsak,
                forhåndsvarsel = revurdering.forhåndsvarsel,
                grunnlagsdata = revurdering.grunnlagsdata,
                vilkårsvurderinger = revurdering.vilkårsvurderinger,
                informasjonSomRevurderes = revurdering.informasjonSomRevurderes,
                attesteringer = revurdering.attesteringer,
                avkorting = revurdering.avkorting.håndter(),
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
    override val tilRevurdering: VedtakSomKanRevurderes,
    override val saksbehandler: Saksbehandler,
    override val oppgaveId: OppgaveId,
    override val fritekstTilBrev: String,
    override val revurderingsårsak: Revurderingsårsak,
    override val forhåndsvarsel: Forhåndsvarsel?,
    override val grunnlagsdata: Grunnlagsdata,
    override val vilkårsvurderinger: Vilkårsvurderinger.Revurdering,
    override val informasjonSomRevurderes: InformasjonSomRevurderes,
    override val attesteringer: Attesteringshistorikk = Attesteringshistorikk.empty(),
    override val avkorting: AvkortingVedRevurdering.Uhåndtert,
) : Revurdering() {

    override fun accept(visitor: RevurderingVisitor) {
        visitor.visit(this)
    }

    override fun oppdaterUføreOgMarkerSomVurdert(
        uføre: Vilkår.Uførhet.Vurdert,
    ) = oppdaterUføreOgMarkerSomVurdertInternal(uføre)

    override fun oppdaterUtenlandsoppholdOgMarkerSomVurdert(
        utenlandsopphold: UtenlandsoppholdVilkår.Vurdert,
    ) = oppdaterUtenlandsoppholdOgMarkerSomVurdertInternal(utenlandsopphold)

    override fun oppdaterFormueOgMarkerSomVurdert(formue: Vilkår.Formue.Vurdert) =
        oppdaterFormueOgMarkerSomVurdertInternal(formue)

    override fun oppdaterFradragOgMarkerSomVurdert(fradragsgrunnlag: List<Grunnlag.Fradragsgrunnlag>) =
        oppdaterFradragOgMarkerSomVurdertInternal(fradragsgrunnlag)

    override fun oppdaterBosituasjonOgMarkerSomVurdert(bosituasjon: Grunnlag.Bosituasjon.Fullstendig) =
        oppdaterBosituasjonOgMarkerSomVurdertInternal(bosituasjon)

    fun oppdaterInformasjonSomRevurderes(informasjonSomRevurderes: InformasjonSomRevurderes): OpprettetRevurdering {
        return copy(informasjonSomRevurderes = informasjonSomRevurderes)
    }

    override fun oppdaterFradrag(fradragsgrunnlag: List<Grunnlag.Fradragsgrunnlag>): Either<KunneIkkeLeggeTilFradrag, OpprettetRevurdering> {
        return oppdaterFradragInternal(fradragsgrunnlag)
    }

    fun oppdater(
        periode: Periode,
        revurderingsårsak: Revurderingsårsak,
        grunnlagsdata: Grunnlagsdata,
        vilkårsvurderinger: Vilkårsvurderinger.Revurdering,
        informasjonSomRevurderes: InformasjonSomRevurderes,
        tilRevurdering: VedtakSomKanRevurderes,
        avkorting: AvkortingVedRevurdering.Uhåndtert,
    ): OpprettetRevurdering = this.copy(
        periode = periode,
        revurderingsårsak = revurderingsårsak,
        // TODO jah: Bytt til Either (modellen bør passe på dette selv og ikke la ansvaret ligge bredt i servicen)
        forhåndsvarsel = if (revurderingsårsak.årsak == Revurderingsårsak.Årsak.REGULER_GRUNNBELØP) Forhåndsvarsel.Ferdigbehandlet.SkalIkkeForhåndsvarsles else null,
        grunnlagsdata = grunnlagsdata,
        vilkårsvurderinger = vilkårsvurderinger,
        informasjonSomRevurderes = informasjonSomRevurderes,
        tilRevurdering = tilRevurdering,
        avkorting = avkorting,
    )
}

sealed class BeregnetRevurdering : Revurdering() {
    abstract val beregning: Beregning
    abstract override val avkorting: AvkortingVedRevurdering.DelvisHåndtert

    override fun oppdaterUføreOgMarkerSomVurdert(
        uføre: Vilkår.Uførhet.Vurdert,
    ) = oppdaterUføreOgMarkerSomVurdertInternal(uføre)

    override fun oppdaterUtenlandsoppholdOgMarkerSomVurdert(
        utenlandsopphold: UtenlandsoppholdVilkår.Vurdert,
    ) = oppdaterUtenlandsoppholdOgMarkerSomVurdertInternal(utenlandsopphold)

    override fun oppdaterFormueOgMarkerSomVurdert(formue: Vilkår.Formue.Vurdert) =
        oppdaterFormueOgMarkerSomVurdertInternal(formue)

    override fun oppdaterFradragOgMarkerSomVurdert(fradragsgrunnlag: List<Grunnlag.Fradragsgrunnlag>) =
        oppdaterFradragOgMarkerSomVurdertInternal(fradragsgrunnlag)

    override fun oppdaterFradrag(fradragsgrunnlag: List<Grunnlag.Fradragsgrunnlag>): Either<KunneIkkeLeggeTilFradrag, OpprettetRevurdering> {
        return oppdaterFradragInternal(fradragsgrunnlag)
    }

    override fun oppdaterBosituasjonOgMarkerSomVurdert(bosituasjon: Grunnlag.Bosituasjon.Fullstendig) =
        oppdaterBosituasjonOgMarkerSomVurdertInternal(bosituasjon)

    fun oppdater(
        periode: Periode,
        revurderingsårsak: Revurderingsårsak,
        grunnlagsdata: Grunnlagsdata,
        vilkårsvurderinger: Vilkårsvurderinger.Revurdering,
        informasjonSomRevurderes: InformasjonSomRevurderes,
        tilRevurdering: VedtakSomKanRevurderes,
        avkorting: AvkortingVedRevurdering.Uhåndtert,
    ) = OpprettetRevurdering(
        id = id,
        periode = periode,
        opprettet = opprettet,
        tilRevurdering = tilRevurdering,
        saksbehandler = saksbehandler,
        oppgaveId = oppgaveId,
        fritekstTilBrev = fritekstTilBrev,
        revurderingsårsak = revurderingsårsak,
        // TODO jah: Bytt til Either (modellen bør passe på dette selv og ikke la ansvaret ligge bredt i servicen)
        forhåndsvarsel = if (revurderingsårsak.årsak == Revurderingsårsak.Årsak.REGULER_GRUNNBELØP) Forhåndsvarsel.Ferdigbehandlet.SkalIkkeForhåndsvarsles else null,
        grunnlagsdata = grunnlagsdata,
        vilkårsvurderinger = vilkårsvurderinger,
        informasjonSomRevurderes = informasjonSomRevurderes,
        attesteringer = attesteringer,
        avkorting = avkorting,
    )

    data class Innvilget(
        override val id: UUID,
        override val periode: Periode,
        override val opprettet: Tidspunkt,
        override val tilRevurdering: VedtakSomKanRevurderes,
        override val saksbehandler: Saksbehandler,
        override val beregning: Beregning,
        override val oppgaveId: OppgaveId,
        override val fritekstTilBrev: String,
        override val revurderingsårsak: Revurderingsårsak,
        override val forhåndsvarsel: Forhåndsvarsel?,
        override val grunnlagsdata: Grunnlagsdata,
        override val vilkårsvurderinger: Vilkårsvurderinger.Revurdering,
        override val informasjonSomRevurderes: InformasjonSomRevurderes,
        override val attesteringer: Attesteringshistorikk,
        override val avkorting: AvkortingVedRevurdering.DelvisHåndtert,
    ) : BeregnetRevurdering() {
        override fun accept(visitor: RevurderingVisitor) {
            visitor.visit(this)
        }

        fun toSimulert(simulering: Simulering) = SimulertRevurdering.Innvilget(
            id = id,
            periode = periode,
            opprettet = opprettet,
            tilRevurdering = tilRevurdering,
            beregning = beregning,
            simulering = simulering,
            saksbehandler = saksbehandler,
            oppgaveId = oppgaveId,
            fritekstTilBrev = fritekstTilBrev,
            revurderingsårsak = revurderingsårsak,
            forhåndsvarsel = forhåndsvarsel,
            grunnlagsdata = grunnlagsdata,
            vilkårsvurderinger = vilkårsvurderinger,
            informasjonSomRevurderes = informasjonSomRevurderes,
            attesteringer = attesteringer,
            avkorting = avkorting.håndter(),
        )
    }

    data class IngenEndring(
        override val id: UUID,
        override val periode: Periode,
        override val opprettet: Tidspunkt,
        override val tilRevurdering: VedtakSomKanRevurderes,
        override val saksbehandler: Saksbehandler,
        override val beregning: Beregning,
        override val oppgaveId: OppgaveId,
        override val fritekstTilBrev: String,
        override val revurderingsårsak: Revurderingsårsak,
        override val forhåndsvarsel: Forhåndsvarsel?,
        override val grunnlagsdata: Grunnlagsdata,
        override val vilkårsvurderinger: Vilkårsvurderinger.Revurdering,
        override val informasjonSomRevurderes: InformasjonSomRevurderes,
        override val attesteringer: Attesteringshistorikk,
        override val avkorting: AvkortingVedRevurdering.DelvisHåndtert,
    ) : BeregnetRevurdering() {

        fun tilAttestering(
            attesteringsoppgaveId: OppgaveId,
            saksbehandler: Saksbehandler,
            fritekstTilBrev: String,
            skalFøreTilUtsendingAvVedtaksbrev: Boolean,
        ) = RevurderingTilAttestering.IngenEndring(
            id = id,
            periode = periode,
            opprettet = opprettet,
            tilRevurdering = tilRevurdering,
            saksbehandler = saksbehandler,
            beregning = beregning,
            oppgaveId = attesteringsoppgaveId,
            fritekstTilBrev = fritekstTilBrev,
            revurderingsårsak = revurderingsårsak,
            skalFøreTilUtsendingAvVedtaksbrev = skalFøreTilUtsendingAvVedtaksbrev,
            forhåndsvarsel = forhåndsvarsel,
            grunnlagsdata = grunnlagsdata,
            vilkårsvurderinger = vilkårsvurderinger,
            informasjonSomRevurderes = informasjonSomRevurderes,
            attesteringer = attesteringer,
            avkorting = avkorting.håndter(),
        )

        override fun accept(visitor: RevurderingVisitor) {
            visitor.visit(this)
        }
    }

    data class Opphørt(
        override val id: UUID,
        override val periode: Periode,
        override val opprettet: Tidspunkt,
        override val tilRevurdering: VedtakSomKanRevurderes,
        override val saksbehandler: Saksbehandler,
        override val beregning: Beregning,
        override val oppgaveId: OppgaveId,
        override val fritekstTilBrev: String,
        override val revurderingsårsak: Revurderingsårsak,
        override val forhåndsvarsel: Forhåndsvarsel?,
        override val grunnlagsdata: Grunnlagsdata,
        override val vilkårsvurderinger: Vilkårsvurderinger.Revurdering,
        override val informasjonSomRevurderes: InformasjonSomRevurderes,
        override val attesteringer: Attesteringshistorikk,
        override val avkorting: AvkortingVedRevurdering.DelvisHåndtert,
    ) : BeregnetRevurdering() {
        fun toSimulert(simuler: (sakId: UUID, saksbehandler: NavIdentBruker, opphørsdato: LocalDate) -> Either<SimuleringFeilet, Utbetaling.SimulertUtbetaling>): Either<SimuleringFeilet, SimulertRevurdering.Opphørt> {
            val (simulertUtbetaling, håndtertAvkorting) = simuler(sakId, saksbehandler, periode.fraOgMed)
                .getOrHandle { return it.left() }
                .let { simulering ->
                    when (val avkortingsvarsel = lagAvkortingsvarsel(simulering)) {
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
                            val simuleringMedNyOpphørsdato = simuler(
                                sakId,
                                saksbehandler,
                                OpphørsdatoForUtbetalinger(
                                    revurdering = this,
                                    avkortingsvarsel = avkortingsvarsel,
                                ).value,
                            ).getOrHandle { return it.left() }

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
                forhåndsvarsel = forhåndsvarsel,
                grunnlagsdata = grunnlagsdata,
                vilkårsvurderinger = vilkårsvurderinger,
                informasjonSomRevurderes = informasjonSomRevurderes,
                attesteringer = attesteringer,
                avkorting = håndtertAvkorting,
            ).right()
        }

        private fun lagAvkortingsvarsel(simulertUtbetaling: Utbetaling.SimulertUtbetaling): Avkortingsvarsel {
            return when (simulertUtbetaling.simulering.harFeilutbetalinger()) {
                true -> {
                    when (val vilkårsvurdering = vilkårsvurderinger.resultat) {
                        is Vilkårsvurderingsresultat.Avslag -> {
                            when (vilkårsvurdering.erNøyaktigÅrsak(Inngangsvilkår.Utenlandsopphold)) {
                                true -> {
                                    Avkortingsvarsel.Utenlandsopphold.Opprettet(
                                        sakId = this.sakId,
                                        revurderingId = this.id,
                                        simulering = simulertUtbetaling.simulering,
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
    }
}

sealed class SimulertRevurdering : Revurdering() {

    abstract val beregning: Beregning
    abstract val simulering: Simulering
    abstract override val forhåndsvarsel: Forhåndsvarsel?
    abstract override val grunnlagsdata: Grunnlagsdata

    abstract override fun accept(visitor: RevurderingVisitor)

    fun harSimuleringFeilutbetaling() = simulering.harFeilutbetalinger()

    abstract fun prøvOvergangTilSkalIkkeForhåndsvarsles(): Either<Forhåndsvarsel.UgyldigTilstandsovergang, SimulertRevurdering>

    abstract fun prøvOvergangTilSendt(): Either<Forhåndsvarsel.UgyldigTilstandsovergang, SimulertRevurdering>

    abstract fun prøvOvergangTilAvsluttet(
        begrunnelse: String,
    ): Either<Forhåndsvarsel.UgyldigTilstandsovergang, SimulertRevurdering>

    abstract fun prøvOvergangTilEndreGrunnlaget(
        begrunnelse: String,
    ): Either<Forhåndsvarsel.UgyldigTilstandsovergang, SimulertRevurdering>

    abstract fun prøvOvergangTilFortsettMedSammeGrunnlag(
        begrunnelse: String,
    ): Either<Forhåndsvarsel.UgyldigTilstandsovergang, SimulertRevurdering>

    override fun oppdaterUføreOgMarkerSomVurdert(
        uføre: Vilkår.Uførhet.Vurdert,
    ) = oppdaterUføreOgMarkerSomVurdertInternal(uføre)

    override fun oppdaterUtenlandsoppholdOgMarkerSomVurdert(
        utenlandsopphold: UtenlandsoppholdVilkår.Vurdert,
    ) = oppdaterUtenlandsoppholdOgMarkerSomVurdertInternal(utenlandsopphold)

    override fun oppdaterFormueOgMarkerSomVurdert(formue: Vilkår.Formue.Vurdert) =
        oppdaterFormueOgMarkerSomVurdertInternal(formue)

    override fun oppdaterFradragOgMarkerSomVurdert(fradragsgrunnlag: List<Grunnlag.Fradragsgrunnlag>) =
        oppdaterFradragOgMarkerSomVurdertInternal(fradragsgrunnlag)

    override fun oppdaterBosituasjonOgMarkerSomVurdert(bosituasjon: Grunnlag.Bosituasjon.Fullstendig) =
        oppdaterBosituasjonOgMarkerSomVurdertInternal(bosituasjon)

    override fun oppdaterFradrag(fradragsgrunnlag: List<Grunnlag.Fradragsgrunnlag>): Either<KunneIkkeLeggeTilFradrag, OpprettetRevurdering> {
        return oppdaterFradragInternal(fradragsgrunnlag)
    }

    object ForhåndsvarslingErIkkeFerdigbehandlet

    data class Innvilget(
        override val id: UUID,
        override val periode: Periode,
        override val opprettet: Tidspunkt,
        override val tilRevurdering: VedtakSomKanRevurderes,
        override val saksbehandler: Saksbehandler,
        override val oppgaveId: OppgaveId,
        override val revurderingsårsak: Revurderingsårsak,
        override val fritekstTilBrev: String,
        override val beregning: Beregning,
        override val simulering: Simulering,
        override val forhåndsvarsel: Forhåndsvarsel?,
        override val grunnlagsdata: Grunnlagsdata,
        override val vilkårsvurderinger: Vilkårsvurderinger.Revurdering,
        override val informasjonSomRevurderes: InformasjonSomRevurderes,
        override val attesteringer: Attesteringshistorikk,
        override val avkorting: AvkortingVedRevurdering.Håndtert,
    ) : SimulertRevurdering() {
        override fun accept(visitor: RevurderingVisitor) {
            visitor.visit(this)
        }

        override fun prøvOvergangTilSkalIkkeForhåndsvarsles(): Either<Forhåndsvarsel.UgyldigTilstandsovergang, Innvilget> {
            return forhåndsvarsel.prøvOvergangTilSkalIkkeForhåndsvarsles().map { this.copy(forhåndsvarsel = it) }
        }

        override fun prøvOvergangTilSendt(): Either<Forhåndsvarsel.UgyldigTilstandsovergang, Innvilget> {
            return forhåndsvarsel.prøvOvergangTilSendt().map { this.copy(forhåndsvarsel = it) }
        }

        override fun prøvOvergangTilAvsluttet(
            begrunnelse: String,
        ): Either<Forhåndsvarsel.UgyldigTilstandsovergang, Innvilget> {
            return forhåndsvarsel.prøvOvergangTilAvsluttet(begrunnelse).map { this.copy(forhåndsvarsel = it) }
        }

        override fun prøvOvergangTilEndreGrunnlaget(
            begrunnelse: String,
        ): Either<Forhåndsvarsel.UgyldigTilstandsovergang, Innvilget> {
            return forhåndsvarsel.prøvOvergangTilEndreGrunnlaget(begrunnelse).map { this.copy(forhåndsvarsel = it) }
        }

        override fun prøvOvergangTilFortsettMedSammeGrunnlag(
            begrunnelse: String,
        ): Either<Forhåndsvarsel.UgyldigTilstandsovergang, Innvilget> {
            return forhåndsvarsel.prøvOvergangTilFortsettMedSammeGrunnlag(begrunnelse)
                .map { this.copy(forhåndsvarsel = it) }
        }

        fun tilAttestering(
            attesteringsoppgaveId: OppgaveId,
            saksbehandler: Saksbehandler,
            fritekstTilBrev: String,
        ): Either<ForhåndsvarslingErIkkeFerdigbehandlet, RevurderingTilAttestering.Innvilget> {
            return when (val f = forhåndsvarsel) {
                null,
                is Forhåndsvarsel.UnderBehandling,
                -> ForhåndsvarslingErIkkeFerdigbehandlet.left()
                is Forhåndsvarsel.Ferdigbehandlet -> RevurderingTilAttestering.Innvilget(
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
                    forhåndsvarsel = f,
                    grunnlagsdata = grunnlagsdata,
                    vilkårsvurderinger = vilkårsvurderinger,
                    informasjonSomRevurderes = informasjonSomRevurderes,
                    attesteringer = attesteringer,
                    avkorting = avkorting,
                ).right()
            }
        }
    }

    data class Opphørt(
        override val id: UUID,
        override val periode: Periode,
        override val opprettet: Tidspunkt,
        override val tilRevurdering: VedtakSomKanRevurderes,
        override val saksbehandler: Saksbehandler,
        override val oppgaveId: OppgaveId,
        override val revurderingsårsak: Revurderingsårsak,
        override val fritekstTilBrev: String,
        override val beregning: Beregning,
        override val simulering: Simulering,
        override val forhåndsvarsel: Forhåndsvarsel?,
        override val grunnlagsdata: Grunnlagsdata,
        override val vilkårsvurderinger: Vilkårsvurderinger.Revurdering,
        override val informasjonSomRevurderes: InformasjonSomRevurderes,
        override val attesteringer: Attesteringshistorikk,
        override val avkorting: AvkortingVedRevurdering.Håndtert,
    ) : SimulertRevurdering() {

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

        override fun prøvOvergangTilSkalIkkeForhåndsvarsles(): Either<Forhåndsvarsel.UgyldigTilstandsovergang, Opphørt> {
            return forhåndsvarsel.prøvOvergangTilSkalIkkeForhåndsvarsles().map { this.copy(forhåndsvarsel = it) }
        }

        override fun prøvOvergangTilSendt(): Either<Forhåndsvarsel.UgyldigTilstandsovergang, Opphørt> {
            return forhåndsvarsel.prøvOvergangTilSendt().map { this.copy(forhåndsvarsel = it) }
        }

        override fun prøvOvergangTilAvsluttet(
            begrunnelse: String,
        ): Either<Forhåndsvarsel.UgyldigTilstandsovergang, Opphørt> {
            return forhåndsvarsel.prøvOvergangTilAvsluttet(begrunnelse).map { this.copy(forhåndsvarsel = it) }
        }

        override fun prøvOvergangTilEndreGrunnlaget(
            begrunnelse: String,
        ): Either<Forhåndsvarsel.UgyldigTilstandsovergang, Opphørt> {
            return forhåndsvarsel.prøvOvergangTilEndreGrunnlaget(begrunnelse).map { this.copy(forhåndsvarsel = it) }
        }

        override fun prøvOvergangTilFortsettMedSammeGrunnlag(
            begrunnelse: String,
        ): Either<Forhåndsvarsel.UgyldigTilstandsovergang, Opphørt> {
            return forhåndsvarsel.prøvOvergangTilFortsettMedSammeGrunnlag(begrunnelse)
                .map { this.copy(forhåndsvarsel = it) }
        }

        sealed interface KanIkkeSendeOpphørtRevurderingTilAttestering {
            object KanIkkeSendeEnOpphørtGReguleringTilAttestering : KanIkkeSendeOpphørtRevurderingTilAttestering
            object ForhåndsvarslingErIkkeFerdigbehandlet : KanIkkeSendeOpphørtRevurderingTilAttestering
        }

        fun tilAttestering(
            attesteringsoppgaveId: OppgaveId,
            saksbehandler: Saksbehandler,
            fritekstTilBrev: String,
        ): Either<KanIkkeSendeOpphørtRevurderingTilAttestering, RevurderingTilAttestering.Opphørt> {
            if (revurderingsårsak.årsak == Revurderingsårsak.Årsak.REGULER_GRUNNBELØP) {
                return KanIkkeSendeOpphørtRevurderingTilAttestering.KanIkkeSendeEnOpphørtGReguleringTilAttestering.left()
            }
            return when (val f = forhåndsvarsel) {
                null,
                is Forhåndsvarsel.UnderBehandling,
                -> KanIkkeSendeOpphørtRevurderingTilAttestering.ForhåndsvarslingErIkkeFerdigbehandlet.left()
                is Forhåndsvarsel.Ferdigbehandlet -> RevurderingTilAttestering.Opphørt(
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
                    forhåndsvarsel = f,
                    grunnlagsdata = grunnlagsdata,
                    vilkårsvurderinger = vilkårsvurderinger,
                    informasjonSomRevurderes = informasjonSomRevurderes,
                    attesteringer = attesteringer,
                    avkorting = avkorting,
                ).right()
            }
        }
    }

    fun oppdater(
        periode: Periode,
        revurderingsårsak: Revurderingsårsak,
        grunnlagsdata: Grunnlagsdata,
        vilkårsvurderinger: Vilkårsvurderinger.Revurdering,
        informasjonSomRevurderes: InformasjonSomRevurderes,
        tilRevurdering: VedtakSomKanRevurderes,
        avkorting: AvkortingVedRevurdering.Uhåndtert,
    ) = OpprettetRevurdering(
        id = id,
        periode = periode,
        opprettet = opprettet,
        tilRevurdering = tilRevurdering,
        saksbehandler = saksbehandler,
        oppgaveId = oppgaveId,
        fritekstTilBrev = fritekstTilBrev,
        revurderingsårsak = revurderingsårsak,
        // TODO jah: Bytt til Either (modellen bør passe på dette selv og ikke la ansvaret ligge bredt i servicen)
        forhåndsvarsel = if (revurderingsårsak.årsak == Revurderingsårsak.Årsak.REGULER_GRUNNBELØP) Forhåndsvarsel.Ferdigbehandlet.SkalIkkeForhåndsvarsles else null,
        grunnlagsdata = grunnlagsdata,
        vilkårsvurderinger = vilkårsvurderinger,
        informasjonSomRevurderes = informasjonSomRevurderes,
        attesteringer = attesteringer,
        avkorting = avkorting,
    )
}

sealed class RevurderingTilAttestering : Revurdering() {
    abstract val beregning: Beregning
    abstract override val grunnlagsdata: Grunnlagsdata

    abstract override fun accept(visitor: RevurderingVisitor)

    // TODO jah: Denne settes default til true i DB, men bør styres av domenet istedet. Se også TODO i RevurderingPostgresRepo.kt
    abstract val skalFøreTilUtsendingAvVedtaksbrev: Boolean

    data class Innvilget(
        override val id: UUID,
        override val periode: Periode,
        override val opprettet: Tidspunkt,
        override val tilRevurdering: VedtakSomKanRevurderes,
        override val saksbehandler: Saksbehandler,
        override val oppgaveId: OppgaveId,
        override val fritekstTilBrev: String,
        override val revurderingsårsak: Revurderingsårsak,
        override val beregning: Beregning,
        val simulering: Simulering,
        override val forhåndsvarsel: Forhåndsvarsel.Ferdigbehandlet,
        override val grunnlagsdata: Grunnlagsdata,
        override val vilkårsvurderinger: Vilkårsvurderinger.Revurdering,
        override val informasjonSomRevurderes: InformasjonSomRevurderes,
        override val attesteringer: Attesteringshistorikk,
        override val avkorting: AvkortingVedRevurdering.Håndtert,
    ) : RevurderingTilAttestering() {

        override fun accept(visitor: RevurderingVisitor) {
            visitor.visit(this)
        }

        override val skalFøreTilUtsendingAvVedtaksbrev = true

        fun tilIverksatt(
            attestant: NavIdentBruker.Attestant,
            hentOpprinneligAvkorting: (id: UUID) -> Avkortingsvarsel?,
            clock: Clock
        ): Either<KunneIkkeIverksetteRevurdering, IverksattRevurdering.Innvilget> = validerTilIverksettOvergang(
            attestant = attestant,
            hentOpprinneligAvkorting = hentOpprinneligAvkorting,
            saksbehandler = saksbehandler,
            avkorting = avkorting
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
                forhåndsvarsel = forhåndsvarsel,
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
            )
        }
    }

    data class Opphørt(
        override val id: UUID,
        override val periode: Periode,
        override val opprettet: Tidspunkt,
        override val tilRevurdering: VedtakSomKanRevurderes,
        override val saksbehandler: Saksbehandler,
        override val oppgaveId: OppgaveId,
        override val fritekstTilBrev: String,
        override val revurderingsårsak: Revurderingsårsak,
        override val beregning: Beregning,
        val simulering: Simulering,
        override val forhåndsvarsel: Forhåndsvarsel.Ferdigbehandlet,
        override val grunnlagsdata: Grunnlagsdata,
        override val vilkårsvurderinger: Vilkårsvurderinger.Revurdering,
        override val informasjonSomRevurderes: InformasjonSomRevurderes,
        override val attesteringer: Attesteringshistorikk,
        override val avkorting: AvkortingVedRevurdering.Håndtert,
    ) : RevurderingTilAttestering() {
        override fun accept(visitor: RevurderingVisitor) {
            visitor.visit(this)
        }

        override val skalFøreTilUtsendingAvVedtaksbrev = true
        val opphørsdatoForUtbetalinger = OpphørsdatoForUtbetalinger(this).value

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

        fun tilIverksatt(
            attestant: NavIdentBruker.Attestant,
            hentOpprinneligAvkorting: (id: UUID) -> Avkortingsvarsel?,
            clock: Clock
        ): Either<KunneIkkeIverksetteRevurdering, IverksattRevurdering.Opphørt> = validerTilIverksettOvergang(
            attestant = attestant,
            hentOpprinneligAvkorting = hentOpprinneligAvkorting,
            saksbehandler = saksbehandler,
            avkorting = avkorting
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
                forhåndsvarsel = forhåndsvarsel,
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
            )
        }
    }

    data class IngenEndring(
        override val id: UUID,
        override val periode: Periode,
        override val opprettet: Tidspunkt,
        override val tilRevurdering: VedtakSomKanRevurderes,
        override val saksbehandler: Saksbehandler,
        override val beregning: Beregning,
        override val oppgaveId: OppgaveId,
        override val fritekstTilBrev: String,
        override val revurderingsårsak: Revurderingsårsak,
        override val skalFøreTilUtsendingAvVedtaksbrev: Boolean,
        override val forhåndsvarsel: Forhåndsvarsel?,
        override val grunnlagsdata: Grunnlagsdata,
        override val vilkårsvurderinger: Vilkårsvurderinger.Revurdering,
        override val informasjonSomRevurderes: InformasjonSomRevurderes,
        override val attesteringer: Attesteringshistorikk,
        override val avkorting: AvkortingVedRevurdering.Håndtert,
    ) : RevurderingTilAttestering() {

        override fun accept(visitor: RevurderingVisitor) {
            visitor.visit(this)
        }

        fun tilIverksatt(
            attestant: NavIdentBruker.Attestant,
            clock: Clock = Clock.systemUTC(),
        ): Either<KunneIkkeIverksetteRevurdering, IverksattRevurdering.IngenEndring> {

            if (saksbehandler.navIdent == attestant.navIdent) {
                return KunneIkkeIverksetteRevurdering.AttestantOgSaksbehandlerKanIkkeVæreSammePerson.left()
            }
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
                forhåndsvarsel = forhåndsvarsel,
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
            ).right()
        }
    }

    override fun beregn(
        eksisterendeUtbetalinger: List<Utbetaling>,
        clock: Clock,
        gjeldendeVedtaksdata: GjeldendeVedtaksdata,
    ): Either<KunneIkkeBeregneRevurdering, BeregnetRevurdering> {
        throw RuntimeException("Skal ikke kunne beregne når revurderingen er til attestering")
    }

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
                forhåndsvarsel = forhåndsvarsel,
                grunnlagsdata = grunnlagsdata,
                vilkårsvurderinger = vilkårsvurderinger,
                informasjonSomRevurderes = informasjonSomRevurderes,
                avkorting = avkorting,
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
                forhåndsvarsel = forhåndsvarsel,
                grunnlagsdata = grunnlagsdata,
                vilkårsvurderinger = vilkårsvurderinger,
                informasjonSomRevurderes = informasjonSomRevurderes,
                avkorting = avkorting,
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
                forhåndsvarsel = forhåndsvarsel,
                grunnlagsdata = grunnlagsdata,
                vilkårsvurderinger = vilkårsvurderinger,
                informasjonSomRevurderes = informasjonSomRevurderes,
                avkorting = avkorting,
            )
        }
    }
}

sealed class IverksattRevurdering : Revurdering() {
    abstract override val id: UUID
    abstract override val periode: Periode
    abstract override val opprettet: Tidspunkt
    abstract override val tilRevurdering: VedtakSomKanRevurderes
    abstract override val saksbehandler: Saksbehandler
    abstract override val oppgaveId: OppgaveId
    abstract override val fritekstTilBrev: String
    abstract override val revurderingsårsak: Revurderingsårsak
    abstract val beregning: Beregning
    val attestering: Attestering
        get() = attesteringer.hentSisteAttestering()
    abstract override val avkorting: AvkortingVedRevurdering.Iverksatt

    abstract override fun accept(visitor: RevurderingVisitor)

    data class Innvilget(
        override val id: UUID,
        override val periode: Periode,
        override val opprettet: Tidspunkt,
        override val tilRevurdering: VedtakSomKanRevurderes,
        override val saksbehandler: Saksbehandler,
        override val oppgaveId: OppgaveId,
        override val fritekstTilBrev: String,
        override val revurderingsårsak: Revurderingsårsak,
        override val beregning: Beregning,
        override val forhåndsvarsel: Forhåndsvarsel.Ferdigbehandlet,
        val simulering: Simulering,
        override val grunnlagsdata: Grunnlagsdata,
        override val vilkårsvurderinger: Vilkårsvurderinger.Revurdering,
        override val informasjonSomRevurderes: InformasjonSomRevurderes,
        override val attesteringer: Attesteringshistorikk,
        override val avkorting: AvkortingVedRevurdering.Iverksatt,
    ) : IverksattRevurdering() {

        override fun accept(visitor: RevurderingVisitor) {
            visitor.visit(this)
        }
    }

    data class Opphørt(
        override val id: UUID,
        override val periode: Periode,
        override val opprettet: Tidspunkt,
        override val tilRevurdering: VedtakSomKanRevurderes,
        override val saksbehandler: Saksbehandler,
        override val oppgaveId: OppgaveId,
        override val fritekstTilBrev: String,
        override val revurderingsårsak: Revurderingsårsak,
        override val beregning: Beregning,
        val simulering: Simulering,
        override val forhåndsvarsel: Forhåndsvarsel.Ferdigbehandlet,
        override val grunnlagsdata: Grunnlagsdata,
        override val vilkårsvurderinger: Vilkårsvurderinger.Revurdering,
        override val informasjonSomRevurderes: InformasjonSomRevurderes,
        override val attesteringer: Attesteringshistorikk,
        override val avkorting: AvkortingVedRevurdering.Iverksatt,
    ) : IverksattRevurdering() {
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
    }

    data class IngenEndring(
        override val id: UUID,
        override val periode: Periode,
        override val opprettet: Tidspunkt,
        override val tilRevurdering: VedtakSomKanRevurderes,
        override val saksbehandler: Saksbehandler,
        override val beregning: Beregning,
        override val oppgaveId: OppgaveId,
        override val fritekstTilBrev: String,
        override val revurderingsårsak: Revurderingsårsak,
        val skalFøreTilUtsendingAvVedtaksbrev: Boolean,
        override val forhåndsvarsel: Forhåndsvarsel?,
        override val grunnlagsdata: Grunnlagsdata,
        override val vilkårsvurderinger: Vilkårsvurderinger.Revurdering,
        override val informasjonSomRevurderes: InformasjonSomRevurderes,
        override val attesteringer: Attesteringshistorikk,
        override val avkorting: AvkortingVedRevurdering.Iverksatt,
    ) : IverksattRevurdering() {

        override fun accept(visitor: RevurderingVisitor) {
            visitor.visit(this)
        }
    }

    override fun beregn(
        eksisterendeUtbetalinger: List<Utbetaling>,
        clock: Clock,
        gjeldendeVedtaksdata: GjeldendeVedtaksdata,
    ) =
        throw RuntimeException("Skal ikke kunne beregne når revurderingen er iverksatt")
}

sealed class UnderkjentRevurdering : Revurdering() {
    abstract val beregning: Beregning
    abstract override val attesteringer: Attesteringshistorikk
    abstract override val grunnlagsdata: Grunnlagsdata
    abstract override val vilkårsvurderinger: Vilkårsvurderinger.Revurdering
    val attestering: Attestering.Underkjent
        get() = attesteringer.hentSisteAttestering() as Attestering.Underkjent

    abstract override fun accept(visitor: RevurderingVisitor)

    override fun oppdaterUføreOgMarkerSomVurdert(
        uføre: Vilkår.Uførhet.Vurdert,
    ) = oppdaterUføreOgMarkerSomVurdertInternal(uføre)

    override fun oppdaterUtenlandsoppholdOgMarkerSomVurdert(
        utenlandsopphold: UtenlandsoppholdVilkår.Vurdert,
    ) = oppdaterUtenlandsoppholdOgMarkerSomVurdertInternal(utenlandsopphold)

    override fun oppdaterFormueOgMarkerSomVurdert(formue: Vilkår.Formue.Vurdert) =
        oppdaterFormueOgMarkerSomVurdertInternal(formue)

    override fun oppdaterFradragOgMarkerSomVurdert(fradragsgrunnlag: List<Grunnlag.Fradragsgrunnlag>) =
        oppdaterFradragOgMarkerSomVurdertInternal(fradragsgrunnlag)

    override fun oppdaterBosituasjonOgMarkerSomVurdert(bosituasjon: Grunnlag.Bosituasjon.Fullstendig) =
        oppdaterBosituasjonOgMarkerSomVurdertInternal(bosituasjon)

    override fun oppdaterFradrag(fradragsgrunnlag: List<Grunnlag.Fradragsgrunnlag>): Either<KunneIkkeLeggeTilFradrag, OpprettetRevurdering> {
        return oppdaterFradragInternal(fradragsgrunnlag)
    }

    data class Innvilget(
        override val id: UUID,
        override val periode: Periode,
        override val opprettet: Tidspunkt,
        override val tilRevurdering: VedtakSomKanRevurderes,
        override val saksbehandler: Saksbehandler,
        override val oppgaveId: OppgaveId,
        override val fritekstTilBrev: String,
        override val revurderingsårsak: Revurderingsårsak,
        override val beregning: Beregning,
        override val attesteringer: Attesteringshistorikk,
        override val forhåndsvarsel: Forhåndsvarsel.Ferdigbehandlet,
        val simulering: Simulering,
        override val grunnlagsdata: Grunnlagsdata,
        override val vilkårsvurderinger: Vilkårsvurderinger.Revurdering,
        override val informasjonSomRevurderes: InformasjonSomRevurderes,
        override val avkorting: AvkortingVedRevurdering.Håndtert,
    ) : UnderkjentRevurdering() {
        override fun accept(visitor: RevurderingVisitor) {
            visitor.visit(this)
        }

        fun harSimuleringFeilutbetaling() = simulering.harFeilutbetalinger()

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
            forhåndsvarsel = forhåndsvarsel,
            grunnlagsdata = grunnlagsdata,
            vilkårsvurderinger = vilkårsvurderinger,
            informasjonSomRevurderes = informasjonSomRevurderes,
            attesteringer = attesteringer,
            avkorting = avkorting,
        )
    }

    data class Opphørt(
        override val id: UUID,
        override val periode: Periode,
        override val opprettet: Tidspunkt,
        override val tilRevurdering: VedtakSomKanRevurderes,
        override val saksbehandler: Saksbehandler,
        override val oppgaveId: OppgaveId,
        override val fritekstTilBrev: String,
        override val revurderingsårsak: Revurderingsårsak,
        override val beregning: Beregning,
        override val forhåndsvarsel: Forhåndsvarsel.Ferdigbehandlet,
        val simulering: Simulering,
        override val grunnlagsdata: Grunnlagsdata,
        override val vilkårsvurderinger: Vilkårsvurderinger.Revurdering,
        override val informasjonSomRevurderes: InformasjonSomRevurderes,
        override val attesteringer: Attesteringshistorikk,
        override val avkorting: AvkortingVedRevurdering.Håndtert,
    ) : UnderkjentRevurdering() {
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

        fun harSimuleringFeilutbetaling() = simulering.harFeilutbetalinger()

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
                    forhåndsvarsel = forhåndsvarsel,
                    grunnlagsdata = grunnlagsdata,
                    vilkårsvurderinger = vilkårsvurderinger,
                    informasjonSomRevurderes = informasjonSomRevurderes,
                    attesteringer = attesteringer,
                    avkorting = avkorting,
                ).right()
            }
        }
    }

    data class IngenEndring(
        override val id: UUID,
        override val periode: Periode,
        override val opprettet: Tidspunkt,
        override val tilRevurdering: VedtakSomKanRevurderes,
        override val saksbehandler: Saksbehandler,
        override val beregning: Beregning,
        override val oppgaveId: OppgaveId,
        override val fritekstTilBrev: String,
        override val revurderingsårsak: Revurderingsårsak,
        val skalFøreTilUtsendingAvVedtaksbrev: Boolean,
        override val forhåndsvarsel: Forhåndsvarsel?,
        override val grunnlagsdata: Grunnlagsdata,
        override val vilkårsvurderinger: Vilkårsvurderinger.Revurdering,
        override val informasjonSomRevurderes: InformasjonSomRevurderes,
        override val attesteringer: Attesteringshistorikk,
        override val avkorting: AvkortingVedRevurdering.Håndtert,
    ) : UnderkjentRevurdering() {

        override fun accept(visitor: RevurderingVisitor) {
            visitor.visit(this)
        }

        fun tilAttestering(
            oppgaveId: OppgaveId,
            saksbehandler: Saksbehandler,
            fritekstTilBrev: String,
            skalFøreTilUtsendingAvVedtaksbrev: Boolean,
        ) = RevurderingTilAttestering.IngenEndring(
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
            forhåndsvarsel = forhåndsvarsel,
            grunnlagsdata = grunnlagsdata,
            vilkårsvurderinger = vilkårsvurderinger,
            informasjonSomRevurderes = informasjonSomRevurderes,
            attesteringer = attesteringer,
            avkorting = avkorting,
        )
    }

    fun oppdater(
        periode: Periode,
        revurderingsårsak: Revurderingsårsak,
        grunnlagsdata: Grunnlagsdata,
        vilkårsvurderinger: Vilkårsvurderinger.Revurdering,
        informasjonSomRevurderes: InformasjonSomRevurderes,
        tilRevurdering: VedtakSomKanRevurderes,
        avkorting: AvkortingVedRevurdering.Uhåndtert,
    ) = OpprettetRevurdering(
        id = id,
        periode = periode,
        opprettet = opprettet,
        tilRevurdering = tilRevurdering,
        saksbehandler = saksbehandler,
        oppgaveId = oppgaveId,
        fritekstTilBrev = fritekstTilBrev,
        revurderingsårsak = revurderingsårsak,
        // TODO jah: Bytt til Either (modellen bør passe på dette selv og ikke la ansvaret ligge bredt i servicen)
        forhåndsvarsel = if (revurderingsårsak.årsak == Revurderingsårsak.Årsak.REGULER_GRUNNBELØP) Forhåndsvarsel.Ferdigbehandlet.SkalIkkeForhåndsvarsles else null,
        grunnlagsdata = grunnlagsdata,
        vilkårsvurderinger = vilkårsvurderinger,
        informasjonSomRevurderes = informasjonSomRevurderes,
        attesteringer = attesteringer,
        avkorting = avkorting,
    )
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
        is AvsluttetRevurdering -> copy(fritekst = fritekstTilBrev)
    }

enum class Vurderingstatus(val status: String) {
    Vurdert("Vurdert"),
    IkkeVurdert("IkkeVurdert")
}

enum class Revurderingsteg(val vilkår: String) {
    // BorOgOppholderSegINorge("BorOgOppholderSegINorge"),
    // Flyktning("Flyktning"),
    Formue("Formue"),

    // Oppholdstillatelse("Oppholdstillatelse"),
    // PersonligOppmøte("PersonligOppmøte"),
    Uførhet("Uførhet"),
    Bosituasjon("Bosituasjon"),

    // InnlagtPåInstitusjon("InnlagtPåInstitusjon"),
    Utenlandsopphold("Utenlandsopphold"),
    Inntekt("Inntekt"),
}

private fun validerTilIverksettOvergang(
    attestant: NavIdentBruker.Attestant,
    hentOpprinneligAvkorting: (id: UUID) -> Avkortingsvarsel?,
    saksbehandler: Saksbehandler,
    avkorting: AvkortingVedRevurdering.Håndtert
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
