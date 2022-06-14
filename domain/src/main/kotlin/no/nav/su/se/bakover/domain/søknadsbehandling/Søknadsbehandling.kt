package no.nav.su.se.bakover.domain.søknadsbehandling

import arrow.core.Either
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.nonEmptyListOf
import arrow.core.right
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.periode.inneholderAlle
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.Sakstype
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.avkorting.AvkortingVedSøknadsbehandling
import no.nav.su.se.bakover.domain.avkorting.Avkortingsplan
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.behandling.Attesteringshistorikk
import no.nav.su.se.bakover.domain.behandling.AvslagGrunnetBeregning
import no.nav.su.se.bakover.domain.behandling.BehandlingMedAttestering
import no.nav.su.se.bakover.domain.behandling.BehandlingMedOppgave
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.behandling.VurderAvslagGrunnetBeregning
import no.nav.su.se.bakover.domain.behandling.avslag.Avslagsgrunn
import no.nav.su.se.bakover.domain.behandling.avslag.Avslagsgrunn.Companion.toAvslagsgrunn
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.beregning.BeregningStrategyFactory
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag.Fradragsgrunnlag.Companion.perioder
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.grunnlag.GrunnlagsdataOgVilkårsvurderinger
import no.nav.su.se.bakover.domain.grunnlag.KunneIkkeLageGrunnlagsdata
import no.nav.su.se.bakover.domain.grunnlag.OpplysningspliktBeskrivelse
import no.nav.su.se.bakover.domain.grunnlag.Opplysningspliktgrunnlag
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingFeilet
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.satser.SatsFactory
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling.Vilkårsvurdert.Companion.opprett
import no.nav.su.se.bakover.domain.vilkår.FormuegrenserFactory
import no.nav.su.se.bakover.domain.vilkår.OpplysningspliktVilkår
import no.nav.su.se.bakover.domain.vilkår.PensjonsVilkår
import no.nav.su.se.bakover.domain.vilkår.UtenlandsoppholdVilkår
import no.nav.su.se.bakover.domain.vilkår.Vilkår
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderingsresultat
import no.nav.su.se.bakover.domain.vilkår.VurderingsperiodeOpplysningsplikt
import no.nav.su.se.bakover.domain.vilkår.inneholderAlle
import no.nav.su.se.bakover.domain.visitor.Visitable
import java.time.Clock
import java.util.UUID
import kotlin.reflect.KClass

sealed class Søknadsbehandling : BehandlingMedOppgave, BehandlingMedAttestering, Visitable<SøknadsbehandlingVisitor> {
    abstract val søknad: Søknad.Journalført.MedOppgave
    abstract val behandlingsinformasjon: Behandlingsinformasjon

    // TODO jah: Denne kan fjernes fra domenet og heller la mappingen ligge i infrastruktur-laget
    abstract val status: BehandlingsStatus
    abstract val stønadsperiode: Stønadsperiode?
    abstract override val grunnlagsdata: Grunnlagsdata
    abstract override val vilkårsvurderinger: Vilkårsvurderinger.Søknadsbehandling
    abstract override val attesteringer: Attesteringshistorikk
    abstract val avkorting: AvkortingVedSøknadsbehandling
    abstract val sakstype: Sakstype

    // TODO ia: fritekst bør flyttes ut av denne klassen og til et eget konsept (som også omfatter fritekst på revurderinger)
    abstract val fritekstTilBrev: String

    val erIverksatt: Boolean by lazy { this is Iverksatt.Avslag || this is Iverksatt.Innvilget }
    val erLukket: Boolean by lazy { this is LukketSøknadsbehandling }

    val grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderinger.Søknadsbehandling
        get() = GrunnlagsdataOgVilkårsvurderinger.Søknadsbehandling(
            grunnlagsdata = grunnlagsdata,
            vilkårsvurderinger = vilkårsvurderinger,
        )

    protected fun kastHvisGrunnlagsdataOgVilkårsvurderingerPeriodenOgBehandlingensPerioderErUlike() {
        if (grunnlagsdataOgVilkårsvurderinger.periode() == null) return
        if (grunnlagsdataOgVilkårsvurderinger.periode() != periode) {
            // Det er Søknadbehandling sin oppgave og vurdere om grunnlagsdataOgVilkårsvurderinger
            // sin periode tilsvarer søknadbehandlingens periode.
            throw IllegalArgumentException("Perioden til søknadsbehandlingen: $periode var ulik grunnlagene/vilkårsvurderingene sin periode: ${grunnlagsdataOgVilkårsvurderinger.periode()}")
        }
    }

    sealed class KunneIkkeLukkeSøknadsbehandling {
        object KanIkkeLukkeEnAlleredeLukketSøknadsbehandling : KunneIkkeLukkeSøknadsbehandling()
        object KanIkkeLukkeEnIverksattSøknadsbehandling : KunneIkkeLukkeSøknadsbehandling()
        object KanIkkeLukkeEnSøknadsbehandlingTilAttestering : KunneIkkeLukkeSøknadsbehandling()
    }

    fun lukkSøknadsbehandling(): Either<KunneIkkeLukkeSøknadsbehandling, LukketSøknadsbehandling> {
        return LukketSøknadsbehandling.tryCreate(this)
    }

    sealed class KunneIkkeLeggeTilFradragsgrunnlag {
        data class IkkeLovÅLeggeTilFradragIDenneStatusen(val status: KClass<out Søknadsbehandling>) :
            KunneIkkeLeggeTilFradragsgrunnlag()

        object GrunnlagetMåVæreInnenforBehandlingsperioden : KunneIkkeLeggeTilFradragsgrunnlag()
        data class KunneIkkeEndreFradragsgrunnlag(val feil: KunneIkkeLageGrunnlagsdata) :
            KunneIkkeLeggeTilFradragsgrunnlag()
    }

    sealed class KunneIkkeLeggeTilUtenlandsopphold {
        data class IkkeLovÅLeggeTilUtenlandsoppholdIDenneStatusen(
            val fra: KClass<out Søknadsbehandling>,
            val til: KClass<out Søknadsbehandling>,
        ) : KunneIkkeLeggeTilUtenlandsopphold()

        object VurderingsperiodeUtenforBehandlingsperiode : KunneIkkeLeggeTilUtenlandsopphold()
        object MåInneholdeKunEnVurderingsperiode : KunneIkkeLeggeTilUtenlandsopphold()
        object AlleVurderingsperioderMåHaSammeResultat : KunneIkkeLeggeTilUtenlandsopphold()
        object MåVurdereHelePerioden : KunneIkkeLeggeTilUtenlandsopphold()
    }

    sealed interface KunneIkkeLeggeTilFormuegrunnlag {
        data class UgyldigTilstand(
            val fra: KClass<out Søknadsbehandling>,
            val til: KClass<out Søknadsbehandling>,
        ) : KunneIkkeLeggeTilFormuegrunnlag
    }

    internal fun validerFradragsgrunnlag(fradragsgrunnlag: List<Grunnlag.Fradragsgrunnlag>): Either<KunneIkkeLeggeTilFradragsgrunnlag, Unit> {
        if (fradragsgrunnlag.isNotEmpty()) {
            if (!periode.inneholderAlle(fradragsgrunnlag.perioder())) {
                return KunneIkkeLeggeTilFradragsgrunnlag.GrunnlagetMåVæreInnenforBehandlingsperioden.left()
            }
        }
        return Unit.right()
    }

    open fun leggTilFradragsgrunnlag(
        fradragsgrunnlag: List<Grunnlag.Fradragsgrunnlag>,
        formuegrenserFactory: FormuegrenserFactory,
    ): Either<KunneIkkeLeggeTilFradragsgrunnlag, Vilkårsvurdert.Innvilget> =
        KunneIkkeLeggeTilFradragsgrunnlag.IkkeLovÅLeggeTilFradragIDenneStatusen(this::class).left()

    /**
     * TODO("bør vi skille på oppdatering og fullføring (ufullstendig vs fullstendig bosituasjon)")
     */
    fun oppdaterBosituasjon(
        bosituasjon: Grunnlag.Bosituasjon,
        clock: Clock,
    ): Either<KunneIkkeOppdatereBosituasjon, Vilkårsvurdert> {
        grunnlagsdataOgVilkårsvurderinger.oppdaterBosituasjon(listOf(bosituasjon)).let { grunnlagOgVilkår ->
            return when (this) {
                is Vilkårsvurdert -> tilVilkårsvurdert(
                    this.behandlingsinformasjon,
                    grunnlagOgVilkår,
                    clock,
                ).right()
                is Beregnet -> tilVilkårsvurdert(
                    this.behandlingsinformasjon,
                    grunnlagOgVilkår,
                    clock,
                ).right()
                is Simulert -> tilVilkårsvurdert(
                    this.behandlingsinformasjon,
                    grunnlagOgVilkår,
                    clock,
                ).right()
                is Underkjent -> tilVilkårsvurdert(
                    this.behandlingsinformasjon,
                    grunnlagOgVilkår,
                    clock,
                ).right()

                is TilAttestering,
                is LukketSøknadsbehandling,
                is Iverksatt,
                -> KunneIkkeOppdatereBosituasjon.UgyldigTilstand(this::class, Vilkårsvurdert::class).left()
            }
        }
    }

    sealed class KunneIkkeOppdatereBosituasjon {
        data class UgyldigTilstand(val fra: KClass<out Søknadsbehandling>, val til: KClass<out Vilkårsvurdert>) :
            KunneIkkeOppdatereBosituasjon()
    }

    open fun leggTilUtenlandsopphold(
        utenlandsopphold: UtenlandsoppholdVilkår.Vurdert,
        clock: Clock,
    ): Either<KunneIkkeLeggeTilUtenlandsopphold, Vilkårsvurdert> {
        return KunneIkkeLeggeTilUtenlandsopphold.IkkeLovÅLeggeTilUtenlandsoppholdIDenneStatusen(
            fra = this::class,
            til = Vilkårsvurdert::class,
        ).left()
    }

    open fun leggTilFormuevilkår(
        vilkår: Vilkår.Formue.Vurdert,
        clock: Clock,
    ): Either<KunneIkkeLeggeTilFormuegrunnlag, Vilkårsvurdert> {
        return KunneIkkeLeggeTilFormuegrunnlag.UgyldigTilstand(
            fra = this::class,
            til = Vilkårsvurdert::class,
        ).left()
    }

    open fun oppdaterStønadsperiode(
        oppdatertStønadsperiode: Stønadsperiode,
        clock: Clock,
        formuegrenserFactory: FormuegrenserFactory,
    ): Either<KunneIkkeOppdatereStønadsperiode, Vilkårsvurdert> {
        return KunneIkkeOppdatereStønadsperiode.UgyldigTilstand(this::class).left()
    }

    sealed class KunneIkkeOppdatereStønadsperiode {
        data class UgyldigTilstand(
            val fra: KClass<out Søknadsbehandling>,
            val til: KClass<out Vilkårsvurdert> = Vilkårsvurdert::class,
        ) : KunneIkkeOppdatereStønadsperiode()

        data class KunneIkkeOppdatereGrunnlagsdata(
            val feil: KunneIkkeLageGrunnlagsdata,
        ) : KunneIkkeOppdatereStønadsperiode()
    }

    open fun leggTilOpplysningspliktVilkår(
        opplysningspliktVilkår: OpplysningspliktVilkår.Vurdert,
        clock: Clock,
        formuegrenserFactory: FormuegrenserFactory,
    ): Either<KunneIkkeLeggeTilOpplysningsplikt, Vilkårsvurdert> {
        return KunneIkkeLeggeTilOpplysningsplikt.UgyldigTilstand(this::class).left()
    }

    sealed interface KunneIkkeLeggeTilOpplysningsplikt {
        data class UgyldigTilstand(
            val fra: KClass<out Søknadsbehandling>,
            val til: KClass<out Søknadsbehandling> = Vilkårsvurdert::class,
        ) : KunneIkkeLeggeTilOpplysningsplikt

        object HeleBehandlingsperiodenErIkkeVurdert : KunneIkkeLeggeTilOpplysningsplikt
    }

    open fun leggTilPensjonsVilkår(
        vilkår: PensjonsVilkår.Vurdert,
        clock: Clock,
        formuegrenserFactory: FormuegrenserFactory,
    ): Either<KunneIkkeLeggeTilPensjonsVilkår, Vilkårsvurdert> {
        return KunneIkkeLeggeTilPensjonsVilkår.UgyldigTilstand(this::class).left()
    }

    sealed interface KunneIkkeLeggeTilPensjonsVilkår {
        data class UgyldigTilstand(
            val fra: KClass<out Søknadsbehandling>,
            val til: KClass<out Søknadsbehandling> = Vilkårsvurdert::class,
        ) : KunneIkkeLeggeTilPensjonsVilkår

        object HeleBehandlingsperiodenErIkkeVurdert : KunneIkkeLeggeTilPensjonsVilkår
        object VilkårKunRelevantForAlder : KunneIkkeLeggeTilPensjonsVilkår
    }

    open fun beregn(
        begrunnelse: String?,
        clock: Clock,
        satsFactory: SatsFactory,
        formuegrenserFactory: FormuegrenserFactory,
    ): Either<KunneIkkeBeregne, Beregnet> {
        return KunneIkkeBeregne.UgyldigTilstand(this::class).left()
    }

    sealed class KunneIkkeBeregne {
        data class UgyldigTilstand(
            val fra: KClass<out Søknadsbehandling>,
            val til: KClass<out Beregnet> = Beregnet::class,
        ) : KunneIkkeBeregne()

        data class UgyldigTilstandForEndringAvFradrag(val feil: KunneIkkeLeggeTilFradragsgrunnlag) : KunneIkkeBeregne()
        object AvkortingErUfullstendig : KunneIkkeBeregne()
    }

    protected open fun valider(utenlandsopphold: UtenlandsoppholdVilkår.Vurdert): Either<KunneIkkeLeggeTilUtenlandsopphold, Unit> {
        return when {
            utenlandsopphold.vurderingsperioder.size != 1 -> {
                KunneIkkeLeggeTilUtenlandsopphold.MåInneholdeKunEnVurderingsperiode.left()
            }
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

    open fun leggTilUførevilkår(
        uførhet: Vilkår.Uførhet.Vurdert,
        clock: Clock,
        formuegrenserFactory: FormuegrenserFactory,
    ): Either<KunneIkkeLeggeTilUførevilkår, Vilkårsvurdert> {
        return KunneIkkeLeggeTilUførevilkår.UgyldigTilstand(this::class, Vilkårsvurdert::class).left()
    }

    protected open fun valider(uførhet: Vilkår.Uførhet.Vurdert): Either<KunneIkkeLeggeTilUførevilkår, Unit> {
        return when {
            !periode.inneholderAlle(uførhet.vurderingsperioder) -> {
                KunneIkkeLeggeTilUførevilkår.VurderingsperiodeUtenforBehandlingsperiode.left()
            }
            else -> Unit.right()
        }
    }

    protected open fun valider(opplysningspliktVilkår: OpplysningspliktVilkår.Vurdert): Either<KunneIkkeLeggeTilOpplysningsplikt, Unit> {
        return when {
            !periode.fullstendigOverlapp(opplysningspliktVilkår.minsteAntallSammenhengendePerioder()) -> {
                KunneIkkeLeggeTilOpplysningsplikt.HeleBehandlingsperiodenErIkkeVurdert.left()
            }
            else -> Unit.right()
        }
    }

    protected open fun valider(vilkår: PensjonsVilkår.Vurdert): Either<KunneIkkeLeggeTilPensjonsVilkår, Unit> {
        return when {
            Sakstype.ALDER != sakstype -> {
                KunneIkkeLeggeTilPensjonsVilkår.VilkårKunRelevantForAlder.left()
            }
            !periode.fullstendigOverlapp(vilkår.minsteAntallSammenhengendePerioder()) -> {
                KunneIkkeLeggeTilPensjonsVilkår.HeleBehandlingsperiodenErIkkeVurdert.left()
            }
            else -> Unit.right()
        }
    }

    sealed class KunneIkkeLeggeTilUførevilkår {
        data class UgyldigTilstand(val fra: KClass<out Søknadsbehandling>, val til: KClass<out Vilkårsvurdert>) :
            KunneIkkeLeggeTilUførevilkår()

        object VurderingsperiodeUtenforBehandlingsperiode : KunneIkkeLeggeTilUførevilkår()
    }

    protected fun beregnInternal(
        søknadsbehandling: Vilkårsvurdert,
        begrunnelse: String?,
        clock: Clock,
        beregningStrategyFactory: BeregningStrategyFactory,
        formuegrenserFactory: FormuegrenserFactory,
    ): Either<KunneIkkeBeregne, Beregnet> {
        return when (val avkort = søknadsbehandling.avkorting) {
            is AvkortingVedSøknadsbehandling.Uhåndtert.IngenUtestående -> {
                beregnUtenAvkorting(
                    begrunnelse = begrunnelse,
                    beregningStrategyFactory = beregningStrategyFactory,
                    formuegrenserFactory = formuegrenserFactory,
                ).getOrHandle { return it.left() }
            }
            is AvkortingVedSøknadsbehandling.Uhåndtert.KanIkkeHåndtere -> {
                throw IllegalStateException("${avkort::class} skal aldri kunne oppstå ved beregning. Modellen er dog nødt å støtte dette tilfellet pga at alle tilstander av avslutt/lukking må støttes.")
            }
            is AvkortingVedSøknadsbehandling.Uhåndtert.UteståendeAvkorting -> {
                beregnMedAvkorting(
                    avkorting = avkort,
                    begrunnelse = begrunnelse,
                    clock = clock,
                    beregningStrategyFactory = beregningStrategyFactory,
                    formuegrenserFactory = formuegrenserFactory,
                ).getOrHandle { return it.left() }
            }
        }.let { (behandling, beregning) ->
            when (VurderAvslagGrunnetBeregning.vurderAvslagGrunnetBeregning(beregning)) {
                is AvslagGrunnetBeregning.Ja -> Beregnet.Avslag(
                    id = behandling.id,
                    opprettet = behandling.opprettet,
                    sakId = behandling.sakId,
                    saksnummer = behandling.saksnummer,
                    søknad = behandling.søknad,
                    oppgaveId = behandling.oppgaveId,
                    behandlingsinformasjon = behandling.behandlingsinformasjon,
                    fnr = behandling.fnr,
                    beregning = beregning,
                    fritekstTilBrev = behandling.fritekstTilBrev,
                    stønadsperiode = behandling.stønadsperiode!!,
                    grunnlagsdata = behandling.grunnlagsdata,
                    vilkårsvurderinger = behandling.vilkårsvurderinger,
                    attesteringer = behandling.attesteringer,
                    avkorting = behandling.avkorting.håndter().kanIkke(),
                    sakstype = behandling.sakstype,
                )
                AvslagGrunnetBeregning.Nei -> {
                    Beregnet.Innvilget(
                        id = behandling.id,
                        opprettet = behandling.opprettet,
                        sakId = behandling.sakId,
                        saksnummer = behandling.saksnummer,
                        søknad = behandling.søknad,
                        oppgaveId = behandling.oppgaveId,
                        behandlingsinformasjon = behandling.behandlingsinformasjon,
                        fnr = behandling.fnr,
                        beregning = beregning,
                        fritekstTilBrev = behandling.fritekstTilBrev,
                        stønadsperiode = behandling.stønadsperiode!!,
                        grunnlagsdata = behandling.grunnlagsdata,
                        vilkårsvurderinger = behandling.vilkårsvurderinger,
                        attesteringer = behandling.attesteringer,
                        avkorting = behandling.avkorting.håndter(),
                        sakstype = behandling.sakstype,
                    )
                }
            }.right()
        }
    }

    /**
     * Beregner uten å ta hensyn til avkorting. Fjerner eventuelle [Fradragstype.AvkortingUtenlandsopphold] som måtte
     * ligge i grunnlaget
     */
    private fun beregnUtenAvkorting(
        begrunnelse: String?,
        beregningStrategyFactory: BeregningStrategyFactory,
        formuegrenserFactory: FormuegrenserFactory,
    ): Either<KunneIkkeBeregne, Pair<Vilkårsvurdert, Beregning>> {
        return leggTilFradragsgrunnlag(
            fradragsgrunnlag = grunnlagsdata.fradragsgrunnlag.filterNot { it.fradragstype == Fradragstype.AvkortingUtenlandsopphold },
            formuegrenserFactory = formuegrenserFactory,
        ).getOrHandle {
            return KunneIkkeBeregne.UgyldigTilstandForEndringAvFradrag(it).left()
        }.let {
            it to gjørBeregning(
                søknadsbehandling = it,
                begrunnelse = begrunnelse,
                beregningStrategyFactory = beregningStrategyFactory,
            )
        }.right()
    }

    private fun gjørBeregning(
        søknadsbehandling: Søknadsbehandling,
        begrunnelse: String?,
        beregningStrategyFactory: BeregningStrategyFactory,
    ): Beregning {
        return beregningStrategyFactory.beregn(søknadsbehandling, begrunnelse)
    }

    /**
     * Restbeløpet etter andre fradrag er faktorert inn av [beregnUtenAvkorting] er maksimalt beløp som kan avkortes.
     */
    private fun beregnMedAvkorting(
        avkorting: AvkortingVedSøknadsbehandling.Uhåndtert.UteståendeAvkorting,
        begrunnelse: String?,
        clock: Clock,
        beregningStrategyFactory: BeregningStrategyFactory,
        formuegrenserFactory: FormuegrenserFactory,
    ): Either<KunneIkkeBeregne, Pair<Vilkårsvurdert, Beregning>> {
        return beregnUtenAvkorting(begrunnelse, beregningStrategyFactory, formuegrenserFactory)
            .map { (utenAvkorting, beregningUtenAvkorting) ->
                val fradragForAvkorting = Avkortingsplan(
                    feilutbetaltBeløp = avkorting.avkortingsvarsel.hentUtbetalteBeløp().sum(),
                    beregning = beregningUtenAvkorting,
                    clock = clock,
                ).lagFradrag().getOrHandle {
                    return when (it) {
                        Avkortingsplan.KunneIkkeLageAvkortingsplan.AvkortingErUfullstendig -> {
                            KunneIkkeBeregne.AvkortingErUfullstendig.left()
                        }
                    }
                }

                val medAvkorting = utenAvkorting.leggTilFradragsgrunnlag(
                    fradragsgrunnlag = utenAvkorting.grunnlagsdata.fradragsgrunnlag + fradragForAvkorting,
                    formuegrenserFactory = formuegrenserFactory,
                ).getOrHandle { return KunneIkkeBeregne.UgyldigTilstandForEndringAvFradrag(it).left() }

                medAvkorting to gjørBeregning(
                    søknadsbehandling = medAvkorting,
                    begrunnelse = begrunnelse,
                    beregningStrategyFactory = beregningStrategyFactory,
                )
            }
    }

    sealed class Vilkårsvurdert : Søknadsbehandling() {

        abstract override val avkorting: AvkortingVedSøknadsbehandling.Uhåndtert

        fun tilVilkårsvurdert(
            behandlingsinformasjon: Behandlingsinformasjon = this.behandlingsinformasjon,
            grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderinger.Søknadsbehandling = this.grunnlagsdataOgVilkårsvurderinger,
            clock: Clock,
        ): Vilkårsvurdert =
            opprett(
                id = id,
                opprettet = opprettet,
                sakId = sakId,
                saksnummer = saksnummer,
                søknad = søknad,
                oppgaveId = oppgaveId,
                behandlingsinformasjon = this.behandlingsinformasjon.patch(behandlingsinformasjon),
                fnr = fnr,
                fritekstTilBrev = fritekstTilBrev,
                stønadsperiode = stønadsperiode,
                grunnlagsdataOgVilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger,
                attesteringer = attesteringer,
                clock = clock,
                avkorting = avkorting,
                sakstype = sakstype,
            )

        companion object {
            fun opprett(
                id: UUID,
                opprettet: Tidspunkt,
                sakId: UUID,
                saksnummer: Saksnummer,
                søknad: Søknad.Journalført.MedOppgave,
                oppgaveId: OppgaveId,
                behandlingsinformasjon: Behandlingsinformasjon,
                fnr: Fnr,
                fritekstTilBrev: String,
                // TODO jah: Hvorfor er denne nullable når vi krever at den ikke er det noen få linjer ned.
                stønadsperiode: Stønadsperiode?,
                grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderinger.Søknadsbehandling,
                attesteringer: Attesteringshistorikk,
                clock: Clock,
                avkorting: AvkortingVedSøknadsbehandling.Uhåndtert,
                sakstype: Sakstype,
            ): Vilkårsvurdert {
                val grunnlagsdata = grunnlagsdataOgVilkårsvurderinger.grunnlagsdata
                val vilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger.vilkårsvurderinger
                val oppdaterteVilkårsvurderinger = vilkårsvurderinger.oppdater(
                    stønadsperiode = stønadsperiode!!,
                    behandlingsinformasjon = behandlingsinformasjon,
                    clock = clock,
                ).let {
                    if (vilkårsvurderinger.opplysningspliktVilkår() !is OpplysningspliktVilkår.Vurdert) {
                        it.leggTil(
                            /**
                             * Legger til implisitt vilkår for oppfylt opplysningsplikt dersom dette ikke er vurdert fra før.
                             * Tar enn så lenge ikke stilling til dette vilkåret fra frontend ved søknadsbehandling, men brukes
                             * av [no.nav.su.se.bakover.domain.behandling.avslag.AvslagManglendeDokumentasjon]
                             */
                            OpplysningspliktVilkår.Vurdert.tryCreate(
                                vurderingsperioder = nonEmptyListOf(
                                    VurderingsperiodeOpplysningsplikt.create(
                                        id = UUID.randomUUID(),
                                        opprettet = opprettet,
                                        periode = stønadsperiode.periode,
                                        grunnlag = Opplysningspliktgrunnlag(
                                            id = UUID.randomUUID(),
                                            opprettet = opprettet,
                                            periode = stønadsperiode.periode,
                                            beskrivelse = OpplysningspliktBeskrivelse.TilstrekkeligDokumentasjon,
                                        ),
                                    ),
                                ),
                            ).getOrHandle { throw IllegalArgumentException(it.toString()) },
                        )
                    } else {
                        it
                    }
                }
                return when (oppdaterteVilkårsvurderinger.resultat) {
                    is Vilkårsvurderingsresultat.Avslag -> {
                        Avslag(
                            id,
                            opprettet,
                            sakId,
                            saksnummer,
                            søknad,
                            oppgaveId,
                            behandlingsinformasjon,
                            fnr,
                            fritekstTilBrev,
                            stønadsperiode,
                            grunnlagsdata,
                            oppdaterteVilkårsvurderinger,
                            attesteringer,
                            avkorting.kanIkke(),
                            sakstype,
                        )
                    }
                    is Vilkårsvurderingsresultat.Innvilget -> {
                        Innvilget(
                            id,
                            opprettet,
                            sakId,
                            saksnummer,
                            søknad,
                            oppgaveId,
                            behandlingsinformasjon,
                            fnr,
                            fritekstTilBrev,
                            stønadsperiode,
                            grunnlagsdata,
                            oppdaterteVilkårsvurderinger,
                            attesteringer,
                            avkorting.uhåndtert(),
                            sakstype,
                        )
                    }
                    is Vilkårsvurderingsresultat.Uavklart -> {
                        Uavklart(
                            id,
                            opprettet,
                            sakId,
                            saksnummer,
                            søknad,
                            oppgaveId,
                            behandlingsinformasjon,
                            fnr,
                            fritekstTilBrev,
                            stønadsperiode,
                            grunnlagsdata,
                            oppdaterteVilkårsvurderinger,
                            attesteringer,
                            avkorting.kanIkke(),
                            sakstype,
                        )
                    }
                }
            }
        }

        data class Innvilget(
            override val id: UUID,
            override val opprettet: Tidspunkt,
            override val sakId: UUID,
            override val saksnummer: Saksnummer,
            override val søknad: Søknad.Journalført.MedOppgave,
            override val oppgaveId: OppgaveId,
            override val behandlingsinformasjon: Behandlingsinformasjon,
            override val fnr: Fnr,
            override val fritekstTilBrev: String,
            override val stønadsperiode: Stønadsperiode,
            override val grunnlagsdata: Grunnlagsdata,
            override val vilkårsvurderinger: Vilkårsvurderinger.Søknadsbehandling,
            override val attesteringer: Attesteringshistorikk,
            override val avkorting: AvkortingVedSøknadsbehandling.Uhåndtert,
            override val sakstype: Sakstype,
        ) : Vilkårsvurdert() {

            override val status: BehandlingsStatus = BehandlingsStatus.VILKÅRSVURDERT_INNVILGET

            override val periode: Periode = stønadsperiode.periode

            init {
                kastHvisGrunnlagsdataOgVilkårsvurderingerPeriodenOgBehandlingensPerioderErUlike()
            }

            override fun accept(visitor: SøknadsbehandlingVisitor) {
                visitor.visit(this)
            }

            override fun leggTilFradragsgrunnlag(
                fradragsgrunnlag: List<Grunnlag.Fradragsgrunnlag>,
                formuegrenserFactory: FormuegrenserFactory,
            ): Either<KunneIkkeLeggeTilFradragsgrunnlag, Innvilget> {
                validerFradragsgrunnlag(fradragsgrunnlag).mapLeft {
                    return it.left()
                }

                return Innvilget(
                    id,
                    opprettet,
                    sakId,
                    saksnummer,
                    søknad,
                    oppgaveId,
                    this.behandlingsinformasjon.patch(behandlingsinformasjon),
                    fnr,
                    fritekstTilBrev,
                    stønadsperiode,
                    grunnlagsdata = Grunnlagsdata.tryCreateTillatUfullstendigBosituasjon(
                        fradragsgrunnlag = fradragsgrunnlag,
                        bosituasjon = this.grunnlagsdata.bosituasjon,
                    ).getOrHandle {
                        return KunneIkkeLeggeTilFradragsgrunnlag.KunneIkkeEndreFradragsgrunnlag(it).left()
                    },
                    vilkårsvurderinger,
                    attesteringer,
                    avkorting,
                    sakstype,
                ).right()
            }

            override fun leggTilUtenlandsopphold(
                utenlandsopphold: UtenlandsoppholdVilkår.Vurdert,
                clock: Clock,
            ): Either<KunneIkkeLeggeTilUtenlandsopphold, Vilkårsvurdert> {
                return valider(utenlandsopphold)
                    .map { vilkårsvurder(vilkårsvurderinger.leggTil(utenlandsopphold), clock) }
            }

            override fun leggTilOpplysningspliktVilkår(
                opplysningspliktVilkår: OpplysningspliktVilkår.Vurdert,
                clock: Clock,
                formuegrenserFactory: FormuegrenserFactory,
            ): Either<KunneIkkeLeggeTilOpplysningsplikt, Vilkårsvurdert> {
                return valider(opplysningspliktVilkår)
                    .map { vilkårsvurder(vilkårsvurderinger.leggTil(opplysningspliktVilkår), clock) }
            }

            override fun leggTilPensjonsVilkår(
                vilkår: PensjonsVilkår.Vurdert,
                clock: Clock,
                formuegrenserFactory: FormuegrenserFactory,
            ): Either<KunneIkkeLeggeTilPensjonsVilkår, Vilkårsvurdert> {
                return valider(vilkår)
                    .map { vilkårsvurder(vilkårsvurderinger.leggTil(vilkår), clock) }
            }

            override fun leggTilFormuevilkår(
                vilkår: Vilkår.Formue.Vurdert,
                clock: Clock,
            ): Either<KunneIkkeLeggeTilFormuegrunnlag, Vilkårsvurdert> {
                return tilVilkårsvurdert(
                    grunnlagsdataOgVilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger.oppdaterFormuevilkår(
                        vilkår = vilkår,
                    ),
                    clock = clock,
                ).right()
            }

            private fun vilkårsvurder(
                vilkårsvurderinger: Vilkårsvurderinger.Søknadsbehandling,
                clock: Clock,
            ): Vilkårsvurdert {
                return tilVilkårsvurdert(
                    behandlingsinformasjon = behandlingsinformasjon,
                    grunnlagsdataOgVilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger.copy(
                        vilkårsvurderinger = vilkårsvurderinger,
                    ),
                    clock = clock,
                )
            }

            override fun leggTilUførevilkår(
                uførhet: Vilkår.Uførhet.Vurdert,
                clock: Clock,
                formuegrenserFactory: FormuegrenserFactory,
            ): Either<KunneIkkeLeggeTilUførevilkår, Vilkårsvurdert> {
                return valider(uførhet)
                    .map { vilkårsvurder(vilkårsvurderinger.leggTil(uførhet), clock) }
            }

            override fun beregn(
                begrunnelse: String?,
                clock: Clock,
                satsFactory: SatsFactory,
                formuegrenserFactory: FormuegrenserFactory,
            ): Either<KunneIkkeBeregne, Beregnet> {
                return beregnInternal(
                    søknadsbehandling = vilkårsvurder(vilkårsvurderinger, clock),
                    begrunnelse = begrunnelse,
                    clock = clock,
                    beregningStrategyFactory = BeregningStrategyFactory(
                        clock = clock,
                        satsFactory = satsFactory,
                    ),
                    formuegrenserFactory = formuegrenserFactory,
                )
            }

            // TODO("flere_satser det gir egentlig ikke mening at vi oppdaterer flere verdier på denne måten, bør sees på/vurderes fjernet")
            override fun oppdaterStønadsperiode(
                oppdatertStønadsperiode: Stønadsperiode,
                clock: Clock,
                formuegrenserFactory: FormuegrenserFactory,
            ): Either<KunneIkkeOppdatereStønadsperiode, Vilkårsvurdert> {
                return grunnlagsdataOgVilkårsvurderinger.oppdaterStønadsperiode(
                    stønadsperiode = oppdatertStønadsperiode,
                    formuegrenserFactory = formuegrenserFactory,
                ).mapLeft {
                    KunneIkkeOppdatereStønadsperiode.KunneIkkeOppdatereGrunnlagsdata(it)
                }.map {
                    copy(
                        stønadsperiode = oppdatertStønadsperiode,
                        grunnlagsdata = it.grunnlagsdata,
                        vilkårsvurderinger = it.vilkårsvurderinger,
                    ).vilkårsvurder(
                        vilkårsvurderinger = it.vilkårsvurderinger,
                        clock = clock,
                    )
                }
            }
        }

        data class Avslag(
            override val id: UUID,
            override val opprettet: Tidspunkt,
            override val sakId: UUID,
            override val saksnummer: Saksnummer,
            override val søknad: Søknad.Journalført.MedOppgave,
            override val oppgaveId: OppgaveId,
            override val behandlingsinformasjon: Behandlingsinformasjon,
            override val fnr: Fnr,
            override val fritekstTilBrev: String,
            override val stønadsperiode: Stønadsperiode,
            override val grunnlagsdata: Grunnlagsdata,
            override val vilkårsvurderinger: Vilkårsvurderinger.Søknadsbehandling,
            override val attesteringer: Attesteringshistorikk,
            override val avkorting: AvkortingVedSøknadsbehandling.Uhåndtert.KanIkkeHåndtere,
            override val sakstype: Sakstype,
        ) : Vilkårsvurdert(), ErAvslag {

            override val status: BehandlingsStatus = BehandlingsStatus.VILKÅRSVURDERT_AVSLAG
            override val periode: Periode = stønadsperiode.periode

            init {
                kastHvisGrunnlagsdataOgVilkårsvurderingerPeriodenOgBehandlingensPerioderErUlike()
            }

            override fun accept(visitor: SøknadsbehandlingVisitor) {
                visitor.visit(this)
            }

            fun tilAttestering(
                saksbehandler: NavIdentBruker.Saksbehandler,
                fritekstTilBrev: String,
            ): TilAttestering.Avslag.UtenBeregning =
                TilAttestering.Avslag.UtenBeregning(
                    id,
                    opprettet,
                    sakId,
                    saksnummer,
                    søknad,
                    oppgaveId,
                    behandlingsinformasjon,
                    fnr,
                    saksbehandler,
                    fritekstTilBrev,
                    stønadsperiode,
                    grunnlagsdata,
                    vilkårsvurderinger,
                    attesteringer,
                    avkorting.håndter().kanIkke(),
                    sakstype,
                )

            // TODO fiks typing/gyldig tilstand/vilkår fradrag?
            override val avslagsgrunner: List<Avslagsgrunn> = when (val vilkår = vilkårsvurderinger.resultat) {
                is Vilkårsvurderingsresultat.Avslag -> vilkår.avslagsgrunner
                is Vilkårsvurderingsresultat.Innvilget -> emptyList()
                is Vilkårsvurderingsresultat.Uavklart -> emptyList()
            }

            override fun leggTilUtenlandsopphold(
                utenlandsopphold: UtenlandsoppholdVilkår.Vurdert,
                clock: Clock,
            ): Either<KunneIkkeLeggeTilUtenlandsopphold, Vilkårsvurdert> {
                return valider(utenlandsopphold)
                    .map { vilkårsvurder(vilkårsvurderinger.leggTil(utenlandsopphold), clock) }
            }

            override fun leggTilOpplysningspliktVilkår(
                opplysningspliktVilkår: OpplysningspliktVilkår.Vurdert,
                clock: Clock,
                formuegrenserFactory: FormuegrenserFactory,
            ): Either<KunneIkkeLeggeTilOpplysningsplikt, Vilkårsvurdert> {
                return valider(opplysningspliktVilkår)
                    .map { vilkårsvurder(vilkårsvurderinger.leggTil(opplysningspliktVilkår), clock) }
            }

            override fun leggTilPensjonsVilkår(
                vilkår: PensjonsVilkår.Vurdert,
                clock: Clock,
                formuegrenserFactory: FormuegrenserFactory,
            ): Either<KunneIkkeLeggeTilPensjonsVilkår, Vilkårsvurdert> {
                return valider(vilkår)
                    .map { vilkårsvurder(vilkårsvurderinger.leggTil(vilkår), clock) }
            }

            override fun leggTilFormuevilkår(
                vilkår: Vilkår.Formue.Vurdert,
                clock: Clock,
            ): Either<KunneIkkeLeggeTilFormuegrunnlag, Vilkårsvurdert> {
                return tilVilkårsvurdert(
                    grunnlagsdataOgVilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger.oppdaterFormuevilkår(
                        vilkår = vilkår,
                    ),
                    clock = clock,
                ).right()
            }

            private fun vilkårsvurder(
                vilkårsvurderinger: Vilkårsvurderinger.Søknadsbehandling,
                clock: Clock,
            ): Vilkårsvurdert {
                return tilVilkårsvurdert(
                    behandlingsinformasjon,
                    grunnlagsdataOgVilkårsvurderinger.copy(
                        vilkårsvurderinger = vilkårsvurderinger,
                    ),
                    clock,
                )
            }

            override fun leggTilUførevilkår(
                uførhet: Vilkår.Uførhet.Vurdert,
                clock: Clock,
                formuegrenserFactory: FormuegrenserFactory,
            ): Either<KunneIkkeLeggeTilUførevilkår, Vilkårsvurdert> {
                return valider(uførhet)
                    .map { vilkårsvurder(vilkårsvurderinger.leggTil(uførhet), clock) }
            }

            override fun oppdaterStønadsperiode(
                oppdatertStønadsperiode: Stønadsperiode,
                clock: Clock,
                formuegrenserFactory: FormuegrenserFactory,
            ): Either<KunneIkkeOppdatereStønadsperiode, Vilkårsvurdert> {
                return grunnlagsdataOgVilkårsvurderinger.oppdaterStønadsperiode(
                    stønadsperiode = oppdatertStønadsperiode,
                    formuegrenserFactory = formuegrenserFactory,
                ).mapLeft {
                    KunneIkkeOppdatereStønadsperiode.KunneIkkeOppdatereGrunnlagsdata(it)
                }.map {
                    copy(
                        stønadsperiode = oppdatertStønadsperiode,
                        grunnlagsdata = it.grunnlagsdata,
                        vilkårsvurderinger = it.vilkårsvurderinger,
                    ).vilkårsvurder(
                        vilkårsvurderinger = it.vilkårsvurderinger,
                        clock = clock,
                    )
                }
            }
        }

        data class Uavklart(
            override val id: UUID,
            override val opprettet: Tidspunkt,
            override val sakId: UUID,
            override val saksnummer: Saksnummer,
            override val søknad: Søknad.Journalført.MedOppgave,
            override val oppgaveId: OppgaveId,
            override val behandlingsinformasjon: Behandlingsinformasjon,
            override val fnr: Fnr,
            override val fritekstTilBrev: String,
            override val stønadsperiode: Stønadsperiode?,
            override val grunnlagsdata: Grunnlagsdata,
            override val vilkårsvurderinger: Vilkårsvurderinger.Søknadsbehandling,
            override val attesteringer: Attesteringshistorikk,
            override val avkorting: AvkortingVedSøknadsbehandling.Uhåndtert.KanIkkeHåndtere,
            override val sakstype: Sakstype,
        ) : Vilkårsvurdert() {

            override val status: BehandlingsStatus = BehandlingsStatus.OPPRETTET
            override val periode: Periode
                get() = stønadsperiode?.periode ?: throw StønadsperiodeIkkeDefinertException(id)

            init {
                kastHvisGrunnlagsdataOgVilkårsvurderingerPeriodenOgBehandlingensPerioderErUlike()
            }

            override fun accept(visitor: SøknadsbehandlingVisitor) {
                visitor.visit(this)
            }

            override fun leggTilUtenlandsopphold(
                utenlandsopphold: UtenlandsoppholdVilkår.Vurdert,
                clock: Clock,
            ): Either<KunneIkkeLeggeTilUtenlandsopphold, Vilkårsvurdert> {
                return valider(utenlandsopphold)
                    .map { vilkårsvurder(vilkårsvurderinger.leggTil(utenlandsopphold), clock) }
            }

            override fun leggTilOpplysningspliktVilkår(
                opplysningspliktVilkår: OpplysningspliktVilkår.Vurdert,
                clock: Clock,
                formuegrenserFactory: FormuegrenserFactory,
            ): Either<KunneIkkeLeggeTilOpplysningsplikt, Vilkårsvurdert> {
                return valider(opplysningspliktVilkår)
                    .map { vilkårsvurder(vilkårsvurderinger.leggTil(opplysningspliktVilkår), clock) }
            }

            override fun leggTilPensjonsVilkår(
                vilkår: PensjonsVilkår.Vurdert,
                clock: Clock,
                formuegrenserFactory: FormuegrenserFactory,
            ): Either<KunneIkkeLeggeTilPensjonsVilkår, Vilkårsvurdert> {
                return valider(vilkår)
                    .map { vilkårsvurder(vilkårsvurderinger.leggTil(vilkår), clock) }
            }

            override fun leggTilFormuevilkår(
                vilkår: Vilkår.Formue.Vurdert,
                clock: Clock,
            ): Either<KunneIkkeLeggeTilFormuegrunnlag, Vilkårsvurdert> {
                return tilVilkårsvurdert(
                    grunnlagsdataOgVilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger.oppdaterFormuevilkår(
                        vilkår = vilkår,
                    ),
                    clock = clock,
                ).right()
            }

            private fun vilkårsvurder(
                vilkårsvurderinger: Vilkårsvurderinger.Søknadsbehandling,
                clock: Clock,
            ): Vilkårsvurdert {
                return tilVilkårsvurdert(
                    behandlingsinformasjon,
                    grunnlagsdataOgVilkårsvurderinger.copy(
                        vilkårsvurderinger = vilkårsvurderinger,
                    ),
                    clock,
                )
            }

            override fun leggTilUførevilkår(
                uførhet: Vilkår.Uførhet.Vurdert,
                clock: Clock,
                formuegrenserFactory: FormuegrenserFactory,
            ): Either<KunneIkkeLeggeTilUførevilkår, Vilkårsvurdert> {
                return valider(uførhet)
                    .map { vilkårsvurder(vilkårsvurderinger.leggTil(uførhet), clock) }
            }

            override fun oppdaterStønadsperiode(
                oppdatertStønadsperiode: Stønadsperiode,
                clock: Clock,
                formuegrenserFactory: FormuegrenserFactory,
            ): Either<KunneIkkeOppdatereStønadsperiode, Vilkårsvurdert> {
                return grunnlagsdataOgVilkårsvurderinger.oppdaterStønadsperiode(
                    stønadsperiode = oppdatertStønadsperiode,
                    formuegrenserFactory = formuegrenserFactory,
                ).mapLeft {
                    KunneIkkeOppdatereStønadsperiode.KunneIkkeOppdatereGrunnlagsdata(it)
                }.map {
                    copy(
                        stønadsperiode = oppdatertStønadsperiode,
                        grunnlagsdata = it.grunnlagsdata,
                        vilkårsvurderinger = it.vilkårsvurderinger,
                    ).vilkårsvurder(
                        vilkårsvurderinger = it.vilkårsvurderinger,
                        clock = clock,
                    )
                }
            }

            data class StønadsperiodeIkkeDefinertException(
                val id: UUID,
            ) : RuntimeException("Sønadsperiode er ikke definert for søknadsbehandling:$id")
        }
    }

    sealed class Beregnet : Søknadsbehandling() {
        abstract override val behandlingsinformasjon: Behandlingsinformasjon
        abstract val beregning: Beregning
        abstract override val stønadsperiode: Stønadsperiode
        abstract override val avkorting: AvkortingVedSøknadsbehandling.Håndtert

        fun tilVilkårsvurdert(
            behandlingsinformasjon: Behandlingsinformasjon = this.behandlingsinformasjon,
            grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderinger.Søknadsbehandling = this.grunnlagsdataOgVilkårsvurderinger,
            clock: Clock,
        ): Vilkårsvurdert =
            opprett(
                id = id,
                opprettet = opprettet,
                sakId = sakId,
                saksnummer = saksnummer,
                søknad = søknad,
                oppgaveId = oppgaveId,
                behandlingsinformasjon = this.behandlingsinformasjon.patch(behandlingsinformasjon),
                fnr = fnr,
                fritekstTilBrev = fritekstTilBrev,
                stønadsperiode = stønadsperiode,
                grunnlagsdataOgVilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger,
                attesteringer = attesteringer,
                clock = clock,
                avkorting = avkorting.uhåndtert(),
                sakstype = sakstype,
            )

        abstract override fun beregn(
            begrunnelse: String?,
            clock: Clock,
            satsFactory: SatsFactory,
            formuegrenserFactory: FormuegrenserFactory,
        ): Either<KunneIkkeBeregne, Beregnet>

        fun tilSimulert(simulering: Simulering): Simulert =
            Simulert(
                id,
                opprettet,
                sakId,
                saksnummer,
                søknad,
                oppgaveId,
                behandlingsinformasjon,
                fnr,
                beregning,
                simulering,
                fritekstTilBrev,
                stønadsperiode,
                grunnlagsdata,
                vilkårsvurderinger,
                attesteringer,
                avkorting,
                sakstype,
            )

        data class Innvilget(
            override val id: UUID,
            override val opprettet: Tidspunkt,
            override val sakId: UUID,
            override val saksnummer: Saksnummer,
            override val søknad: Søknad.Journalført.MedOppgave,
            override val oppgaveId: OppgaveId,
            override val behandlingsinformasjon: Behandlingsinformasjon,
            override val fnr: Fnr,
            override val beregning: Beregning,
            override val fritekstTilBrev: String,
            override val stønadsperiode: Stønadsperiode,
            override val grunnlagsdata: Grunnlagsdata,
            override val vilkårsvurderinger: Vilkårsvurderinger.Søknadsbehandling,
            override val attesteringer: Attesteringshistorikk,
            override val avkorting: AvkortingVedSøknadsbehandling.Håndtert,
            override val sakstype: Sakstype,
        ) : Beregnet() {
            override val status: BehandlingsStatus = BehandlingsStatus.BEREGNET_INNVILGET
            override val periode: Periode = stønadsperiode.periode

            init {
                kastHvisGrunnlagsdataOgVilkårsvurderingerPeriodenOgBehandlingensPerioderErUlike()
            }

            override fun accept(visitor: SøknadsbehandlingVisitor) {
                visitor.visit(this)
            }

            override fun leggTilFradragsgrunnlag(
                fradragsgrunnlag: List<Grunnlag.Fradragsgrunnlag>,
                formuegrenserFactory: FormuegrenserFactory,
            ): Either<KunneIkkeLeggeTilFradragsgrunnlag, Vilkårsvurdert.Innvilget> {
                validerFradragsgrunnlag(fradragsgrunnlag).mapLeft {
                    return it.left()
                }

                return Vilkårsvurdert.Innvilget(
                    id,
                    opprettet,
                    sakId,
                    saksnummer,
                    søknad,
                    oppgaveId,
                    this.behandlingsinformasjon.patch(behandlingsinformasjon),
                    fnr,
                    fritekstTilBrev,
                    stønadsperiode,
                    grunnlagsdata = Grunnlagsdata.tryCreate(
                        fradragsgrunnlag = fradragsgrunnlag,
                        bosituasjon = this.grunnlagsdata.bosituasjon,
                    ).getOrHandle {
                        return KunneIkkeLeggeTilFradragsgrunnlag.KunneIkkeEndreFradragsgrunnlag(it).left()
                    },
                    vilkårsvurderinger,
                    attesteringer,
                    avkorting.uhåndtert(),
                    sakstype,
                ).right()
            }

            override fun leggTilUtenlandsopphold(
                utenlandsopphold: UtenlandsoppholdVilkår.Vurdert,
                clock: Clock,
            ): Either<KunneIkkeLeggeTilUtenlandsopphold, Vilkårsvurdert> {
                return valider(utenlandsopphold)
                    .map { vilkårsvurder(vilkårsvurderinger.leggTil(utenlandsopphold), clock) }
            }

            override fun leggTilOpplysningspliktVilkår(
                opplysningspliktVilkår: OpplysningspliktVilkår.Vurdert,
                clock: Clock,
                formuegrenserFactory: FormuegrenserFactory,
            ): Either<KunneIkkeLeggeTilOpplysningsplikt, Vilkårsvurdert> {
                return valider(opplysningspliktVilkår)
                    .map { vilkårsvurder(vilkårsvurderinger.leggTil(opplysningspliktVilkår), clock) }
            }

            override fun leggTilPensjonsVilkår(
                vilkår: PensjonsVilkår.Vurdert,
                clock: Clock,
                formuegrenserFactory: FormuegrenserFactory,
            ): Either<KunneIkkeLeggeTilPensjonsVilkår, Vilkårsvurdert> {
                return valider(vilkår)
                    .map { vilkårsvurder(vilkårsvurderinger.leggTil(vilkår), clock) }
            }

            override fun leggTilFormuevilkår(
                vilkår: Vilkår.Formue.Vurdert,
                clock: Clock,
            ): Either<KunneIkkeLeggeTilFormuegrunnlag, Vilkårsvurdert> {
                return tilVilkårsvurdert(
                    grunnlagsdataOgVilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger.oppdaterFormuevilkår(
                        vilkår = vilkår,
                    ),
                    clock = clock,
                ).right()
            }

            private fun vilkårsvurder(
                vilkårsvurderinger: Vilkårsvurderinger.Søknadsbehandling,
                clock: Clock,
            ): Vilkårsvurdert {
                return tilVilkårsvurdert(
                    behandlingsinformasjon = behandlingsinformasjon,
                    grunnlagsdataOgVilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger.copy(
                        vilkårsvurderinger = vilkårsvurderinger,
                    ),
                    clock = clock,
                )
            }

            override fun beregn(
                begrunnelse: String?,
                clock: Clock,
                satsFactory: SatsFactory,
                formuegrenserFactory: FormuegrenserFactory,
            ): Either<KunneIkkeBeregne, Beregnet> {
                return beregnInternal(
                    søknadsbehandling = vilkårsvurder(vilkårsvurderinger, clock),
                    begrunnelse = begrunnelse,
                    clock = clock,
                    beregningStrategyFactory = BeregningStrategyFactory(
                        clock = clock,
                        satsFactory = satsFactory,
                    ),
                    formuegrenserFactory = formuegrenserFactory,
                )
            }

            override fun leggTilUførevilkår(
                uførhet: Vilkår.Uførhet.Vurdert,
                clock: Clock,
                formuegrenserFactory: FormuegrenserFactory,
            ): Either<KunneIkkeLeggeTilUførevilkår, Vilkårsvurdert> {
                return valider(uførhet)
                    .map { vilkårsvurder(vilkårsvurderinger.leggTil(uførhet), clock) }
            }

            override fun oppdaterStønadsperiode(
                oppdatertStønadsperiode: Stønadsperiode,
                clock: Clock,
                formuegrenserFactory: FormuegrenserFactory,
            ): Either<KunneIkkeOppdatereStønadsperiode, Vilkårsvurdert> {
                return grunnlagsdataOgVilkårsvurderinger.oppdaterStønadsperiode(
                    stønadsperiode = oppdatertStønadsperiode,
                    formuegrenserFactory = formuegrenserFactory,
                ).mapLeft {
                    KunneIkkeOppdatereStønadsperiode.KunneIkkeOppdatereGrunnlagsdata(it)
                }.map {
                    copy(
                        stønadsperiode = oppdatertStønadsperiode,
                        grunnlagsdata = it.grunnlagsdata,
                        vilkårsvurderinger = it.vilkårsvurderinger,
                    ).vilkårsvurder(
                        vilkårsvurderinger = it.vilkårsvurderinger,
                        clock = clock,
                    )
                }
            }
        }

        data class Avslag(
            override val id: UUID,
            override val opprettet: Tidspunkt,
            override val sakId: UUID,
            override val saksnummer: Saksnummer,
            override val søknad: Søknad.Journalført.MedOppgave,
            override val oppgaveId: OppgaveId,
            override val behandlingsinformasjon: Behandlingsinformasjon,
            override val fnr: Fnr,
            override val beregning: Beregning,
            override val fritekstTilBrev: String,
            override val stønadsperiode: Stønadsperiode,
            override val grunnlagsdata: Grunnlagsdata,
            override val vilkårsvurderinger: Vilkårsvurderinger.Søknadsbehandling,
            override val attesteringer: Attesteringshistorikk,
            override val avkorting: AvkortingVedSøknadsbehandling.Håndtert.KanIkkeHåndtere,
            override val sakstype: Sakstype,
        ) : Beregnet(), ErAvslag {
            override val status: BehandlingsStatus = BehandlingsStatus.BEREGNET_AVSLAG
            override val periode: Periode = stønadsperiode.periode

            private val avslagsgrunnForBeregning: List<Avslagsgrunn> =
                when (val vurdering = VurderAvslagGrunnetBeregning.vurderAvslagGrunnetBeregning(beregning)) {
                    is AvslagGrunnetBeregning.Ja -> listOf(vurdering.grunn.toAvslagsgrunn())
                    is AvslagGrunnetBeregning.Nei -> emptyList()
                }

            init {
                kastHvisGrunnlagsdataOgVilkårsvurderingerPeriodenOgBehandlingensPerioderErUlike()
            }

            override fun leggTilFradragsgrunnlag(
                fradragsgrunnlag: List<Grunnlag.Fradragsgrunnlag>,
                formuegrenserFactory: FormuegrenserFactory,
            ): Either<KunneIkkeLeggeTilFradragsgrunnlag, Vilkårsvurdert.Innvilget> {
                validerFradragsgrunnlag(fradragsgrunnlag).mapLeft {
                    return it.left()
                }

                return Vilkårsvurdert.Innvilget(
                    id,
                    opprettet,
                    sakId,
                    saksnummer,
                    søknad,
                    oppgaveId,
                    this.behandlingsinformasjon.patch(behandlingsinformasjon),
                    fnr,
                    fritekstTilBrev,
                    stønadsperiode,
                    grunnlagsdata = Grunnlagsdata.tryCreate(
                        fradragsgrunnlag = fradragsgrunnlag,
                        bosituasjon = this.grunnlagsdata.bosituasjon,
                    ).getOrHandle {
                        return KunneIkkeLeggeTilFradragsgrunnlag.KunneIkkeEndreFradragsgrunnlag(it).left()
                    },
                    vilkårsvurderinger,
                    attesteringer,
                    avkorting.uhåndtert(),
                    sakstype,
                ).right()
            }

            override fun accept(visitor: SøknadsbehandlingVisitor) {
                visitor.visit(this)
            }

            fun tilAttestering(
                saksbehandler: NavIdentBruker.Saksbehandler,
                fritekstTilBrev: String,
            ): TilAttestering.Avslag.MedBeregning =
                TilAttestering.Avslag.MedBeregning(
                    id,
                    opprettet,
                    sakId,
                    saksnummer,
                    søknad,
                    oppgaveId,
                    behandlingsinformasjon,
                    fnr,
                    beregning,
                    saksbehandler,
                    fritekstTilBrev,
                    stønadsperiode,
                    grunnlagsdata,
                    vilkårsvurderinger,
                    attesteringer,
                    avkorting.kanIkke(),
                    sakstype,
                )

            // TODO fiks typing/gyldig tilstand/vilkår fradrag?
            override val avslagsgrunner: List<Avslagsgrunn> = when (val vilkår = vilkårsvurderinger.resultat) {
                is Vilkårsvurderingsresultat.Avslag -> vilkår.avslagsgrunner
                is Vilkårsvurderingsresultat.Innvilget -> emptyList()
                is Vilkårsvurderingsresultat.Uavklart -> emptyList()
            } + avslagsgrunnForBeregning

            override fun leggTilUtenlandsopphold(
                utenlandsopphold: UtenlandsoppholdVilkår.Vurdert,
                clock: Clock,
            ): Either<KunneIkkeLeggeTilUtenlandsopphold, Vilkårsvurdert> {
                return valider(utenlandsopphold)
                    .map { vilkårsvurder(vilkårsvurderinger.leggTil(utenlandsopphold), clock) }
            }

            override fun leggTilOpplysningspliktVilkår(
                opplysningspliktVilkår: OpplysningspliktVilkår.Vurdert,
                clock: Clock,
                formuegrenserFactory: FormuegrenserFactory,
            ): Either<KunneIkkeLeggeTilOpplysningsplikt, Vilkårsvurdert> {
                return valider(opplysningspliktVilkår)
                    .map { vilkårsvurder(vilkårsvurderinger.leggTil(opplysningspliktVilkår), clock) }
            }

            override fun leggTilPensjonsVilkår(
                vilkår: PensjonsVilkår.Vurdert,
                clock: Clock,
                formuegrenserFactory: FormuegrenserFactory,
            ): Either<KunneIkkeLeggeTilPensjonsVilkår, Vilkårsvurdert> {
                return valider(vilkår)
                    .map { vilkårsvurder(vilkårsvurderinger.leggTil(vilkår), clock) }
            }

            override fun leggTilFormuevilkår(
                vilkår: Vilkår.Formue.Vurdert,
                clock: Clock,
            ): Either<KunneIkkeLeggeTilFormuegrunnlag, Vilkårsvurdert> {
                return tilVilkårsvurdert(
                    grunnlagsdataOgVilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger.oppdaterFormuevilkår(
                        vilkår = vilkår,
                    ),
                    clock = clock,
                ).right()
            }

            private fun vilkårsvurder(
                vilkårsvurderinger: Vilkårsvurderinger.Søknadsbehandling,
                clock: Clock,
            ): Vilkårsvurdert {
                return tilVilkårsvurdert(
                    behandlingsinformasjon = behandlingsinformasjon,
                    grunnlagsdataOgVilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger.copy(
                        vilkårsvurderinger = vilkårsvurderinger,
                    ),
                    clock = clock,
                )
            }

            override fun beregn(
                begrunnelse: String?,
                clock: Clock,
                satsFactory: SatsFactory,
                formuegrenserFactory: FormuegrenserFactory,
            ): Either<KunneIkkeBeregne, Beregnet> {
                return beregnInternal(
                    søknadsbehandling = vilkårsvurder(vilkårsvurderinger, clock),
                    begrunnelse = begrunnelse,
                    clock = clock,
                    beregningStrategyFactory = BeregningStrategyFactory(
                        clock = clock,
                        satsFactory = satsFactory,
                    ),
                    formuegrenserFactory = formuegrenserFactory,
                )
            }

            override fun leggTilUførevilkår(
                uførhet: Vilkår.Uførhet.Vurdert,
                clock: Clock,
                formuegrenserFactory: FormuegrenserFactory,
            ): Either<KunneIkkeLeggeTilUførevilkår, Vilkårsvurdert> {
                return valider(uførhet)
                    .map { vilkårsvurder(vilkårsvurderinger.leggTil(uførhet), clock) }
            }

            override fun oppdaterStønadsperiode(
                oppdatertStønadsperiode: Stønadsperiode,
                clock: Clock,
                formuegrenserFactory: FormuegrenserFactory,
            ): Either<KunneIkkeOppdatereStønadsperiode, Vilkårsvurdert> {
                return grunnlagsdataOgVilkårsvurderinger.oppdaterStønadsperiode(
                    stønadsperiode = oppdatertStønadsperiode,
                    formuegrenserFactory = formuegrenserFactory,
                ).mapLeft {
                    KunneIkkeOppdatereStønadsperiode.KunneIkkeOppdatereGrunnlagsdata(it)
                }.map {
                    copy(
                        stønadsperiode = oppdatertStønadsperiode,
                        grunnlagsdata = it.grunnlagsdata,
                        vilkårsvurderinger = it.vilkårsvurderinger,
                    ).vilkårsvurder(
                        vilkårsvurderinger = it.vilkårsvurderinger,
                        clock = clock,
                    )
                }
            }
        }
    }

    data class Simulert(
        override val id: UUID,
        override val opprettet: Tidspunkt,
        override val sakId: UUID,
        override val saksnummer: Saksnummer,
        override val søknad: Søknad.Journalført.MedOppgave,
        override val oppgaveId: OppgaveId,
        override val behandlingsinformasjon: Behandlingsinformasjon,
        override val fnr: Fnr,
        val beregning: Beregning,
        val simulering: Simulering,
        override val fritekstTilBrev: String,
        override val stønadsperiode: Stønadsperiode,
        override val grunnlagsdata: Grunnlagsdata,
        override val vilkårsvurderinger: Vilkårsvurderinger.Søknadsbehandling,
        override val attesteringer: Attesteringshistorikk,
        override val avkorting: AvkortingVedSøknadsbehandling.Håndtert,
        override val sakstype: Sakstype,
    ) : Søknadsbehandling() {
        override val status: BehandlingsStatus = BehandlingsStatus.SIMULERT
        override val periode: Periode = stønadsperiode.periode

        init {
            kastHvisGrunnlagsdataOgVilkårsvurderingerPeriodenOgBehandlingensPerioderErUlike()
        }

        override fun accept(visitor: SøknadsbehandlingVisitor) {
            visitor.visit(this)
        }

        override fun leggTilFradragsgrunnlag(
            fradragsgrunnlag: List<Grunnlag.Fradragsgrunnlag>,
            formuegrenserFactory: FormuegrenserFactory,
        ): Either<KunneIkkeLeggeTilFradragsgrunnlag, Vilkårsvurdert.Innvilget> {
            validerFradragsgrunnlag(fradragsgrunnlag).mapLeft {
                return it.left()
            }

            return Vilkårsvurdert.Innvilget(
                id,
                opprettet,
                sakId,
                saksnummer,
                søknad,
                oppgaveId,
                this.behandlingsinformasjon.patch(behandlingsinformasjon),
                fnr,
                fritekstTilBrev,
                stønadsperiode,
                grunnlagsdata = Grunnlagsdata.tryCreate(
                    fradragsgrunnlag = fradragsgrunnlag,
                    bosituasjon = this.grunnlagsdata.bosituasjon,
                ).getOrHandle { return KunneIkkeLeggeTilFradragsgrunnlag.KunneIkkeEndreFradragsgrunnlag(it).left() },
                vilkårsvurderinger,
                attesteringer,
                avkorting.uhåndtert(),
                sakstype,
            ).right()
        }

        fun tilVilkårsvurdert(
            behandlingsinformasjon: Behandlingsinformasjon = this.behandlingsinformasjon,
            grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderinger.Søknadsbehandling = this.grunnlagsdataOgVilkårsvurderinger,
            clock: Clock,
        ): Vilkårsvurdert =
            opprett(
                id = id,
                opprettet = opprettet,
                sakId = sakId,
                saksnummer = saksnummer,
                søknad = søknad,
                oppgaveId = oppgaveId,
                behandlingsinformasjon = this.behandlingsinformasjon.patch(behandlingsinformasjon),
                fnr = fnr,
                fritekstTilBrev = fritekstTilBrev,
                stønadsperiode = stønadsperiode,
                grunnlagsdataOgVilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger,
                attesteringer = attesteringer,
                clock = clock,
                avkorting = avkorting.uhåndtert(),
                sakstype = sakstype,
            )

        override fun beregn(
            begrunnelse: String?,
            clock: Clock,
            satsFactory: SatsFactory,
            formuegrenserFactory: FormuegrenserFactory,
        ): Either<KunneIkkeBeregne, Beregnet> {
            return beregnInternal(
                søknadsbehandling = this.vilkårsvurder(vilkårsvurderinger, clock),
                begrunnelse = begrunnelse,
                clock = clock,
                beregningStrategyFactory = BeregningStrategyFactory(
                    clock = clock,
                    satsFactory = satsFactory,
                ),
                formuegrenserFactory = formuegrenserFactory,
            )
        }

        fun tilSimulert(simulering: Simulering): Simulert =
            Simulert(
                id,
                opprettet,
                sakId,
                saksnummer,
                søknad,
                oppgaveId,
                behandlingsinformasjon,
                fnr,
                beregning,
                simulering,
                fritekstTilBrev,
                stønadsperiode,
                grunnlagsdata,
                vilkårsvurderinger,
                attesteringer,
                avkorting,
                sakstype,
            )

        fun tilAttestering(
            saksbehandler: NavIdentBruker.Saksbehandler,
            fritekstTilBrev: String,
        ): TilAttestering.Innvilget {
            if (simulering.harFeilutbetalinger()) {
                /**
                 * Kun en nødbrems for tilfeller som i utgangspunktet skal være håndtert og forhindret av andre mekanismer.
                 */
                throw IllegalStateException("Simulering inneholder feilutbetalinger")
            }
            return TilAttestering.Innvilget(
                id,
                opprettet,
                sakId,
                saksnummer,
                søknad,
                oppgaveId,
                behandlingsinformasjon,
                fnr,
                beregning,
                simulering,
                saksbehandler,
                fritekstTilBrev,
                stønadsperiode,
                grunnlagsdata,
                vilkårsvurderinger,
                attesteringer,
                avkorting,
                sakstype,
            )
        }

        override fun leggTilUtenlandsopphold(
            utenlandsopphold: UtenlandsoppholdVilkår.Vurdert,
            clock: Clock,
        ): Either<KunneIkkeLeggeTilUtenlandsopphold, Vilkårsvurdert> {
            return valider(utenlandsopphold)
                .map { vilkårsvurder(vilkårsvurderinger.leggTil(utenlandsopphold), clock) }
        }

        override fun leggTilOpplysningspliktVilkår(
            opplysningspliktVilkår: OpplysningspliktVilkår.Vurdert,
            clock: Clock,
            formuegrenserFactory: FormuegrenserFactory,
        ): Either<KunneIkkeLeggeTilOpplysningsplikt, Vilkårsvurdert> {
            return valider(opplysningspliktVilkår)
                .map { vilkårsvurder(vilkårsvurderinger.leggTil(opplysningspliktVilkår), clock) }
        }

        override fun leggTilPensjonsVilkår(
            vilkår: PensjonsVilkår.Vurdert,
            clock: Clock,
            formuegrenserFactory: FormuegrenserFactory,
        ): Either<KunneIkkeLeggeTilPensjonsVilkår, Vilkårsvurdert> {
            return valider(vilkår)
                .map { vilkårsvurder(vilkårsvurderinger.leggTil(vilkår), clock) }
        }

        override fun leggTilFormuevilkår(
            vilkår: Vilkår.Formue.Vurdert,
            clock: Clock,
        ): Either<KunneIkkeLeggeTilFormuegrunnlag, Vilkårsvurdert> {
            return tilVilkårsvurdert(
                grunnlagsdataOgVilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger.oppdaterFormuevilkår(
                    vilkår = vilkår,
                ),
                clock = clock,
            ).right()
        }

        private fun vilkårsvurder(
            vilkårsvurderinger: Vilkårsvurderinger.Søknadsbehandling,
            clock: Clock,
        ): Vilkårsvurdert {
            return tilVilkårsvurdert(
                behandlingsinformasjon,
                grunnlagsdataOgVilkårsvurderinger.copy(vilkårsvurderinger = vilkårsvurderinger),
                clock,
            )
        }

        override fun leggTilUførevilkår(
            uførhet: Vilkår.Uførhet.Vurdert,
            clock: Clock,
            formuegrenserFactory: FormuegrenserFactory,
        ): Either<KunneIkkeLeggeTilUførevilkår, Vilkårsvurdert> {
            return valider(uførhet)
                .map { vilkårsvurder(vilkårsvurderinger.leggTil(uførhet), clock) }
        }

        override fun oppdaterStønadsperiode(
            oppdatertStønadsperiode: Stønadsperiode,
            clock: Clock,
            formuegrenserFactory: FormuegrenserFactory,
        ): Either<KunneIkkeOppdatereStønadsperiode, Vilkårsvurdert> {
            return grunnlagsdataOgVilkårsvurderinger.oppdaterStønadsperiode(
                stønadsperiode = oppdatertStønadsperiode,
                formuegrenserFactory = formuegrenserFactory,
            ).mapLeft {
                KunneIkkeOppdatereStønadsperiode.KunneIkkeOppdatereGrunnlagsdata(it)
            }.map {
                copy(
                    stønadsperiode = oppdatertStønadsperiode,
                    grunnlagsdata = it.grunnlagsdata,
                    vilkårsvurderinger = it.vilkårsvurderinger,
                ).vilkårsvurder(
                    vilkårsvurderinger = it.vilkårsvurderinger,
                    clock = clock,
                )
            }
        }
    }

    sealed class TilAttestering : Søknadsbehandling() {
        abstract val saksbehandler: NavIdentBruker
        abstract fun nyOppgaveId(nyOppgaveId: OppgaveId): TilAttestering
        abstract fun tilUnderkjent(attestering: Attestering): Underkjent
        abstract override val stønadsperiode: Stønadsperiode
        abstract override val attesteringer: Attesteringshistorikk
        abstract override val avkorting: AvkortingVedSøknadsbehandling.Håndtert

        data class Innvilget(
            override val id: UUID,
            override val opprettet: Tidspunkt,
            override val sakId: UUID,
            override val saksnummer: Saksnummer,
            override val søknad: Søknad.Journalført.MedOppgave,
            override val oppgaveId: OppgaveId,
            override val behandlingsinformasjon: Behandlingsinformasjon,
            override val fnr: Fnr,
            val beregning: Beregning,
            val simulering: Simulering,
            override val saksbehandler: NavIdentBruker.Saksbehandler,
            override val fritekstTilBrev: String,
            override val stønadsperiode: Stønadsperiode,
            override val grunnlagsdata: Grunnlagsdata,
            override val vilkårsvurderinger: Vilkårsvurderinger.Søknadsbehandling,
            override val attesteringer: Attesteringshistorikk,
            override val avkorting: AvkortingVedSøknadsbehandling.Håndtert,
            override val sakstype: Sakstype,
        ) : TilAttestering() {
            override val status: BehandlingsStatus = BehandlingsStatus.TIL_ATTESTERING_INNVILGET
            override val periode: Periode = stønadsperiode.periode

            init {
                kastHvisGrunnlagsdataOgVilkårsvurderingerPeriodenOgBehandlingensPerioderErUlike()
            }

            override fun accept(visitor: SøknadsbehandlingVisitor) {
                visitor.visit(this)
            }

            override fun nyOppgaveId(nyOppgaveId: OppgaveId): Innvilget {
                return this.copy(oppgaveId = nyOppgaveId)
            }

            override fun tilUnderkjent(attestering: Attestering): Underkjent.Innvilget {
                return Underkjent.Innvilget(
                    id,
                    opprettet,
                    sakId,
                    saksnummer,
                    søknad,
                    oppgaveId,
                    behandlingsinformasjon,
                    fnr,
                    beregning,
                    simulering,
                    saksbehandler,
                    attesteringer.leggTilNyAttestering(attestering),
                    fritekstTilBrev,
                    stønadsperiode,
                    grunnlagsdata,
                    vilkårsvurderinger,
                    avkorting,
                    sakstype,
                )
            }

            fun tilIverksatt(attestering: Attestering): Iverksatt.Innvilget {
                return Iverksatt.Innvilget(
                    id,
                    opprettet,
                    sakId,
                    saksnummer,
                    søknad,
                    oppgaveId,
                    behandlingsinformasjon,
                    fnr,
                    beregning,
                    simulering,
                    saksbehandler,
                    attesteringer.leggTilNyAttestering(attestering),
                    fritekstTilBrev,
                    stønadsperiode,
                    grunnlagsdata,
                    vilkårsvurderinger,
                    avkorting.iverksett(id),
                    sakstype,
                )
            }
        }

        sealed class Avslag : TilAttestering(), ErAvslag {
            final override val status: BehandlingsStatus = BehandlingsStatus.TIL_ATTESTERING_AVSLAG
            abstract override val stønadsperiode: Stønadsperiode

            data class UtenBeregning(
                override val id: UUID,
                override val opprettet: Tidspunkt,
                override val sakId: UUID,
                override val saksnummer: Saksnummer,
                override val søknad: Søknad.Journalført.MedOppgave,
                override val oppgaveId: OppgaveId,
                override val behandlingsinformasjon: Behandlingsinformasjon,
                override val fnr: Fnr,
                override val saksbehandler: NavIdentBruker.Saksbehandler,
                override val fritekstTilBrev: String,
                override val stønadsperiode: Stønadsperiode,
                override val grunnlagsdata: Grunnlagsdata,
                override val vilkårsvurderinger: Vilkårsvurderinger.Søknadsbehandling,
                override val attesteringer: Attesteringshistorikk,
                override val avkorting: AvkortingVedSøknadsbehandling.Håndtert.KanIkkeHåndtere,
                override val sakstype: Sakstype,
            ) : Avslag() {

                // TODO fiks typing/gyldig tilstand/vilkår fradrag?
                override val avslagsgrunner: List<Avslagsgrunn> = when (val vilkår = vilkårsvurderinger.resultat) {
                    is Vilkårsvurderingsresultat.Avslag -> vilkår.avslagsgrunner
                    is Vilkårsvurderingsresultat.Innvilget -> emptyList()
                    is Vilkårsvurderingsresultat.Uavklart -> emptyList()
                }

                override val periode: Periode = stønadsperiode.periode

                init {
                    kastHvisGrunnlagsdataOgVilkårsvurderingerPeriodenOgBehandlingensPerioderErUlike()
                }

                override fun accept(visitor: SøknadsbehandlingVisitor) {
                    visitor.visit(this)
                }

                override fun nyOppgaveId(nyOppgaveId: OppgaveId): UtenBeregning {
                    return this.copy(oppgaveId = nyOppgaveId)
                }

                override fun tilUnderkjent(attestering: Attestering): Underkjent.Avslag.UtenBeregning {
                    return Underkjent.Avslag.UtenBeregning(
                        id,
                        opprettet,
                        sakId,
                        saksnummer,
                        søknad,
                        oppgaveId,
                        behandlingsinformasjon,
                        fnr,
                        saksbehandler,
                        attesteringer.leggTilNyAttestering(attestering),
                        fritekstTilBrev,
                        stønadsperiode,
                        grunnlagsdata,
                        vilkårsvurderinger,
                        avkorting,
                        sakstype,
                    )
                }

                fun tilIverksatt(
                    attestering: Attestering,
                ): Iverksatt.Avslag.UtenBeregning {
                    return Iverksatt.Avslag.UtenBeregning(
                        id,
                        opprettet,
                        sakId,
                        saksnummer,
                        søknad,
                        oppgaveId,
                        behandlingsinformasjon,
                        fnr,
                        saksbehandler,
                        attesteringer.leggTilNyAttestering(attestering),
                        fritekstTilBrev,
                        stønadsperiode,
                        grunnlagsdata,
                        vilkårsvurderinger,
                        avkorting.iverksett(id),
                        sakstype,
                    )
                }
            }

            data class MedBeregning(
                override val id: UUID,
                override val opprettet: Tidspunkt,
                override val sakId: UUID,
                override val saksnummer: Saksnummer,
                override val søknad: Søknad.Journalført.MedOppgave,
                override val oppgaveId: OppgaveId,
                override val behandlingsinformasjon: Behandlingsinformasjon,
                override val fnr: Fnr,
                val beregning: Beregning,
                override val saksbehandler: NavIdentBruker.Saksbehandler,
                override val fritekstTilBrev: String,
                override val stønadsperiode: Stønadsperiode,
                override val grunnlagsdata: Grunnlagsdata,
                override val vilkårsvurderinger: Vilkårsvurderinger.Søknadsbehandling,
                override val attesteringer: Attesteringshistorikk,
                override val avkorting: AvkortingVedSøknadsbehandling.Håndtert.KanIkkeHåndtere,
                override val sakstype: Sakstype,
            ) : Avslag() {

                private val avslagsgrunnForBeregning: List<Avslagsgrunn> =
                    when (val vurdering = VurderAvslagGrunnetBeregning.vurderAvslagGrunnetBeregning(beregning)) {
                        is AvslagGrunnetBeregning.Ja -> listOf(vurdering.grunn.toAvslagsgrunn())
                        is AvslagGrunnetBeregning.Nei -> emptyList()
                    }

                // TODO fiks typing/gyldig tilstand/vilkår fradrag?
                override val avslagsgrunner: List<Avslagsgrunn> = when (val vilkår = vilkårsvurderinger.resultat) {
                    is Vilkårsvurderingsresultat.Avslag -> vilkår.avslagsgrunner
                    is Vilkårsvurderingsresultat.Innvilget -> emptyList()
                    is Vilkårsvurderingsresultat.Uavklart -> emptyList()
                } + avslagsgrunnForBeregning

                override val periode: Periode = stønadsperiode.periode

                init {
                    kastHvisGrunnlagsdataOgVilkårsvurderingerPeriodenOgBehandlingensPerioderErUlike()
                }

                override fun accept(visitor: SøknadsbehandlingVisitor) {
                    visitor.visit(this)
                }

                override fun nyOppgaveId(nyOppgaveId: OppgaveId): MedBeregning {
                    return this.copy(oppgaveId = nyOppgaveId)
                }

                override fun tilUnderkjent(attestering: Attestering): Underkjent.Avslag.MedBeregning {
                    return Underkjent.Avslag.MedBeregning(
                        id,
                        opprettet,
                        sakId,
                        saksnummer,
                        søknad,
                        oppgaveId,
                        behandlingsinformasjon,
                        fnr,
                        beregning,
                        saksbehandler,
                        attesteringer.leggTilNyAttestering(attestering),
                        fritekstTilBrev,
                        stønadsperiode,
                        grunnlagsdata,
                        vilkårsvurderinger,
                        avkorting,
                        sakstype,
                    )
                }

                fun tilIverksatt(
                    attestering: Attestering,
                ): Iverksatt.Avslag.MedBeregning {
                    return Iverksatt.Avslag.MedBeregning(
                        id,
                        opprettet,
                        sakId,
                        saksnummer,
                        søknad,
                        oppgaveId,
                        behandlingsinformasjon,
                        fnr,
                        beregning,
                        saksbehandler,
                        attesteringer.leggTilNyAttestering(attestering),
                        fritekstTilBrev,
                        stønadsperiode,
                        grunnlagsdata,
                        vilkårsvurderinger,
                        avkorting.iverksett(id),
                        sakstype,
                    )
                }
            }
        }
    }

    sealed class Underkjent : Søknadsbehandling() {
        abstract override val id: UUID
        abstract override val opprettet: Tidspunkt
        abstract override val sakId: UUID
        abstract override val saksnummer: Saksnummer
        abstract override val søknad: Søknad.Journalført.MedOppgave
        abstract override val oppgaveId: OppgaveId
        abstract override val behandlingsinformasjon: Behandlingsinformasjon
        abstract override val fnr: Fnr
        abstract val saksbehandler: NavIdentBruker.Saksbehandler
        abstract override val attesteringer: Attesteringshistorikk
        abstract override val stønadsperiode: Stønadsperiode
        abstract override val avkorting: AvkortingVedSøknadsbehandling.Håndtert

        abstract fun nyOppgaveId(nyOppgaveId: OppgaveId): Underkjent

        fun tilVilkårsvurdert(
            behandlingsinformasjon: Behandlingsinformasjon = this.behandlingsinformasjon,
            grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderinger.Søknadsbehandling = this.grunnlagsdataOgVilkårsvurderinger,
            clock: Clock,
        ): Vilkårsvurdert =
            opprett(
                id = id,
                opprettet = opprettet,
                sakId = sakId,
                saksnummer = saksnummer,
                søknad = søknad,
                oppgaveId = oppgaveId,
                behandlingsinformasjon = this.behandlingsinformasjon.patch(behandlingsinformasjon),
                fnr = fnr,
                fritekstTilBrev = fritekstTilBrev,
                stønadsperiode = stønadsperiode,
                grunnlagsdataOgVilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger,
                attesteringer = attesteringer,
                clock = clock,
                avkorting = avkorting.uhåndtert(),
                sakstype = sakstype,
            )

        data class Innvilget(
            override val id: UUID,
            override val opprettet: Tidspunkt,
            override val sakId: UUID,
            override val saksnummer: Saksnummer,
            override val søknad: Søknad.Journalført.MedOppgave,
            override val oppgaveId: OppgaveId,
            override val behandlingsinformasjon: Behandlingsinformasjon,
            override val fnr: Fnr,
            val beregning: Beregning,
            val simulering: Simulering,
            override val saksbehandler: NavIdentBruker.Saksbehandler,
            override val attesteringer: Attesteringshistorikk,
            override val fritekstTilBrev: String,
            override val stønadsperiode: Stønadsperiode,
            override val grunnlagsdata: Grunnlagsdata,
            override val vilkårsvurderinger: Vilkårsvurderinger.Søknadsbehandling,
            override val avkorting: AvkortingVedSøknadsbehandling.Håndtert,
            override val sakstype: Sakstype,
        ) : Underkjent() {

            override val status: BehandlingsStatus = BehandlingsStatus.UNDERKJENT_INNVILGET
            override val periode: Periode = stønadsperiode.periode

            init {
                kastHvisGrunnlagsdataOgVilkårsvurderingerPeriodenOgBehandlingensPerioderErUlike()
            }

            override fun nyOppgaveId(nyOppgaveId: OppgaveId): Innvilget {
                return this.copy(oppgaveId = nyOppgaveId)
            }

            override fun leggTilFradragsgrunnlag(
                fradragsgrunnlag: List<Grunnlag.Fradragsgrunnlag>,
                formuegrenserFactory: FormuegrenserFactory,
            ): Either<KunneIkkeLeggeTilFradragsgrunnlag, Vilkårsvurdert.Innvilget> {
                validerFradragsgrunnlag(fradragsgrunnlag).mapLeft {
                    return it.left()
                }

                return Vilkårsvurdert.Innvilget(
                    id = id,
                    opprettet = opprettet,
                    sakId = sakId,
                    saksnummer = saksnummer,
                    søknad = søknad,
                    oppgaveId = oppgaveId,
                    behandlingsinformasjon = this.behandlingsinformasjon.patch(behandlingsinformasjon),
                    fnr = fnr,
                    fritekstTilBrev = fritekstTilBrev,
                    stønadsperiode = stønadsperiode,
                    grunnlagsdata = Grunnlagsdata.tryCreate(
                        fradragsgrunnlag = fradragsgrunnlag,
                        bosituasjon = this.grunnlagsdata.bosituasjon,
                    ).getOrHandle {
                        return KunneIkkeLeggeTilFradragsgrunnlag.KunneIkkeEndreFradragsgrunnlag(it).left()
                    },
                    vilkårsvurderinger = vilkårsvurderinger,
                    attesteringer = attesteringer,
                    avkorting = avkorting.uhåndtert(),
                    sakstype,
                ).right()
            }

            override fun accept(visitor: SøknadsbehandlingVisitor) {
                visitor.visit(this)
            }

            override fun beregn(
                begrunnelse: String?,
                clock: Clock,
                satsFactory: SatsFactory,
                formuegrenserFactory: FormuegrenserFactory,
            ): Either<KunneIkkeBeregne, Beregnet> {
                return beregnInternal(
                    søknadsbehandling = this.vilkårsvurder(vilkårsvurderinger, clock),
                    begrunnelse = begrunnelse,
                    clock = clock,
                    beregningStrategyFactory = BeregningStrategyFactory(
                        clock = clock,
                        satsFactory = satsFactory,
                    ),
                    formuegrenserFactory = formuegrenserFactory,
                )
            }

            fun tilSimulert(simulering: Simulering): Simulert =
                Simulert(
                    id,
                    opprettet,
                    sakId,
                    saksnummer,
                    søknad,
                    oppgaveId,
                    behandlingsinformasjon,
                    fnr,
                    beregning,
                    simulering,
                    fritekstTilBrev,
                    stønadsperiode,
                    grunnlagsdata,
                    vilkårsvurderinger,
                    attesteringer,
                    avkorting,
                    sakstype,
                )

            fun tilAttestering(saksbehandler: NavIdentBruker.Saksbehandler): TilAttestering.Innvilget {
                if (simulering.harFeilutbetalinger()) {
                    /**
                     * Kun en nødbrems for tilfeller som i utgangspunktet skal være håndtert og forhindret av andre mekanismer.
                     */
                    throw IllegalStateException("Simulering inneholder feilutbetalinger")
                }
                return TilAttestering.Innvilget(
                    id,
                    opprettet,
                    sakId,
                    saksnummer,
                    søknad,
                    oppgaveId,
                    behandlingsinformasjon,
                    fnr,
                    beregning,
                    simulering,
                    saksbehandler,
                    fritekstTilBrev,
                    stønadsperiode,
                    grunnlagsdata,
                    vilkårsvurderinger,
                    attesteringer,
                    avkorting,
                    sakstype,
                )
            }

            override fun leggTilUtenlandsopphold(
                utenlandsopphold: UtenlandsoppholdVilkår.Vurdert,
                clock: Clock,
            ): Either<KunneIkkeLeggeTilUtenlandsopphold, Vilkårsvurdert> {
                return valider(utenlandsopphold)
                    .map { vilkårsvurder(vilkårsvurderinger.leggTil(utenlandsopphold), clock) }
            }

            override fun leggTilOpplysningspliktVilkår(
                opplysningspliktVilkår: OpplysningspliktVilkår.Vurdert,
                clock: Clock,
                formuegrenserFactory: FormuegrenserFactory,
            ): Either<KunneIkkeLeggeTilOpplysningsplikt, Vilkårsvurdert> {
                return valider(opplysningspliktVilkår)
                    .map { vilkårsvurder(vilkårsvurderinger.leggTil(opplysningspliktVilkår), clock) }
            }

            override fun leggTilPensjonsVilkår(
                vilkår: PensjonsVilkår.Vurdert,
                clock: Clock,
                formuegrenserFactory: FormuegrenserFactory,
            ): Either<KunneIkkeLeggeTilPensjonsVilkår, Vilkårsvurdert> {
                return valider(vilkår)
                    .map { vilkårsvurder(vilkårsvurderinger.leggTil(vilkår), clock) }
            }

            override fun leggTilFormuevilkår(
                vilkår: Vilkår.Formue.Vurdert,
                clock: Clock,
            ): Either<KunneIkkeLeggeTilFormuegrunnlag, Vilkårsvurdert> {
                return tilVilkårsvurdert(
                    grunnlagsdataOgVilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger.oppdaterFormuevilkår(
                        vilkår = vilkår,
                    ),
                    clock = clock,
                ).right()
            }

            private fun vilkårsvurder(
                vilkårsvurderinger: Vilkårsvurderinger.Søknadsbehandling,
                clock: Clock,
            ): Vilkårsvurdert {
                return tilVilkårsvurdert(
                    behandlingsinformasjon,
                    grunnlagsdataOgVilkårsvurderinger.copy(
                        vilkårsvurderinger = vilkårsvurderinger,
                    ),
                    clock,
                )
            }

            override fun leggTilUførevilkår(
                uførhet: Vilkår.Uførhet.Vurdert,
                clock: Clock,
                formuegrenserFactory: FormuegrenserFactory,
            ): Either<KunneIkkeLeggeTilUførevilkår, Vilkårsvurdert> {
                return valider(uførhet)
                    .map { vilkårsvurder(vilkårsvurderinger.leggTil(uførhet), clock) }
            }

            override fun oppdaterStønadsperiode(
                oppdatertStønadsperiode: Stønadsperiode,
                clock: Clock,
                formuegrenserFactory: FormuegrenserFactory,
            ): Either<KunneIkkeOppdatereStønadsperiode, Vilkårsvurdert> {
                return grunnlagsdataOgVilkårsvurderinger.oppdaterStønadsperiode(
                    stønadsperiode = oppdatertStønadsperiode,
                    formuegrenserFactory = formuegrenserFactory,
                ).mapLeft {
                    KunneIkkeOppdatereStønadsperiode.KunneIkkeOppdatereGrunnlagsdata(it)
                }.map {
                    copy(
                        stønadsperiode = oppdatertStønadsperiode,
                        grunnlagsdata = it.grunnlagsdata,
                        vilkårsvurderinger = it.vilkårsvurderinger,
                    ).vilkårsvurder(
                        vilkårsvurderinger = it.vilkårsvurderinger,
                        clock = clock,
                    )
                }
            }
        }

        sealed class Avslag : Underkjent(), ErAvslag {
            data class MedBeregning(
                override val id: UUID,
                override val opprettet: Tidspunkt,
                override val sakId: UUID,
                override val saksnummer: Saksnummer,
                override val søknad: Søknad.Journalført.MedOppgave,
                override val oppgaveId: OppgaveId,
                override val behandlingsinformasjon: Behandlingsinformasjon,
                override val fnr: Fnr,
                val beregning: Beregning,
                override val saksbehandler: NavIdentBruker.Saksbehandler,
                override val attesteringer: Attesteringshistorikk,
                override val fritekstTilBrev: String,
                override val stønadsperiode: Stønadsperiode,
                override val grunnlagsdata: Grunnlagsdata,
                override val vilkårsvurderinger: Vilkårsvurderinger.Søknadsbehandling,
                override val avkorting: AvkortingVedSøknadsbehandling.Håndtert.KanIkkeHåndtere,
                override val sakstype: Sakstype,
            ) : Avslag() {
                override val status: BehandlingsStatus = BehandlingsStatus.UNDERKJENT_AVSLAG
                override val periode: Periode = stønadsperiode.periode

                private val avslagsgrunnForBeregning: List<Avslagsgrunn> =
                    when (val vurdering = VurderAvslagGrunnetBeregning.vurderAvslagGrunnetBeregning(beregning)) {
                        is AvslagGrunnetBeregning.Ja -> listOf(vurdering.grunn.toAvslagsgrunn())
                        is AvslagGrunnetBeregning.Nei -> emptyList()
                    }

                init {
                    kastHvisGrunnlagsdataOgVilkårsvurderingerPeriodenOgBehandlingensPerioderErUlike()
                }

                override fun leggTilFradragsgrunnlag(
                    fradragsgrunnlag: List<Grunnlag.Fradragsgrunnlag>,
                    formuegrenserFactory: FormuegrenserFactory,
                ): Either<KunneIkkeLeggeTilFradragsgrunnlag, Vilkårsvurdert.Innvilget> {
                    validerFradragsgrunnlag(fradragsgrunnlag).mapLeft {
                        return it.left()
                    }

                    return Vilkårsvurdert.Innvilget(
                        id,
                        opprettet,
                        sakId,
                        saksnummer,
                        søknad,
                        oppgaveId,
                        this.behandlingsinformasjon.patch(behandlingsinformasjon),
                        fnr,
                        fritekstTilBrev,
                        stønadsperiode,
                        grunnlagsdata = Grunnlagsdata.tryCreate(
                            fradragsgrunnlag = fradragsgrunnlag,
                            bosituasjon = this.grunnlagsdata.bosituasjon,
                        ).getOrHandle {
                            return KunneIkkeLeggeTilFradragsgrunnlag.KunneIkkeEndreFradragsgrunnlag(it).left()
                        },
                        vilkårsvurderinger,
                        attesteringer,
                        avkorting.uhåndtert(),
                        sakstype,
                    ).right()
                }

                override fun nyOppgaveId(nyOppgaveId: OppgaveId): MedBeregning {
                    return this.copy(oppgaveId = nyOppgaveId)
                }

                override fun accept(visitor: SøknadsbehandlingVisitor) {
                    visitor.visit(this)
                }

                override fun beregn(
                    begrunnelse: String?,
                    clock: Clock,
                    satsFactory: SatsFactory,
                    formuegrenserFactory: FormuegrenserFactory,
                ): Either<KunneIkkeBeregne, Beregnet> {
                    return beregnInternal(
                        søknadsbehandling = this.vilkårsvurder(vilkårsvurderinger, clock),
                        begrunnelse = begrunnelse,
                        clock = clock,
                        beregningStrategyFactory = BeregningStrategyFactory(
                            clock = clock,
                            satsFactory = satsFactory,
                        ),
                        formuegrenserFactory = formuegrenserFactory,
                    )
                }

                fun tilAttestering(saksbehandler: NavIdentBruker.Saksbehandler): TilAttestering.Avslag.MedBeregning =
                    TilAttestering.Avslag.MedBeregning(
                        id,
                        opprettet,
                        sakId,
                        saksnummer,
                        søknad,
                        oppgaveId,
                        behandlingsinformasjon,
                        fnr,
                        beregning,
                        saksbehandler,
                        fritekstTilBrev,
                        stønadsperiode,
                        grunnlagsdata,
                        vilkårsvurderinger,
                        attesteringer,
                        avkorting,
                        sakstype,
                    )

                // TODO fiks typing/gyldig tilstand/vilkår fradrag?
                override val avslagsgrunner: List<Avslagsgrunn> = when (val vilkår = vilkårsvurderinger.resultat) {
                    is Vilkårsvurderingsresultat.Avslag -> vilkår.avslagsgrunner
                    is Vilkårsvurderingsresultat.Innvilget -> emptyList()
                    is Vilkårsvurderingsresultat.Uavklart -> emptyList()
                } + avslagsgrunnForBeregning

                override fun leggTilUtenlandsopphold(
                    utenlandsopphold: UtenlandsoppholdVilkår.Vurdert,
                    clock: Clock,
                ): Either<KunneIkkeLeggeTilUtenlandsopphold, Vilkårsvurdert> {
                    return valider(utenlandsopphold)
                        .map { vilkårsvurder(vilkårsvurderinger.leggTil(utenlandsopphold), clock) }
                }

                override fun leggTilOpplysningspliktVilkår(
                    opplysningspliktVilkår: OpplysningspliktVilkår.Vurdert,
                    clock: Clock,
                    formuegrenserFactory: FormuegrenserFactory,
                ): Either<KunneIkkeLeggeTilOpplysningsplikt, Vilkårsvurdert> {
                    return valider(opplysningspliktVilkår)
                        .map { vilkårsvurder(vilkårsvurderinger.leggTil(opplysningspliktVilkår), clock) }
                }

                override fun leggTilPensjonsVilkår(
                    vilkår: PensjonsVilkår.Vurdert,
                    clock: Clock,
                    formuegrenserFactory: FormuegrenserFactory,
                ): Either<KunneIkkeLeggeTilPensjonsVilkår, Vilkårsvurdert> {
                    return valider(vilkår)
                        .map { vilkårsvurder(vilkårsvurderinger.leggTil(vilkår), clock) }
                }

                override fun leggTilFormuevilkår(
                    vilkår: Vilkår.Formue.Vurdert,
                    clock: Clock,
                ): Either<KunneIkkeLeggeTilFormuegrunnlag, Vilkårsvurdert> {
                    return tilVilkårsvurdert(
                        grunnlagsdataOgVilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger.oppdaterFormuevilkår(
                            vilkår = vilkår,
                        ),
                        clock = clock,
                    ).right()
                }

                private fun vilkårsvurder(
                    vilkårsvurderinger: Vilkårsvurderinger.Søknadsbehandling,
                    clock: Clock,
                ): Vilkårsvurdert {
                    return tilVilkårsvurdert(
                        behandlingsinformasjon,
                        grunnlagsdataOgVilkårsvurderinger.copy(
                            vilkårsvurderinger = vilkårsvurderinger,
                        ),
                        clock,
                    )
                }

                override fun leggTilUførevilkår(
                    uførhet: Vilkår.Uførhet.Vurdert,
                    clock: Clock,
                    formuegrenserFactory: FormuegrenserFactory,
                ): Either<KunneIkkeLeggeTilUførevilkår, Vilkårsvurdert> {
                    return valider(uførhet)
                        .map { vilkårsvurder(vilkårsvurderinger.leggTil(uførhet), clock) }
                }

                override fun oppdaterStønadsperiode(
                    oppdatertStønadsperiode: Stønadsperiode,
                    clock: Clock,
                    formuegrenserFactory: FormuegrenserFactory,
                ): Either<KunneIkkeOppdatereStønadsperiode, Vilkårsvurdert> {
                    return grunnlagsdataOgVilkårsvurderinger.oppdaterStønadsperiode(
                        stønadsperiode = oppdatertStønadsperiode,
                        formuegrenserFactory = formuegrenserFactory,
                    ).mapLeft {
                        KunneIkkeOppdatereStønadsperiode.KunneIkkeOppdatereGrunnlagsdata(it)
                    }.map {
                        copy(
                            stønadsperiode = oppdatertStønadsperiode,
                            grunnlagsdata = it.grunnlagsdata,
                            vilkårsvurderinger = it.vilkårsvurderinger,
                        ).vilkårsvurder(
                            vilkårsvurderinger = it.vilkårsvurderinger,
                            clock = clock,
                        )
                    }
                }
            }

            data class UtenBeregning(
                override val id: UUID,
                override val opprettet: Tidspunkt,
                override val sakId: UUID,
                override val saksnummer: Saksnummer,
                override val søknad: Søknad.Journalført.MedOppgave,
                override val oppgaveId: OppgaveId,
                override val behandlingsinformasjon: Behandlingsinformasjon,
                override val fnr: Fnr,
                override val saksbehandler: NavIdentBruker.Saksbehandler,
                override val attesteringer: Attesteringshistorikk,
                override val fritekstTilBrev: String,
                override val stønadsperiode: Stønadsperiode,
                override val grunnlagsdata: Grunnlagsdata,
                override val vilkårsvurderinger: Vilkårsvurderinger.Søknadsbehandling,
                override val avkorting: AvkortingVedSøknadsbehandling.Håndtert.KanIkkeHåndtere,
                override val sakstype: Sakstype,
            ) : Avslag() {
                override val status: BehandlingsStatus = BehandlingsStatus.UNDERKJENT_AVSLAG
                override val periode: Periode = stønadsperiode.periode

                init {
                    kastHvisGrunnlagsdataOgVilkårsvurderingerPeriodenOgBehandlingensPerioderErUlike()
                }

                override fun nyOppgaveId(nyOppgaveId: OppgaveId): UtenBeregning {
                    return this.copy(oppgaveId = nyOppgaveId)
                }

                override fun accept(visitor: SøknadsbehandlingVisitor) {
                    visitor.visit(this)
                }

                fun tilAttestering(saksbehandler: NavIdentBruker.Saksbehandler): TilAttestering.Avslag.UtenBeregning =
                    TilAttestering.Avslag.UtenBeregning(
                        id,
                        opprettet,
                        sakId,
                        saksnummer,
                        søknad,
                        oppgaveId,
                        behandlingsinformasjon,
                        fnr,
                        saksbehandler,
                        fritekstTilBrev,
                        stønadsperiode,
                        grunnlagsdata,
                        vilkårsvurderinger,
                        attesteringer,
                        avkorting,
                        sakstype,
                    )

                // TODO fiks typing/gyldig tilstand/vilkår fradrag?
                override val avslagsgrunner: List<Avslagsgrunn> = when (val vilkår = vilkårsvurderinger.resultat) {
                    is Vilkårsvurderingsresultat.Avslag -> vilkår.avslagsgrunner
                    is Vilkårsvurderingsresultat.Innvilget -> emptyList()
                    is Vilkårsvurderingsresultat.Uavklart -> emptyList()
                }

                override fun leggTilUtenlandsopphold(
                    utenlandsopphold: UtenlandsoppholdVilkår.Vurdert,
                    clock: Clock,
                ): Either<KunneIkkeLeggeTilUtenlandsopphold, Vilkårsvurdert> {
                    return valider(utenlandsopphold)
                        .map { vilkårsvurder(vilkårsvurderinger.leggTil(utenlandsopphold), clock) }
                }

                override fun leggTilOpplysningspliktVilkår(
                    opplysningspliktVilkår: OpplysningspliktVilkår.Vurdert,
                    clock: Clock,
                    formuegrenserFactory: FormuegrenserFactory,
                ): Either<KunneIkkeLeggeTilOpplysningsplikt, Vilkårsvurdert> {
                    return valider(opplysningspliktVilkår)
                        .map { vilkårsvurder(vilkårsvurderinger.leggTil(opplysningspliktVilkår), clock) }
                }

                override fun leggTilPensjonsVilkår(
                    vilkår: PensjonsVilkår.Vurdert,
                    clock: Clock,
                    formuegrenserFactory: FormuegrenserFactory,
                ): Either<KunneIkkeLeggeTilPensjonsVilkår, Vilkårsvurdert> {
                    return valider(vilkår)
                        .map { vilkårsvurder(vilkårsvurderinger.leggTil(vilkår), clock) }
                }

                override fun leggTilFormuevilkår(
                    vilkår: Vilkår.Formue.Vurdert,
                    clock: Clock,
                ): Either<KunneIkkeLeggeTilFormuegrunnlag, Vilkårsvurdert> {
                    return tilVilkårsvurdert(
                        grunnlagsdataOgVilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger.oppdaterFormuevilkår(
                            vilkår = vilkår,
                        ),
                        clock = clock,
                    ).right()
                }

                private fun vilkårsvurder(
                    vilkårsvurderinger: Vilkårsvurderinger.Søknadsbehandling,
                    clock: Clock,
                ): Vilkårsvurdert {
                    return tilVilkårsvurdert(
                        behandlingsinformasjon,
                        grunnlagsdataOgVilkårsvurderinger.copy(
                            vilkårsvurderinger = vilkårsvurderinger,
                        ),
                        clock,
                    )
                }

                override fun leggTilUførevilkår(
                    uførhet: Vilkår.Uførhet.Vurdert,
                    clock: Clock,
                    formuegrenserFactory: FormuegrenserFactory,
                ): Either<KunneIkkeLeggeTilUførevilkår, Vilkårsvurdert> {
                    return valider(uførhet)
                        .map { vilkårsvurder(vilkårsvurderinger.leggTil(uførhet), clock) }
                }

                override fun oppdaterStønadsperiode(
                    oppdatertStønadsperiode: Stønadsperiode,
                    clock: Clock,
                    formuegrenserFactory: FormuegrenserFactory,
                ): Either<KunneIkkeOppdatereStønadsperiode, Vilkårsvurdert> {
                    return grunnlagsdataOgVilkårsvurderinger.oppdaterStønadsperiode(
                        stønadsperiode = oppdatertStønadsperiode,
                        formuegrenserFactory = formuegrenserFactory,
                    ).mapLeft {
                        KunneIkkeOppdatereStønadsperiode.KunneIkkeOppdatereGrunnlagsdata(it)
                    }.map {
                        copy(
                            stønadsperiode = oppdatertStønadsperiode,
                            grunnlagsdata = it.grunnlagsdata,
                            vilkårsvurderinger = it.vilkårsvurderinger,
                        ).vilkårsvurder(
                            vilkårsvurderinger = it.vilkårsvurderinger,
                            clock = clock,
                        )
                    }
                }
            }
        }
    }

    sealed class Iverksatt : Søknadsbehandling() {
        abstract override val id: UUID
        abstract override val opprettet: Tidspunkt
        abstract override val sakId: UUID
        abstract override val saksnummer: Saksnummer
        abstract override val søknad: Søknad.Journalført.MedOppgave
        abstract override val oppgaveId: OppgaveId
        abstract override val behandlingsinformasjon: Behandlingsinformasjon
        abstract override val fnr: Fnr
        abstract val saksbehandler: NavIdentBruker.Saksbehandler
        abstract override val attesteringer: Attesteringshistorikk
        abstract override val stønadsperiode: Stønadsperiode
        abstract override val avkorting: AvkortingVedSøknadsbehandling.Iverksatt

        data class Innvilget(
            override val id: UUID,
            override val opprettet: Tidspunkt,
            override val sakId: UUID,
            override val saksnummer: Saksnummer,
            override val søknad: Søknad.Journalført.MedOppgave,
            override val oppgaveId: OppgaveId,
            override val behandlingsinformasjon: Behandlingsinformasjon,
            override val fnr: Fnr,
            val beregning: Beregning,
            val simulering: Simulering,
            override val saksbehandler: NavIdentBruker.Saksbehandler,
            override val attesteringer: Attesteringshistorikk,
            override val fritekstTilBrev: String,
            override val stønadsperiode: Stønadsperiode,
            override val grunnlagsdata: Grunnlagsdata,
            override val vilkårsvurderinger: Vilkårsvurderinger.Søknadsbehandling,
            override val avkorting: AvkortingVedSøknadsbehandling.Iverksatt,
            override val sakstype: Sakstype,
        ) : Iverksatt() {
            override val status: BehandlingsStatus = BehandlingsStatus.IVERKSATT_INNVILGET
            override val periode: Periode = stønadsperiode.periode

            init {
                kastHvisGrunnlagsdataOgVilkårsvurderingerPeriodenOgBehandlingensPerioderErUlike()
            }

            override fun accept(visitor: SøknadsbehandlingVisitor) {
                visitor.visit(this)
            }
        }

        sealed class Avslag : Iverksatt(), ErAvslag {
            data class MedBeregning(
                override val id: UUID,
                override val opprettet: Tidspunkt,
                override val sakId: UUID,
                override val saksnummer: Saksnummer,
                override val søknad: Søknad.Journalført.MedOppgave,
                override val oppgaveId: OppgaveId,
                override val behandlingsinformasjon: Behandlingsinformasjon,
                override val fnr: Fnr,
                val beregning: Beregning,
                override val saksbehandler: NavIdentBruker.Saksbehandler,
                override val attesteringer: Attesteringshistorikk,
                override val fritekstTilBrev: String,
                override val stønadsperiode: Stønadsperiode,
                override val grunnlagsdata: Grunnlagsdata,
                override val vilkårsvurderinger: Vilkårsvurderinger.Søknadsbehandling,
                override val avkorting: AvkortingVedSøknadsbehandling.Iverksatt.KanIkkeHåndtere,
                override val sakstype: Sakstype,
            ) : Avslag() {
                override val status: BehandlingsStatus = BehandlingsStatus.IVERKSATT_AVSLAG
                override val periode: Periode = stønadsperiode.periode

                init {
                    kastHvisGrunnlagsdataOgVilkårsvurderingerPeriodenOgBehandlingensPerioderErUlike()
                }

                override fun accept(visitor: SøknadsbehandlingVisitor) {
                    visitor.visit(this)
                }

                private val avslagsgrunnForBeregning: List<Avslagsgrunn> =
                    when (val vurdering = VurderAvslagGrunnetBeregning.vurderAvslagGrunnetBeregning(beregning)) {
                        is AvslagGrunnetBeregning.Ja -> listOf(vurdering.grunn.toAvslagsgrunn())
                        is AvslagGrunnetBeregning.Nei -> emptyList()
                    }

                // TODO fiks typing/gyldig tilstand/vilkår fradrag?
                override val avslagsgrunner: List<Avslagsgrunn> = when (val vilkår = vilkårsvurderinger.resultat) {
                    is Vilkårsvurderingsresultat.Avslag -> vilkår.avslagsgrunner
                    is Vilkårsvurderingsresultat.Innvilget -> emptyList()
                    is Vilkårsvurderingsresultat.Uavklart -> emptyList()
                } + avslagsgrunnForBeregning
            }

            data class UtenBeregning(
                override val id: UUID,
                override val opprettet: Tidspunkt,
                override val sakId: UUID,
                override val saksnummer: Saksnummer,
                override val søknad: Søknad.Journalført.MedOppgave,
                override val oppgaveId: OppgaveId,
                override val behandlingsinformasjon: Behandlingsinformasjon,
                override val fnr: Fnr,
                override val saksbehandler: NavIdentBruker.Saksbehandler,
                override val attesteringer: Attesteringshistorikk,
                override val fritekstTilBrev: String,
                override val stønadsperiode: Stønadsperiode,
                override val grunnlagsdata: Grunnlagsdata,
                override val vilkårsvurderinger: Vilkårsvurderinger.Søknadsbehandling,
                override val avkorting: AvkortingVedSøknadsbehandling.Iverksatt.KanIkkeHåndtere,
                override val sakstype: Sakstype,
            ) : Avslag() {
                override val status: BehandlingsStatus = BehandlingsStatus.IVERKSATT_AVSLAG
                override val periode: Periode = stønadsperiode.periode

                init {
                    kastHvisGrunnlagsdataOgVilkårsvurderingerPeriodenOgBehandlingensPerioderErUlike()
                }

                override fun accept(visitor: SøknadsbehandlingVisitor) {
                    visitor.visit(this)
                }

                // TODO fiks typing/gyldig tilstand/vilkår fradrag?
                override val avslagsgrunner: List<Avslagsgrunn> = when (val vilkår = vilkårsvurderinger.resultat) {
                    is Vilkårsvurderingsresultat.Avslag -> vilkår.avslagsgrunner
                    is Vilkårsvurderingsresultat.Innvilget -> emptyList()
                    is Vilkårsvurderingsresultat.Uavklart -> emptyList()
                }
            }
        }
    }
}

enum class BehandlingsStatus {
    OPPRETTET,
    VILKÅRSVURDERT_INNVILGET,
    VILKÅRSVURDERT_AVSLAG,
    BEREGNET_INNVILGET,
    BEREGNET_AVSLAG,
    SIMULERT,
    TIL_ATTESTERING_INNVILGET,
    TIL_ATTESTERING_AVSLAG,
    UNDERKJENT_INNVILGET,
    UNDERKJENT_AVSLAG,
    IVERKSATT_INNVILGET,
    IVERKSATT_AVSLAG;

    companion object {
        @Suppress("MemberVisibilityCanBePrivate")
        fun åpneBeregnetSøknadsbehandlinger() = listOf(
            BEREGNET_INNVILGET,
            BEREGNET_AVSLAG,
            SIMULERT,
            TIL_ATTESTERING_INNVILGET,
            TIL_ATTESTERING_AVSLAG,
            UNDERKJENT_INNVILGET,
            UNDERKJENT_AVSLAG,
        )

        fun åpneBeregnetSøknadsbehandlingerKommaseparert(): String =
            åpneBeregnetSøknadsbehandlinger().joinToString(",") { "'$it'" }
    }
}

sealed interface KunneIkkeIverksette {
    object AttestantOgSaksbehandlerKanIkkeVæreSammePerson : KunneIkkeIverksette
    data class KunneIkkeUtbetale(val utbetalingFeilet: UtbetalingFeilet) : KunneIkkeIverksette
    object FantIkkeBehandling : KunneIkkeIverksette
    object KunneIkkeGenerereVedtaksbrev : KunneIkkeIverksette
    object AvkortingErUfullstendig : KunneIkkeIverksette
    object HarBlittAnnullertAvEnAnnen : KunneIkkeIverksette
    object HarAlleredeBlittAvkortetAvEnAnnen : KunneIkkeIverksette
    object KunneIkkeOpprettePlanlagtKontrollsamtale : KunneIkkeIverksette
    object LagringFeilet : KunneIkkeIverksette
}

// Her trikses det litt for å få til at funksjonen returnerer den samme konkrete typen som den kalles på.
// Teoretisk sett skal ikke UNCHECKED_CAST være noe problem i dette tilfellet siden T er begrenset til subklasser av Søknadsbehandling.
// ... i hvert fall så lenge alle subklassene av Søknadsbehandling er data classes
@Suppress("UNCHECKED_CAST")
fun <T : Søknadsbehandling> T.medFritekstTilBrev(fritekstTilBrev: String): T =
    (
        // Her caster vi til Søknadsbehandling for å unngå å måtte ha en else-branch
        when (val x = this as Søknadsbehandling) {
            is Søknadsbehandling.Beregnet.Avslag -> x.copy(fritekstTilBrev = fritekstTilBrev)
            is Søknadsbehandling.Beregnet.Innvilget -> x.copy(fritekstTilBrev = fritekstTilBrev)
            is Søknadsbehandling.Iverksatt.Avslag.MedBeregning -> x.copy(fritekstTilBrev = fritekstTilBrev)
            is Søknadsbehandling.Iverksatt.Avslag.UtenBeregning -> x.copy(fritekstTilBrev = fritekstTilBrev)
            is Søknadsbehandling.Iverksatt.Innvilget -> x.copy(fritekstTilBrev = fritekstTilBrev)
            is Søknadsbehandling.Simulert -> x.copy(fritekstTilBrev = fritekstTilBrev)
            is Søknadsbehandling.TilAttestering.Avslag.MedBeregning -> x.copy(fritekstTilBrev = fritekstTilBrev)
            is Søknadsbehandling.TilAttestering.Avslag.UtenBeregning -> x.copy(fritekstTilBrev = fritekstTilBrev)
            is Søknadsbehandling.TilAttestering.Innvilget -> x.copy(fritekstTilBrev = fritekstTilBrev)
            is Søknadsbehandling.Underkjent.Avslag.MedBeregning -> x.copy(fritekstTilBrev = fritekstTilBrev)
            is Søknadsbehandling.Underkjent.Avslag.UtenBeregning -> x.copy(fritekstTilBrev = fritekstTilBrev)
            is Søknadsbehandling.Underkjent.Innvilget -> x.copy(fritekstTilBrev = fritekstTilBrev)
            is Søknadsbehandling.Vilkårsvurdert.Avslag -> x.copy(fritekstTilBrev = fritekstTilBrev)
            is Søknadsbehandling.Vilkårsvurdert.Innvilget -> x.copy(fritekstTilBrev = fritekstTilBrev)
            is Søknadsbehandling.Vilkårsvurdert.Uavklart -> x.copy(fritekstTilBrev = fritekstTilBrev)
            is LukketSøknadsbehandling -> throw IllegalArgumentException("Det støttes ikke å endre fritekstTilBrev på en lukket søknadsbehandling.")
        }
        // ... og så caster vi tilbake til T for at Kotlin skal henge med i svingene
        ) as T
