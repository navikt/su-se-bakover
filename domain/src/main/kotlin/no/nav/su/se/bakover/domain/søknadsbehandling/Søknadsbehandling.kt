package no.nav.su.se.bakover.domain.søknadsbehandling

import arrow.core.Either
import arrow.core.NonEmptyList
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.nonEmptyListOf
import arrow.core.right
import no.nav.su.se.bakover.common.Fnr
import no.nav.su.se.bakover.common.NavIdentBruker
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.periode.inneholderAlle
import no.nav.su.se.bakover.common.toNonEmptyList
import no.nav.su.se.bakover.domain.avkorting.AvkortingVedSøknadsbehandling
import no.nav.su.se.bakover.domain.avkorting.Avkortingsplan
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.behandling.Attesteringshistorikk
import no.nav.su.se.bakover.domain.behandling.AvslagGrunnetBeregning
import no.nav.su.se.bakover.domain.behandling.BehandlingMedAttestering
import no.nav.su.se.bakover.domain.behandling.BehandlingMedOppgave
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
import no.nav.su.se.bakover.domain.grunnlag.OpplysningspliktBeskrivelse
import no.nav.su.se.bakover.domain.grunnlag.Opplysningspliktgrunnlag
import no.nav.su.se.bakover.domain.grunnlag.krevAlleVilkårInnvilget
import no.nav.su.se.bakover.domain.grunnlag.krevMinstEttAvslag
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.sak.Saksnummer
import no.nav.su.se.bakover.domain.sak.Sakstype
import no.nav.su.se.bakover.domain.sak.SimulerUtbetalingFeilet
import no.nav.su.se.bakover.domain.satser.SatsFactory
import no.nav.su.se.bakover.domain.søknad.LukkSøknadCommand
import no.nav.su.se.bakover.domain.søknad.Søknad
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling.Vilkårsvurdert.Companion.opprett
import no.nav.su.se.bakover.domain.vilkår.FamiliegjenforeningVilkår
import no.nav.su.se.bakover.domain.vilkår.FastOppholdINorgeVilkår
import no.nav.su.se.bakover.domain.vilkår.FlyktningVilkår
import no.nav.su.se.bakover.domain.vilkår.FormueVilkår
import no.nav.su.se.bakover.domain.vilkår.FormuegrenserFactory
import no.nav.su.se.bakover.domain.vilkår.InstitusjonsoppholdVilkår
import no.nav.su.se.bakover.domain.vilkår.LovligOppholdVilkår
import no.nav.su.se.bakover.domain.vilkår.OpplysningspliktVilkår
import no.nav.su.se.bakover.domain.vilkår.PensjonsVilkår
import no.nav.su.se.bakover.domain.vilkår.PersonligOppmøteVilkår
import no.nav.su.se.bakover.domain.vilkår.UføreVilkår
import no.nav.su.se.bakover.domain.vilkår.UtenlandsoppholdVilkår
import no.nav.su.se.bakover.domain.vilkår.Vilkår
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderingsresultat
import no.nav.su.se.bakover.domain.vilkår.VurderingsperiodeOpplysningsplikt
import no.nav.su.se.bakover.domain.vilkår.inneholderAlle
import no.nav.su.se.bakover.domain.visitor.Visitable
import java.time.Clock
import java.util.UUID

sealed class Søknadsbehandling : BehandlingMedOppgave, BehandlingMedAttestering, Visitable<SøknadsbehandlingVisitor> {
    abstract val søknad: Søknad.Journalført.MedOppgave

    abstract val stønadsperiode: Stønadsperiode?
    abstract override val grunnlagsdata: Grunnlagsdata
    abstract override val vilkårsvurderinger: Vilkårsvurderinger.Søknadsbehandling
    abstract override val attesteringer: Attesteringshistorikk
    abstract val avkorting: AvkortingVedSøknadsbehandling

    // TODO ia: fritekst bør flyttes ut av denne klassen og til et eget konsept (som også omfatter fritekst på revurderinger)
    abstract val fritekstTilBrev: String

    val erIverksatt: Boolean by lazy { this is Iverksatt.Avslag || this is Iverksatt.Innvilget }
    val erLukket: Boolean by lazy { this is LukketSøknadsbehandling }

    abstract val saksbehandler: NavIdentBruker.Saksbehandler?
    abstract val beregning: Beregning?
    abstract val simulering: Simulering?

    fun erÅpen(): Boolean {
        return !(erIverksatt || erLukket)
    }

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

    fun lukkSøknadsbehandlingOgSøknad(
        lukkSøknadCommand: LukkSøknadCommand,
    ): Either<KunneIkkeLukkeSøknadsbehandling, LukketSøknadsbehandling> = LukketSøknadsbehandling.tryCreate(
        søknadsbehandlingSomSkalLukkes = this,
        lukkSøknadCommand = lukkSøknadCommand,
    )

    private fun validerFradragsgrunnlag(fradragsgrunnlag: List<Grunnlag.Fradragsgrunnlag>): Either<KunneIkkeLeggeTilGrunnlag.KunneIkkeLeggeTilFradragsgrunnlag, Unit> {
        if (fradragsgrunnlag.isNotEmpty()) {
            if (!periode.inneholderAlle(fradragsgrunnlag.perioder())) {
                return KunneIkkeLeggeTilGrunnlag.KunneIkkeLeggeTilFradragsgrunnlag.GrunnlagetMåVæreInnenforBehandlingsperioden.left()
            }
        }
        return Unit.right()
    }

    fun leggTilFradragsgrunnlag(
        saksbehandler: NavIdentBruker.Saksbehandler,
        fradragsgrunnlag: List<Grunnlag.Fradragsgrunnlag>,
    ) = vilkårsvurder(saksbehandler).let {
        when (it) {
            is KanBeregnes -> leggTilFradragsgrunnlagInternal(saksbehandler, fradragsgrunnlag)
            else -> KunneIkkeLeggeTilGrunnlag.KunneIkkeLeggeTilFradragsgrunnlag.IkkeLovÅLeggeTilFradragIDenneStatusen(
                this::class,
            ).left()
        }
    }

    private fun leggTilFradragsgrunnlagInternal(
        saksbehandler: NavIdentBruker.Saksbehandler,
        fradragsgrunnlag: List<Grunnlag.Fradragsgrunnlag>,
    ) = validerFradragsgrunnlag(fradragsgrunnlag)
        .map {
            copyInternal(
                grunnlagsdataOgVilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger.leggTilFradragsgrunnlag(
                    fradragsgrunnlag,
                ),
            ).vilkårsvurder(saksbehandler) as Vilkårsvurdert.Innvilget // TODO cast
        }

    /**
     * TODO("bør vi skille på oppdatering og fullføring (ufullstendig vs fullstendig bosituasjon)")
     */
    fun oppdaterBosituasjon(
        saksbehandler: NavIdentBruker.Saksbehandler,
        bosituasjon: Grunnlag.Bosituasjon,
    ) = if (this is KanOppdaterePeriodeGrunnlagVilkår) {
        oppdaterBosituasjonInternal(saksbehandler, bosituasjon).right()
    } else {
        KunneIkkeLeggeTilGrunnlag.KunneIkkeOppdatereBosituasjon.UgyldigTilstand(
            this::class,
            Vilkårsvurdert::class,
        ).left()
    }

    private fun oppdaterBosituasjonInternal(
        saksbehandler: NavIdentBruker.Saksbehandler,
        bosituasjon: Grunnlag.Bosituasjon,
    ) = grunnlagsdataOgVilkårsvurderinger.oppdaterBosituasjon(listOf(bosituasjon)).let {
        copyInternal(grunnlagsdataOgVilkårsvurderinger = it).vilkårsvurder(saksbehandler)
    }

    /**
     * Eksponerer deler av subklassenes copy-konstruktør slik at vi kan eliminere behovet for duplikate implementasjoner i hver subklasse.
     */
    protected open fun copyInternal(
        stønadsperiode: Stønadsperiode = this.stønadsperiode!!,
        grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderinger.Søknadsbehandling = this.grunnlagsdataOgVilkårsvurderinger,
    ) = this

    fun leggTilUtenlandsopphold(
        saksbehandler: NavIdentBruker.Saksbehandler,
        utenlandsopphold: UtenlandsoppholdVilkår.Vurdert,
    ) = if (this is KanOppdaterePeriodeGrunnlagVilkår) {
        leggTilUtenlandsoppholdInternal(saksbehandler, utenlandsopphold)
    } else {
        KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilUtenlandsopphold.IkkeLovÅLeggeTilUtenlandsoppholdIDenneStatusen(
            fra = this::class,
            til = Vilkårsvurdert::class,
        ).left()
    }

    private fun leggTilUtenlandsoppholdInternal(
        saksbehandler: NavIdentBruker.Saksbehandler,
        vilkår: UtenlandsoppholdVilkår.Vurdert,
    ) = valider<KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilUtenlandsopphold>(vilkår)
        .map {
            copyInternal(
                grunnlagsdataOgVilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger.leggTil(vilkår),
            ).vilkårsvurder(saksbehandler)
        }

    fun vilkårsvurder(saksbehandler: NavIdentBruker.Saksbehandler): Vilkårsvurdert = opprett(
        id = id,
        opprettet = opprettet,
        sakId = sakId,
        saksnummer = saksnummer,
        søknad = søknad,
        oppgaveId = oppgaveId,
        fnr = fnr,
        fritekstTilBrev = fritekstTilBrev,
        stønadsperiode = stønadsperiode!!,
        grunnlagsdataOgVilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger,
        attesteringer = attesteringer,
        avkorting = avkorting.uhåndtert(),
        sakstype = sakstype,
        saksbehandler = saksbehandler,
    )

    interface KanOppdaterePeriodeGrunnlagVilkår
    interface KanBeregnes : KanOppdaterePeriodeGrunnlagVilkår {
        val avkorting: AvkortingVedSøknadsbehandling.Uhåndtert
    }

    fun leggTilFormuevilkår(
        saksbehandler: NavIdentBruker.Saksbehandler,
        vilkår: FormueVilkår.Vurdert,
    ) = if (this is KanOppdaterePeriodeGrunnlagVilkår) {
        leggTilFormuevilkårInternal(saksbehandler, vilkår)
    } else {
        KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilFormuevilkår.UgyldigTilstand(
            fra = this::class,
            til = Vilkårsvurdert::class,
        ).left()
    }

    private fun leggTilFormuevilkårInternal(
        saksbehandler: NavIdentBruker.Saksbehandler,
        vilkår: FormueVilkår.Vurdert,
    ) = valider<KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilFormuevilkår>(vilkår)
        .map {
            copyInternal(
                grunnlagsdataOgVilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger.oppdaterFormuevilkår(vilkår),
            ).vilkårsvurder(saksbehandler)
        }

    fun oppdaterStønadsperiode(
        saksbehandler: NavIdentBruker.Saksbehandler,
        oppdatertStønadsperiode: Stønadsperiode,
        formuegrenserFactory: FormuegrenserFactory,
        clock: Clock,
    ): Either<KunneIkkeOppdatereStønadsperiode, Vilkårsvurdert> = if (this is KanOppdaterePeriodeGrunnlagVilkår) {
        oppdaterStønadsperiodeInternal(saksbehandler, oppdatertStønadsperiode, formuegrenserFactory, clock)
    } else {
        KunneIkkeOppdatereStønadsperiode.UgyldigTilstand(this::class).left()
    }

    private fun oppdaterStønadsperiodeInternal(
        saksbehandler: NavIdentBruker.Saksbehandler,
        oppdatertStønadsperiode: Stønadsperiode,
        formuegrenserFactory: FormuegrenserFactory,
        clock: Clock,
    ): Either<KunneIkkeOppdatereStønadsperiode, Vilkårsvurdert> {
        return grunnlagsdataOgVilkårsvurderinger.oppdaterStønadsperiode(
            stønadsperiode = oppdatertStønadsperiode,
            formuegrenserFactory = formuegrenserFactory,
            clock = clock,
        ).mapLeft {
            KunneIkkeOppdatereStønadsperiode.KunneIkkeOppdatereGrunnlagsdata(it)
        }.map {
            copyInternal(
                stønadsperiode = oppdatertStønadsperiode,
                grunnlagsdataOgVilkårsvurderinger = it,
            ).vilkårsvurder(saksbehandler)
        }
    }

    fun leggTilOpplysningspliktVilkår(
        saksbehandler: NavIdentBruker.Saksbehandler,
        opplysningspliktVilkår: OpplysningspliktVilkår.Vurdert,
    ): Either<KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilOpplysningsplikt, Vilkårsvurdert> {
        return if (this is KanOppdaterePeriodeGrunnlagVilkår) {
            leggTilOpplysningspliktVilkårInternal(saksbehandler, opplysningspliktVilkår)
        } else {
            KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilOpplysningsplikt.UgyldigTilstand(this::class).left()
        }
    }

    private fun leggTilOpplysningspliktVilkårInternal(
        saksbehandler: NavIdentBruker.Saksbehandler,
        vilkår: OpplysningspliktVilkår.Vurdert,
    ): Either<KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilOpplysningsplikt, Vilkårsvurdert> {
        return valider<KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilOpplysningsplikt>(vilkår)
            .map {
                copyInternal(
                    grunnlagsdataOgVilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger.leggTil(vilkår),
                ).vilkårsvurder(saksbehandler)
            }
    }

    fun leggTilPensjonsVilkår(
        vilkår: PensjonsVilkår.Vurdert,
        saksbehandler: NavIdentBruker.Saksbehandler,
    ): Either<KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilPensjonsVilkår, Vilkårsvurdert> {
        return if (this is KanOppdaterePeriodeGrunnlagVilkår) {
            leggTilPensjonsVilkårInternal(saksbehandler, vilkår)
        } else {
            KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilPensjonsVilkår.UgyldigTilstand(this::class).left()
        }
    }

    private fun leggTilPensjonsVilkårInternal(
        saksbehandler: NavIdentBruker.Saksbehandler,
        vilkår: PensjonsVilkår.Vurdert,
    ): Either<KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilPensjonsVilkår, Vilkårsvurdert> {
        return valider<KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilPensjonsVilkår>(vilkår)
            .map {
                copyInternal(
                    grunnlagsdataOgVilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger.leggTil(vilkår),
                ).vilkårsvurder(saksbehandler)
            }
    }

    fun leggTilPersonligOppmøteVilkår(
        saksbehandler: NavIdentBruker.Saksbehandler,
        vilkår: PersonligOppmøteVilkår.Vurdert,
    ): Either<KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilPersonligOppmøteVilkår, Vilkårsvurdert> {
        return if (this is KanOppdaterePeriodeGrunnlagVilkår) {
            leggTilPersonligOppmøteVilkårInternal(saksbehandler, vilkår)
        } else {
            KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilPersonligOppmøteVilkår.UgyldigTilstand(
                this::class,
                Vilkårsvurdert::class,
            ).left()
        }
    }

    private fun leggTilPersonligOppmøteVilkårInternal(
        saksbehandler: NavIdentBruker.Saksbehandler,
        vilkår: PersonligOppmøteVilkår.Vurdert,
    ): Either<KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilPersonligOppmøteVilkår, Vilkårsvurdert> {
        return valider<KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilPersonligOppmøteVilkår>(vilkår)
            .map {
                copyInternal(
                    grunnlagsdataOgVilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger.leggTil(vilkår),
                ).vilkårsvurder(saksbehandler)
            }
    }

    fun leggTilLovligOpphold(
        saksbehandler: NavIdentBruker.Saksbehandler,
        lovligOppholdVilkår: LovligOppholdVilkår.Vurdert,
    ) = if (this is KanOppdaterePeriodeGrunnlagVilkår) {
        leggTilLovligOppholdInternal(saksbehandler, lovligOppholdVilkår)
    } else {
        KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilLovligOpphold.UgyldigTilstand.Søknadsbehandling(
            this::class,
            Vilkårsvurdert::class,
        ).left()
    }

    private fun leggTilLovligOppholdInternal(
        saksbehandler: NavIdentBruker.Saksbehandler,
        lovligOppholdVilkår: LovligOppholdVilkår.Vurdert,
    ) = valider<KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilLovligOpphold>(lovligOppholdVilkår).map {
        copyInternal(
            grunnlagsdataOgVilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger.leggTil(lovligOppholdVilkår),
        ).vilkårsvurder(saksbehandler)
    }

    fun beregn(
        nySaksbehandler: NavIdentBruker.Saksbehandler,
        begrunnelse: String?,
        clock: Clock,
        satsFactory: SatsFactory,
    ): Either<KunneIkkeBeregne, Beregnet> {
        return when (this) {
            is KanOppdaterePeriodeGrunnlagVilkår -> {
                when (val vilkårsvurdert = vilkårsvurder(nySaksbehandler)) {
                    is KanBeregnes -> {
                        beregnInternal(
                            søknadsbehandling = vilkårsvurdert,
                            begrunnelse = begrunnelse,
                            clock = clock,
                            beregningStrategyFactory = BeregningStrategyFactory(
                                clock = clock,
                                satsFactory = satsFactory,
                            ),
                            nySaksbehandler = nySaksbehandler,
                        )
                    }

                    else -> {
                        KunneIkkeBeregne.UgyldigTilstand(this::class).left()
                    }
                }
            }

            else -> {
                KunneIkkeBeregne.UgyldigTilstand(this::class).left()
            }
        }
    }

    fun leggTilInstitusjonsoppholdVilkår(
        saksbehandler: NavIdentBruker.Saksbehandler,
        vilkår: InstitusjonsoppholdVilkår.Vurdert,
    ): Either<KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilInstitusjonsoppholdVilkår, Vilkårsvurdert> {
        require(vilkår.vurderingsperioder.size == 1) {
            // TODO jah: Flytt denne litt mer sentralt for hele søknadsbehandling eller bytt til en mer felles left.
            "Vi støtter ikke flere enn 1 vurderingsperiode for søknadsbehandling"
        }
        if (vilkår.perioder.first() != periode) {
            return KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilInstitusjonsoppholdVilkår.BehandlingsperiodeOgVurderingsperiodeMåVæreLik.left()
        }
        return if (this is KanOppdaterePeriodeGrunnlagVilkår) {
            copyInternal(
                grunnlagsdataOgVilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger.leggTil(vilkår),
            ).vilkårsvurder(saksbehandler).right()
        } else {
            KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilInstitusjonsoppholdVilkår.UgyldigTilstand(this::class).left()
        }
    }

    @Suppress("UNCHECKED_CAST")
    protected fun <T : KunneIkkeLeggeTilVilkår> valider(vilkår: Vilkår): Either<T, Unit> {
        return when (vilkår) {
            is FamiliegjenforeningVilkår.Vurdert -> Unit.right()
            is FastOppholdINorgeVilkår.Vurdert -> Unit.right()
            is FlyktningVilkår.Vurdert -> Unit.right()
            is FormueVilkår.Vurdert -> Unit.right()
            is InstitusjonsoppholdVilkår.Vurdert -> Unit.right()
            is LovligOppholdVilkår.Vurdert -> Unit.right()
            is OpplysningspliktVilkår.Vurdert -> valider(vilkår).mapLeft { it as T }
            is PensjonsVilkår.Vurdert -> valider(vilkår).mapLeft { it as T }
            is PersonligOppmøteVilkår.Vurdert -> {
                if (!periode.fullstendigOverlapp(vilkår.perioder)) {
                    (KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilPensjonsVilkår.HeleBehandlingsperiodenErIkkeVurdert as T).left()
                } else {
                    Unit.right()
                }
            }

            is UføreVilkår.Vurdert -> valider(vilkår).mapLeft { it as T }
            is UtenlandsoppholdVilkår.Vurdert -> valider(vilkår).mapLeft { it as T }
            else -> throw IllegalStateException("Vet ikke hvordan man validerer vilkår av type: ${vilkår::class}")
        }
    }

    private fun valider(utenlandsopphold: UtenlandsoppholdVilkår.Vurdert): Either<KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilUtenlandsopphold, Unit> {
        return when {
            utenlandsopphold.vurderingsperioder.size != 1 -> {
                KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilUtenlandsopphold.MåInneholdeKunEnVurderingsperiode.left()
            }

            !periode.inneholderAlle(utenlandsopphold.vurderingsperioder) -> {
                KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilUtenlandsopphold.VurderingsperiodeUtenforBehandlingsperiode.left()
            }

            !utenlandsopphold.vurderingsperioder.all {
                it.vurdering == utenlandsopphold.vurderingsperioder.first().vurdering
            } -> {
                KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilUtenlandsopphold.AlleVurderingsperioderMåHaSammeResultat.left()
            }

            !periode.fullstendigOverlapp(utenlandsopphold.vurderingsperioder.map { it.periode }) -> {
                KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilUtenlandsopphold.MåVurdereHelePerioden.left()
            }

            else -> Unit.right()
        }
    }

    fun leggTilUførevilkår(
        saksbehandler: NavIdentBruker.Saksbehandler,
        uførhet: UføreVilkår.Vurdert,
    ): Either<KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilUførevilkår, Vilkårsvurdert> {
        return if (this is KanOppdaterePeriodeGrunnlagVilkår) {
            leggTilUførevilkårInternal(saksbehandler, uførhet)
        } else {
            KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilUførevilkår.UgyldigTilstand(this::class, Vilkårsvurdert::class)
                .left()
        }
    }

    private fun leggTilUførevilkårInternal(
        saksbehandler: NavIdentBruker.Saksbehandler,
        vilkår: UføreVilkår.Vurdert,
    ): Either<KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilUførevilkår, Vilkårsvurdert> {
        return valider<KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilUførevilkår>(vilkår)
            .map {
                copyInternal(
                    grunnlagsdataOgVilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger.leggTil(vilkår),
                ).vilkårsvurder(saksbehandler)
            }
    }

    fun leggTilFamiliegjenforeningvilkår(
        saksbehandler: NavIdentBruker.Saksbehandler,
        familiegjenforening: FamiliegjenforeningVilkår.Vurdert,
    ): Either<KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilFamiliegjenforeningVilkår, Vilkårsvurdert> {
        return if (this is KanOppdaterePeriodeGrunnlagVilkår) {
            leggTilFamiliegjenforeningvilkårInternal(saksbehandler, familiegjenforening)
        } else {
            KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilFamiliegjenforeningVilkår.UgyldigTilstand(
                this::class,
                Vilkårsvurdert::class,
            ).left()
        }
    }

    private fun leggTilFamiliegjenforeningvilkårInternal(
        saksbehandler: NavIdentBruker.Saksbehandler,
        vilkår: FamiliegjenforeningVilkår.Vurdert,
    ): Either<KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilFamiliegjenforeningVilkår, Vilkårsvurdert> {
        return valider<KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilFamiliegjenforeningVilkår>(vilkår)
            .map {
                copyInternal(
                    grunnlagsdataOgVilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger.leggTil(vilkår),
                ).vilkårsvurder(saksbehandler)
            }
    }

    fun leggTilFlyktningVilkår(
        saksbehandler: NavIdentBruker.Saksbehandler,
        vilkår: FlyktningVilkår.Vurdert,
    ): Either<KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilFlyktningVilkår, Vilkårsvurdert> {
        return if (this is KanOppdaterePeriodeGrunnlagVilkår) {
            leggTilFlyktningVilkårInternal(saksbehandler, vilkår)
        } else {
            KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilFlyktningVilkår.UgyldigTilstand(
                fra = this::class,
                til = Vilkårsvurdert::class,
            ).left()
        }
    }

    private fun leggTilFlyktningVilkårInternal(
        saksbehandler: NavIdentBruker.Saksbehandler,
        vilkår: FlyktningVilkår.Vurdert,
    ): Either<KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilFlyktningVilkår, Vilkårsvurdert> {
        return copyInternal(
            grunnlagsdataOgVilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger.leggTil(vilkår),
        ).vilkårsvurder(saksbehandler).right()
    }

    fun leggTilFastOppholdINorgeVilkår(
        saksbehandler: NavIdentBruker.Saksbehandler,
        vilkår: FastOppholdINorgeVilkår.Vurdert,
    ): Either<KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilFastOppholdINorgeVilkår, Vilkårsvurdert> {
        return if (this is KanOppdaterePeriodeGrunnlagVilkår) {
            leggTilFastOppholdINorgeVilkårInternal(saksbehandler, vilkår)
        } else {
            KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilFastOppholdINorgeVilkår.UgyldigTilstand(
                fra = this::class,
                til = Vilkårsvurdert::class,
            ).left()
        }
    }

    private fun leggTilFastOppholdINorgeVilkårInternal(
        saksbehandler: NavIdentBruker.Saksbehandler,
        vilkår: FastOppholdINorgeVilkår.Vurdert,
    ): Either<KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilFastOppholdINorgeVilkår, Vilkårsvurdert> {
        return copyInternal(
            grunnlagsdataOgVilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger.leggTil(vilkår),
        ).vilkårsvurder(saksbehandler).right()
    }

    private fun valider(uførhet: UføreVilkår.Vurdert): Either<KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilUførevilkår, Unit> {
        return when {
            !periode.inneholderAlle(uførhet.vurderingsperioder) -> {
                KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilUførevilkår.VurderingsperiodeUtenforBehandlingsperiode.left()
            }

            else -> Unit.right()
        }
    }

    private fun valider(opplysningspliktVilkår: OpplysningspliktVilkår.Vurdert): Either<KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilOpplysningsplikt, Unit> {
        return when {
            !periode.fullstendigOverlapp(opplysningspliktVilkår.minsteAntallSammenhengendePerioder()) -> {
                KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilOpplysningsplikt.HeleBehandlingsperiodenErIkkeVurdert.left()
            }

            else -> Unit.right()
        }
    }

    private fun valider(vilkår: PensjonsVilkår.Vurdert): Either<KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilPensjonsVilkår, Unit> {
        return when {
            Sakstype.ALDER != sakstype -> {
                KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilPensjonsVilkår.VilkårKunRelevantForAlder.left()
            }

            !periode.fullstendigOverlapp(vilkår.minsteAntallSammenhengendePerioder()) -> {
                KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilPensjonsVilkår.HeleBehandlingsperiodenErIkkeVurdert.left()
            }

            else -> Unit.right()
        }
    }

    open fun simuler(
        saksbehandler: NavIdentBruker.Saksbehandler,
        simuler: (beregning: Beregning, uføregrunnlag: NonEmptyList<Grunnlag.Uføregrunnlag>?) -> Either<SimulerUtbetalingFeilet, Simulering>,
    ): Either<KunneIkkeSimulereBehandling, Simulert> {
        return KunneIkkeSimulereBehandling.UgyldigTilstand(this::class).left()
    }

    private fun beregnInternal(
        nySaksbehandler: NavIdentBruker.Saksbehandler,
        søknadsbehandling: KanBeregnes,
        begrunnelse: String?,
        clock: Clock,
        beregningStrategyFactory: BeregningStrategyFactory,
    ): Either<KunneIkkeBeregne, Beregnet> {
        return when (val avkort = søknadsbehandling.avkorting) {
            is AvkortingVedSøknadsbehandling.Uhåndtert.IngenUtestående -> {
                beregnUtenAvkorting(
                    saksbehandler = nySaksbehandler,
                    begrunnelse = begrunnelse,
                    beregningStrategyFactory = beregningStrategyFactory,
                ).getOrHandle { return it.left() }
            }

            is AvkortingVedSøknadsbehandling.Uhåndtert.KanIkkeHåndtere -> {
                throw IllegalStateException("${avkort::class} skal aldri kunne oppstå ved beregning. Modellen er dog nødt å støtte dette tilfellet pga at alle tilstander av avslutt/lukking må støttes.")
            }

            is AvkortingVedSøknadsbehandling.Uhåndtert.UteståendeAvkorting -> {
                beregnMedAvkorting(
                    saksbehandler = nySaksbehandler,
                    avkorting = avkort,
                    begrunnelse = begrunnelse,
                    clock = clock,
                    beregningStrategyFactory = beregningStrategyFactory,
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
                    fnr = behandling.fnr,
                    beregning = beregning,
                    fritekstTilBrev = behandling.fritekstTilBrev,
                    stønadsperiode = behandling.stønadsperiode!!,
                    grunnlagsdata = behandling.grunnlagsdata,
                    vilkårsvurderinger = behandling.vilkårsvurderinger,
                    attesteringer = behandling.attesteringer,
                    avkorting = behandling.avkorting.håndter().kanIkke(),
                    sakstype = behandling.sakstype,
                    saksbehandler = nySaksbehandler,
                )

                AvslagGrunnetBeregning.Nei -> {
                    Beregnet.Innvilget(
                        id = behandling.id,
                        opprettet = behandling.opprettet,
                        sakId = behandling.sakId,
                        saksnummer = behandling.saksnummer,
                        søknad = behandling.søknad,
                        oppgaveId = behandling.oppgaveId,
                        fnr = behandling.fnr,
                        beregning = beregning,
                        fritekstTilBrev = behandling.fritekstTilBrev,
                        stønadsperiode = behandling.stønadsperiode!!,
                        grunnlagsdata = behandling.grunnlagsdata,
                        vilkårsvurderinger = behandling.vilkårsvurderinger,
                        attesteringer = behandling.attesteringer,
                        avkorting = behandling.avkorting.håndter(),
                        sakstype = behandling.sakstype,
                        saksbehandler = nySaksbehandler,
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
        saksbehandler: NavIdentBruker.Saksbehandler,
        begrunnelse: String?,
        beregningStrategyFactory: BeregningStrategyFactory,
    ): Either<KunneIkkeBeregne, Pair<Vilkårsvurdert, Beregning>> {
        return leggTilFradragsgrunnlag(
            saksbehandler,
            fradragsgrunnlag = grunnlagsdata.fradragsgrunnlag.filterNot { it.fradragstype == Fradragstype.AvkortingUtenlandsopphold },
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
        saksbehandler: NavIdentBruker.Saksbehandler,
        avkorting: AvkortingVedSøknadsbehandling.Uhåndtert.UteståendeAvkorting,
        begrunnelse: String?,
        clock: Clock,
        beregningStrategyFactory: BeregningStrategyFactory,
    ): Either<KunneIkkeBeregne, Pair<Vilkårsvurdert, Beregning>> {
        return beregnUtenAvkorting(saksbehandler, begrunnelse, beregningStrategyFactory)
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
                    saksbehandler = saksbehandler,
                    fradragsgrunnlag = utenAvkorting.grunnlagsdata.fradragsgrunnlag + fradragForAvkorting,
                ).getOrHandle { return KunneIkkeBeregne.UgyldigTilstandForEndringAvFradrag(it).left() }

                medAvkorting to gjørBeregning(
                    søknadsbehandling = medAvkorting,
                    begrunnelse = begrunnelse,
                    beregningStrategyFactory = beregningStrategyFactory,
                )
            }
    }

    sealed class Vilkårsvurdert :
        Søknadsbehandling(),
        KanOppdaterePeriodeGrunnlagVilkår {

        abstract override val avkorting: AvkortingVedSøknadsbehandling.Uhåndtert

        companion object {
            fun opprett(
                id: UUID,
                opprettet: Tidspunkt,
                sakId: UUID,
                saksnummer: Saksnummer,
                søknad: Søknad.Journalført.MedOppgave,
                oppgaveId: OppgaveId,
                fnr: Fnr,
                fritekstTilBrev: String,
                stønadsperiode: Stønadsperiode,
                grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderinger.Søknadsbehandling,
                attesteringer: Attesteringshistorikk,
                avkorting: AvkortingVedSøknadsbehandling.Uhåndtert,
                sakstype: Sakstype,
                saksbehandler: NavIdentBruker.Saksbehandler,
            ): Vilkårsvurdert {
                val grunnlagsdata = grunnlagsdataOgVilkårsvurderinger.grunnlagsdata
                val vilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger.vilkårsvurderinger
                val oppdaterteVilkårsvurderinger = vilkårsvurderinger.let {
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
                return when (oppdaterteVilkårsvurderinger.vurdering) {
                    is Vilkårsvurderingsresultat.Avslag -> {
                        Avslag(
                            id,
                            opprettet,
                            sakId,
                            saksnummer,
                            søknad,
                            oppgaveId,
                            fnr,
                            fritekstTilBrev,
                            stønadsperiode,
                            grunnlagsdata,
                            oppdaterteVilkårsvurderinger,
                            attesteringer,
                            avkorting.kanIkke(),
                            sakstype,
                            saksbehandler,
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
                            fnr,
                            fritekstTilBrev,
                            stønadsperiode,
                            grunnlagsdata,
                            oppdaterteVilkårsvurderinger,
                            attesteringer,
                            avkorting.uhåndtert(),
                            sakstype,
                            saksbehandler,
                        )
                    }

                    is Vilkårsvurderingsresultat.Uavklart -> {
                        Uavklart(
                            id = id,
                            opprettet = opprettet,
                            sakId = sakId,
                            saksnummer = saksnummer,
                            søknad = søknad,
                            oppgaveId = oppgaveId,
                            fnr = fnr,
                            fritekstTilBrev = fritekstTilBrev,
                            stønadsperiode = stønadsperiode,
                            grunnlagsdata = grunnlagsdata,
                            vilkårsvurderinger = oppdaterteVilkårsvurderinger,
                            attesteringer = attesteringer,
                            avkorting = avkorting.kanIkke(),
                            sakstype = sakstype,
                            saksbehandler,
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
            override val fnr: Fnr,
            override val fritekstTilBrev: String,
            override val stønadsperiode: Stønadsperiode,
            override val grunnlagsdata: Grunnlagsdata,
            override val vilkårsvurderinger: Vilkårsvurderinger.Søknadsbehandling,
            override val attesteringer: Attesteringshistorikk,
            override val avkorting: AvkortingVedSøknadsbehandling.Uhåndtert,
            override val sakstype: Sakstype,
            override val saksbehandler: NavIdentBruker.Saksbehandler?,
        ) : Vilkårsvurdert(), KanBeregnes {
            override val periode: Periode = stønadsperiode.periode

            override val beregning = null
            override val simulering: Simulering? = null

            init {
                kastHvisGrunnlagsdataOgVilkårsvurderingerPeriodenOgBehandlingensPerioderErUlike()
            }

            override fun accept(visitor: SøknadsbehandlingVisitor) {
                visitor.visit(this)
            }

            override fun copyInternal(
                stønadsperiode: Stønadsperiode,
                grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderinger.Søknadsbehandling,
            ): Vilkårsvurdert {
                return copy(
                    stønadsperiode = stønadsperiode,
                    grunnlagsdata = grunnlagsdataOgVilkårsvurderinger.grunnlagsdata,
                    vilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger.vilkårsvurderinger,
                )
            }
        }

        data class Avslag(
            override val id: UUID,
            override val opprettet: Tidspunkt,
            override val sakId: UUID,
            override val saksnummer: Saksnummer,
            override val søknad: Søknad.Journalført.MedOppgave,
            override val oppgaveId: OppgaveId,
            override val fnr: Fnr,
            override val fritekstTilBrev: String,
            override val stønadsperiode: Stønadsperiode,
            override val grunnlagsdata: Grunnlagsdata,
            override val vilkårsvurderinger: Vilkårsvurderinger.Søknadsbehandling,
            override val attesteringer: Attesteringshistorikk,
            override val avkorting: AvkortingVedSøknadsbehandling.Uhåndtert.KanIkkeHåndtere,
            override val sakstype: Sakstype,
            override val saksbehandler: NavIdentBruker.Saksbehandler?,
        ) : Vilkårsvurdert(), ErAvslag {

            override val beregning = null
            override val simulering: Simulering? = null

            override fun copyInternal(
                stønadsperiode: Stønadsperiode,
                grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderinger.Søknadsbehandling,
            ): Avslag {
                return copy(
                    stønadsperiode = stønadsperiode,
                    grunnlagsdata = grunnlagsdataOgVilkårsvurderinger.grunnlagsdata,
                    vilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger.vilkårsvurderinger,
                )
            }

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
                    id = id,
                    opprettet = opprettet,
                    sakId = sakId,
                    saksnummer = saksnummer,
                    søknad = søknad,
                    oppgaveId = oppgaveId,
                    fnr = fnr,
                    saksbehandler = saksbehandler,
                    fritekstTilBrev = fritekstTilBrev,
                    stønadsperiode = stønadsperiode,
                    grunnlagsdata = grunnlagsdata,
                    vilkårsvurderinger = vilkårsvurderinger,
                    attesteringer = attesteringer,
                    avkorting = avkorting.håndter().kanIkke(),
                    sakstype = sakstype,
                )

            // TODO fiks typing/gyldig tilstand/vilkår fradrag?
            override val avslagsgrunner: List<Avslagsgrunn> = when (val vilkår = vilkårsvurderinger.vurdering) {
                is Vilkårsvurderingsresultat.Avslag -> vilkår.avslagsgrunner
                is Vilkårsvurderingsresultat.Innvilget -> emptyList()
                is Vilkårsvurderingsresultat.Uavklart -> emptyList()
            }
        }

        data class Uavklart(
            override val id: UUID,
            override val opprettet: Tidspunkt,
            override val sakId: UUID,
            override val saksnummer: Saksnummer,
            override val søknad: Søknad.Journalført.MedOppgave,
            override val oppgaveId: OppgaveId,
            override val fnr: Fnr,
            override val fritekstTilBrev: String,
            override val stønadsperiode: Stønadsperiode?,
            override val grunnlagsdata: Grunnlagsdata,
            override val vilkårsvurderinger: Vilkårsvurderinger.Søknadsbehandling,
            override val attesteringer: Attesteringshistorikk,
            override val avkorting: AvkortingVedSøknadsbehandling.Uhåndtert.KanIkkeHåndtere,
            override val sakstype: Sakstype,
            override val saksbehandler: NavIdentBruker.Saksbehandler?,
        ) : Vilkårsvurdert() {

            override val beregning = null
            override val simulering: Simulering? = null

            override fun copyInternal(
                stønadsperiode: Stønadsperiode,
                grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderinger.Søknadsbehandling,
            ): Uavklart {
                return copy(
                    stønadsperiode = stønadsperiode,
                    grunnlagsdata = grunnlagsdataOgVilkårsvurderinger.grunnlagsdata,
                    vilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger.vilkårsvurderinger,
                )
            }

            override val periode: Periode
                get() = stønadsperiode?.periode ?: throw StønadsperiodeIkkeDefinertException(id)

            init {
                kastHvisGrunnlagsdataOgVilkårsvurderingerPeriodenOgBehandlingensPerioderErUlike()
            }

            override fun accept(visitor: SøknadsbehandlingVisitor) {
                visitor.visit(this)
            }

            data class StønadsperiodeIkkeDefinertException(
                val id: UUID,
            ) : RuntimeException("Sønadsperiode er ikke definert for søknadsbehandling:$id")
        }
    }

    sealed class Beregnet : Søknadsbehandling(), KanOppdaterePeriodeGrunnlagVilkår {
        abstract override val beregning: Beregning
        abstract override val stønadsperiode: Stønadsperiode
        abstract override val avkorting: AvkortingVedSøknadsbehandling.Håndtert

        override fun simuler(
            saksbehandler: NavIdentBruker.Saksbehandler,
            simuler: (beregning: Beregning, uføregrunnlag: NonEmptyList<Grunnlag.Uføregrunnlag>?) -> Either<SimulerUtbetalingFeilet, Simulering>,
        ): Either<KunneIkkeSimulereBehandling, Simulert> {
            return simuler(
                beregning,
                when (sakstype) {
                    Sakstype.ALDER -> {
                        null
                    }

                    Sakstype.UFØRE -> {
                        vilkårsvurderinger.uføreVilkår()
                            .getOrHandle { throw IllegalStateException("Søknadsbehandling uføre: $id mangler uføregrunnlag") }
                            .grunnlag
                            .toNonEmptyList()
                    }
                },
            ).mapLeft {
                KunneIkkeSimulereBehandling.KunneIkkeSimulere(it)
            }.map { simulering ->
                Simulert(
                    id = id,
                    opprettet = opprettet,
                    sakId = sakId,
                    saksnummer = saksnummer,
                    søknad = søknad,
                    oppgaveId = oppgaveId,
                    fnr = fnr,
                    beregning = beregning,
                    simulering = simulering,
                    fritekstTilBrev = fritekstTilBrev,
                    stønadsperiode = stønadsperiode,
                    grunnlagsdata = grunnlagsdata,
                    vilkårsvurderinger = vilkårsvurderinger,
                    attesteringer = attesteringer,
                    avkorting = avkorting,
                    sakstype = sakstype,
                    saksbehandler = saksbehandler,
                )
            }
        }

        data class Innvilget(
            override val id: UUID,
            override val opprettet: Tidspunkt,
            override val sakId: UUID,
            override val saksnummer: Saksnummer,
            override val søknad: Søknad.Journalført.MedOppgave,
            override val oppgaveId: OppgaveId,
            override val fnr: Fnr,
            override val beregning: Beregning,
            override val fritekstTilBrev: String,
            override val stønadsperiode: Stønadsperiode,
            override val grunnlagsdata: Grunnlagsdata,
            override val vilkårsvurderinger: Vilkårsvurderinger.Søknadsbehandling,
            override val attesteringer: Attesteringshistorikk,
            override val avkorting: AvkortingVedSøknadsbehandling.Håndtert,
            override val sakstype: Sakstype,
            override val saksbehandler: NavIdentBruker.Saksbehandler?,
        ) : Beregnet() {
            override val periode: Periode = stønadsperiode.periode
            override val simulering: Simulering? = null

            init {
                kastHvisGrunnlagsdataOgVilkårsvurderingerPeriodenOgBehandlingensPerioderErUlike()
            }

            override fun accept(visitor: SøknadsbehandlingVisitor) {
                visitor.visit(this)
            }

            override fun copyInternal(
                stønadsperiode: Stønadsperiode,
                grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderinger.Søknadsbehandling,
            ): Innvilget {
                return copy(
                    stønadsperiode = stønadsperiode,
                    grunnlagsdata = grunnlagsdataOgVilkårsvurderinger.grunnlagsdata,
                    vilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger.vilkårsvurderinger,
                )
            }
        }

        data class Avslag(
            override val id: UUID,
            override val opprettet: Tidspunkt,
            override val sakId: UUID,
            override val saksnummer: Saksnummer,
            override val søknad: Søknad.Journalført.MedOppgave,
            override val oppgaveId: OppgaveId,
            override val fnr: Fnr,
            override val beregning: Beregning,
            override val fritekstTilBrev: String,
            override val stønadsperiode: Stønadsperiode,
            override val grunnlagsdata: Grunnlagsdata,
            override val vilkårsvurderinger: Vilkårsvurderinger.Søknadsbehandling,
            override val attesteringer: Attesteringshistorikk,
            override val avkorting: AvkortingVedSøknadsbehandling.Håndtert.KanIkkeHåndtere,
            override val sakstype: Sakstype,
            override val saksbehandler: NavIdentBruker.Saksbehandler?,
        ) : Beregnet(), ErAvslag {
            override val periode: Periode = stønadsperiode.periode
            override val simulering: Simulering? = null

            private val avslagsgrunnForBeregning: List<Avslagsgrunn> =
                when (val vurdering = VurderAvslagGrunnetBeregning.vurderAvslagGrunnetBeregning(beregning)) {
                    is AvslagGrunnetBeregning.Ja -> listOf(vurdering.grunn.toAvslagsgrunn())
                    is AvslagGrunnetBeregning.Nei -> emptyList()
                }

            init {
                kastHvisGrunnlagsdataOgVilkårsvurderingerPeriodenOgBehandlingensPerioderErUlike()
            }

            override fun copyInternal(
                stønadsperiode: Stønadsperiode,
                grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderinger.Søknadsbehandling,
            ): Avslag {
                return copy(
                    stønadsperiode = stønadsperiode,
                    grunnlagsdata = grunnlagsdataOgVilkårsvurderinger.grunnlagsdata,
                    vilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger.vilkårsvurderinger,
                )
            }

            override fun accept(visitor: SøknadsbehandlingVisitor) {
                visitor.visit(this)
            }

            fun tilAttestering(
                saksbehandler: NavIdentBruker.Saksbehandler,
                fritekstTilBrev: String,
            ): TilAttestering.Avslag.MedBeregning =
                TilAttestering.Avslag.MedBeregning(
                    id = id,
                    opprettet = opprettet,
                    sakId = sakId,
                    saksnummer = saksnummer,
                    søknad = søknad,
                    oppgaveId = oppgaveId,
                    fnr = fnr,
                    beregning = beregning,
                    saksbehandler = saksbehandler,
                    fritekstTilBrev = fritekstTilBrev,
                    stønadsperiode = stønadsperiode,
                    grunnlagsdata = grunnlagsdata,
                    vilkårsvurderinger = vilkårsvurderinger,
                    attesteringer = attesteringer,
                    avkorting = avkorting.kanIkke(),
                    sakstype = sakstype,
                )

            // TODO fiks typing/gyldig tilstand/vilkår fradrag?
            override val avslagsgrunner: List<Avslagsgrunn> = when (val vilkår = vilkårsvurderinger.vurdering) {
                is Vilkårsvurderingsresultat.Avslag -> vilkår.avslagsgrunner
                is Vilkårsvurderingsresultat.Innvilget -> emptyList()
                is Vilkårsvurderingsresultat.Uavklart -> emptyList()
            } + avslagsgrunnForBeregning
        }
    }

    data class Simulert(
        override val id: UUID,
        override val opprettet: Tidspunkt,
        override val sakId: UUID,
        override val saksnummer: Saksnummer,
        override val søknad: Søknad.Journalført.MedOppgave,
        override val oppgaveId: OppgaveId,
        override val fnr: Fnr,
        override val beregning: Beregning,
        override val simulering: Simulering,
        override val fritekstTilBrev: String,
        override val stønadsperiode: Stønadsperiode,
        override val grunnlagsdata: Grunnlagsdata,
        override val vilkårsvurderinger: Vilkårsvurderinger.Søknadsbehandling,
        override val attesteringer: Attesteringshistorikk,
        override val avkorting: AvkortingVedSøknadsbehandling.Håndtert,
        override val sakstype: Sakstype,
        override val saksbehandler: NavIdentBruker.Saksbehandler?,
    ) : Søknadsbehandling(), KanOppdaterePeriodeGrunnlagVilkår {
        override val periode: Periode = stønadsperiode.periode

        init {
            kastHvisGrunnlagsdataOgVilkårsvurderingerPeriodenOgBehandlingensPerioderErUlike()
        }

        override fun accept(visitor: SøknadsbehandlingVisitor) {
            visitor.visit(this)
        }

        override fun copyInternal(
            stønadsperiode: Stønadsperiode,
            grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderinger.Søknadsbehandling,
        ): Simulert {
            return copy(
                stønadsperiode = stønadsperiode,
                grunnlagsdata = grunnlagsdataOgVilkårsvurderinger.grunnlagsdata,
                vilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger.vilkårsvurderinger,
            )
        }

        override fun simuler(
            saksbehandler: NavIdentBruker.Saksbehandler,
            simuler: (beregning: Beregning, uføregrunnlag: NonEmptyList<Grunnlag.Uføregrunnlag>?) -> Either<SimulerUtbetalingFeilet, Simulering>,
        ): Either<KunneIkkeSimulereBehandling, Simulert> {
            return simuler(
                beregning,
                when (sakstype) {
                    Sakstype.ALDER -> {
                        null
                    }

                    Sakstype.UFØRE -> {
                        vilkårsvurderinger.uføreVilkår()
                            .getOrHandle { throw IllegalStateException("Søknadsbehandling uføre: $id mangler uføregrunnlag") }
                            .grunnlag
                            .toNonEmptyList()
                    }
                },
            ).mapLeft {
                KunneIkkeSimulereBehandling.KunneIkkeSimulere(it)
            }.map { simulering ->
                Simulert(
                    id = id,
                    opprettet = opprettet,
                    sakId = sakId,
                    saksnummer = saksnummer,
                    søknad = søknad,
                    oppgaveId = oppgaveId,
                    fnr = fnr,
                    beregning = beregning,
                    simulering = simulering,
                    fritekstTilBrev = fritekstTilBrev,
                    stønadsperiode = stønadsperiode,
                    grunnlagsdata = grunnlagsdata,
                    vilkårsvurderinger = vilkårsvurderinger,
                    attesteringer = attesteringer,
                    avkorting = avkorting,
                    sakstype = sakstype,
                    saksbehandler = saksbehandler,
                )
            }
        }

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
                id = id,
                opprettet = opprettet,
                sakId = sakId,
                saksnummer = saksnummer,
                søknad = søknad,
                oppgaveId = oppgaveId,
                fnr = fnr,
                beregning = beregning,
                simulering = simulering,
                saksbehandler = saksbehandler,
                fritekstTilBrev = fritekstTilBrev,
                stønadsperiode = stønadsperiode,
                grunnlagsdata = grunnlagsdata,
                vilkårsvurderinger = vilkårsvurderinger,
                attesteringer = attesteringer,
                avkorting = avkorting,
                sakstype = sakstype,
            )
        }
    }

    sealed class TilAttestering : Søknadsbehandling() {
        abstract override val saksbehandler: NavIdentBruker.Saksbehandler
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
            override val fnr: Fnr,
            override val beregning: Beregning,
            override val simulering: Simulering,
            override val saksbehandler: NavIdentBruker.Saksbehandler,
            override val fritekstTilBrev: String,
            override val stønadsperiode: Stønadsperiode,
            override val grunnlagsdata: Grunnlagsdata,
            override val vilkårsvurderinger: Vilkårsvurderinger.Søknadsbehandling,
            override val attesteringer: Attesteringshistorikk,
            override val avkorting: AvkortingVedSøknadsbehandling.Håndtert,
            override val sakstype: Sakstype,
        ) : TilAttestering() {

            override fun copyInternal(
                stønadsperiode: Stønadsperiode,
                grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderinger.Søknadsbehandling,
            ): TilAttestering {
                return copy(
                    stønadsperiode = stønadsperiode,
                    grunnlagsdata = grunnlagsdataOgVilkårsvurderinger.grunnlagsdata,
                    vilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger.vilkårsvurderinger,
                )
            }

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
                    id = id,
                    opprettet = opprettet,
                    sakId = sakId,
                    saksnummer = saksnummer,
                    søknad = søknad,
                    oppgaveId = oppgaveId,
                    fnr = fnr,
                    beregning = beregning,
                    simulering = simulering,
                    saksbehandler = saksbehandler,
                    attesteringer = attesteringer.leggTilNyAttestering(attestering),
                    fritekstTilBrev = fritekstTilBrev,
                    stønadsperiode = stønadsperiode,
                    grunnlagsdata = grunnlagsdata,
                    vilkårsvurderinger = vilkårsvurderinger,
                    avkorting = avkorting,
                    sakstype = sakstype,
                )
            }

            fun tilIverksatt(attestering: Attestering): Iverksatt.Innvilget {
                return Iverksatt.Innvilget(
                    id = id,
                    opprettet = opprettet,
                    sakId = sakId,
                    saksnummer = saksnummer,
                    søknad = søknad,
                    oppgaveId = oppgaveId,
                    fnr = fnr,
                    beregning = beregning,
                    simulering = simulering,
                    saksbehandler = saksbehandler,
                    attesteringer = attesteringer.leggTilNyAttestering(attestering),
                    fritekstTilBrev = fritekstTilBrev,
                    stønadsperiode = stønadsperiode,
                    grunnlagsdata = grunnlagsdata,
                    vilkårsvurderinger = vilkårsvurderinger,
                    avkorting = avkorting.iverksett(id),
                    sakstype = sakstype,
                )
            }
        }

        sealed class Avslag : TilAttestering(), ErAvslag {
            abstract override val stønadsperiode: Stønadsperiode

            data class UtenBeregning(
                override val id: UUID,
                override val opprettet: Tidspunkt,
                override val sakId: UUID,
                override val saksnummer: Saksnummer,
                override val søknad: Søknad.Journalført.MedOppgave,
                override val oppgaveId: OppgaveId,
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
                override val avslagsgrunner: List<Avslagsgrunn> = when (val vilkår = vilkårsvurderinger.vurdering) {
                    is Vilkårsvurderingsresultat.Avslag -> vilkår.avslagsgrunner
                    is Vilkårsvurderingsresultat.Innvilget -> emptyList()
                    is Vilkårsvurderingsresultat.Uavklart -> emptyList()
                }

                override val periode: Periode = stønadsperiode.periode

                override val beregning = null
                override val simulering: Simulering? = null

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
                        id = id,
                        opprettet = opprettet,
                        sakId = sakId,
                        saksnummer = saksnummer,
                        søknad = søknad,
                        oppgaveId = oppgaveId,
                        fnr = fnr,
                        saksbehandler = saksbehandler,
                        attesteringer = attesteringer.leggTilNyAttestering(attestering),
                        fritekstTilBrev = fritekstTilBrev,
                        stønadsperiode = stønadsperiode,
                        grunnlagsdata = grunnlagsdata,
                        vilkårsvurderinger = vilkårsvurderinger,
                        avkorting = avkorting,
                        sakstype = sakstype,
                    )
                }

                override fun copyInternal(
                    stønadsperiode: Stønadsperiode,
                    grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderinger.Søknadsbehandling,
                ): UtenBeregning {
                    return copy(
                        stønadsperiode = stønadsperiode,
                        grunnlagsdata = grunnlagsdataOgVilkårsvurderinger.grunnlagsdata,
                        vilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger.vilkårsvurderinger,
                    )
                }

                fun tilIverksatt(
                    attestering: Attestering,
                ): Iverksatt.Avslag.UtenBeregning {
                    return Iverksatt.Avslag.UtenBeregning(
                        id = id,
                        opprettet = opprettet,
                        sakId = sakId,
                        saksnummer = saksnummer,
                        søknad = søknad,
                        oppgaveId = oppgaveId,
                        fnr = fnr,
                        saksbehandler = saksbehandler,
                        attesteringer = attesteringer.leggTilNyAttestering(attestering),
                        fritekstTilBrev = fritekstTilBrev,
                        stønadsperiode = stønadsperiode,
                        grunnlagsdata = grunnlagsdata,
                        vilkårsvurderinger = vilkårsvurderinger,
                        avkorting = avkorting.iverksett(id),
                        sakstype = sakstype,
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
                override val fnr: Fnr,
                override val beregning: Beregning,
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
                override val avslagsgrunner: List<Avslagsgrunn> = when (val vilkår = vilkårsvurderinger.vurdering) {
                    is Vilkårsvurderingsresultat.Avslag -> vilkår.avslagsgrunner
                    is Vilkårsvurderingsresultat.Innvilget -> emptyList()
                    is Vilkårsvurderingsresultat.Uavklart -> emptyList()
                } + avslagsgrunnForBeregning

                override val periode: Periode = stønadsperiode.periode
                override val simulering: Simulering? = null

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
                        id = id,
                        opprettet = opprettet,
                        sakId = sakId,
                        saksnummer = saksnummer,
                        søknad = søknad,
                        oppgaveId = oppgaveId,
                        fnr = fnr,
                        beregning = beregning,
                        saksbehandler = saksbehandler,
                        attesteringer = attesteringer.leggTilNyAttestering(attestering),
                        fritekstTilBrev = fritekstTilBrev,
                        stønadsperiode = stønadsperiode,
                        grunnlagsdata = grunnlagsdata,
                        vilkårsvurderinger = vilkårsvurderinger,
                        avkorting = avkorting,
                        sakstype = sakstype,
                    )
                }

                override fun copyInternal(
                    stønadsperiode: Stønadsperiode,
                    grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderinger.Søknadsbehandling,
                ): MedBeregning {
                    return copy(
                        stønadsperiode = stønadsperiode,
                        grunnlagsdata = grunnlagsdataOgVilkårsvurderinger.grunnlagsdata,
                        vilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger.vilkårsvurderinger,
                    )
                }

                fun tilIverksatt(
                    attestering: Attestering,
                ): Iverksatt.Avslag.MedBeregning {
                    return Iverksatt.Avslag.MedBeregning(
                        id = id,
                        opprettet = opprettet,
                        sakId = sakId,
                        saksnummer = saksnummer,
                        søknad = søknad,
                        oppgaveId = oppgaveId,
                        fnr = fnr,
                        beregning = beregning,
                        saksbehandler = saksbehandler,
                        attesteringer = attesteringer.leggTilNyAttestering(attestering),
                        fritekstTilBrev = fritekstTilBrev,
                        stønadsperiode = stønadsperiode,
                        grunnlagsdata = grunnlagsdata,
                        vilkårsvurderinger = vilkårsvurderinger,
                        avkorting = avkorting.iverksett(id),
                        sakstype = sakstype,
                    )
                }
            }
        }
    }

    sealed class Underkjent :
        Søknadsbehandling(),
        KanOppdaterePeriodeGrunnlagVilkår {
        abstract override val id: UUID
        abstract override val opprettet: Tidspunkt
        abstract override val sakId: UUID
        abstract override val saksnummer: Saksnummer
        abstract override val søknad: Søknad.Journalført.MedOppgave
        abstract override val oppgaveId: OppgaveId
        abstract override val fnr: Fnr
        abstract override val saksbehandler: NavIdentBruker.Saksbehandler
        abstract override val attesteringer: Attesteringshistorikk
        abstract override val stønadsperiode: Stønadsperiode
        abstract override val avkorting: AvkortingVedSøknadsbehandling.Håndtert

        abstract fun nyOppgaveId(nyOppgaveId: OppgaveId): Underkjent

        data class Innvilget(
            override val id: UUID,
            override val opprettet: Tidspunkt,
            override val sakId: UUID,
            override val saksnummer: Saksnummer,
            override val søknad: Søknad.Journalført.MedOppgave,
            override val oppgaveId: OppgaveId,
            override val fnr: Fnr,
            override val beregning: Beregning,
            override val simulering: Simulering,
            override val saksbehandler: NavIdentBruker.Saksbehandler,
            override val attesteringer: Attesteringshistorikk,
            override val fritekstTilBrev: String,
            override val stønadsperiode: Stønadsperiode,
            override val grunnlagsdata: Grunnlagsdata,
            override val vilkårsvurderinger: Vilkårsvurderinger.Søknadsbehandling,
            override val avkorting: AvkortingVedSøknadsbehandling.Håndtert,
            override val sakstype: Sakstype,
        ) : Underkjent() {

            override val periode: Periode = stønadsperiode.periode

            init {
                kastHvisGrunnlagsdataOgVilkårsvurderingerPeriodenOgBehandlingensPerioderErUlike()
            }

            override fun nyOppgaveId(nyOppgaveId: OppgaveId): Innvilget {
                return this.copy(oppgaveId = nyOppgaveId)
            }

            override fun copyInternal(
                stønadsperiode: Stønadsperiode,
                grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderinger.Søknadsbehandling,
            ): Innvilget {
                return copy(
                    stønadsperiode = stønadsperiode,
                    grunnlagsdata = grunnlagsdataOgVilkårsvurderinger.grunnlagsdata,
                    vilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger.vilkårsvurderinger,
                )
            }

            override fun accept(visitor: SøknadsbehandlingVisitor) {
                visitor.visit(this)
            }

            override fun simuler(
                saksbehandler: NavIdentBruker.Saksbehandler,
                simuler: (beregning: Beregning, uføregrunnlag: NonEmptyList<Grunnlag.Uføregrunnlag>?) -> Either<SimulerUtbetalingFeilet, Simulering>,
            ): Either<KunneIkkeSimulereBehandling, Simulert> {
                return simuler(
                    beregning,
                    when (sakstype) {
                        Sakstype.ALDER -> {
                            null
                        }

                        Sakstype.UFØRE -> {
                            vilkårsvurderinger.uføreVilkår()
                                .getOrHandle { throw IllegalStateException("Søknadsbehandling uføre: $id mangler uføregrunnlag") }
                                .grunnlag
                                .toNonEmptyList()
                        }
                    },
                ).mapLeft {
                    KunneIkkeSimulereBehandling.KunneIkkeSimulere(it)
                }.map { simulering ->
                    Simulert(
                        id = id,
                        opprettet = opprettet,
                        sakId = sakId,
                        saksnummer = saksnummer,
                        søknad = søknad,
                        oppgaveId = oppgaveId,
                        fnr = fnr,
                        beregning = beregning,
                        simulering = simulering,
                        fritekstTilBrev = fritekstTilBrev,
                        stønadsperiode = stønadsperiode,
                        grunnlagsdata = grunnlagsdata,
                        vilkårsvurderinger = vilkårsvurderinger,
                        attesteringer = attesteringer,
                        avkorting = avkorting,
                        sakstype = sakstype,
                        saksbehandler = saksbehandler,
                    )
                }
            }

            fun tilAttestering(saksbehandler: NavIdentBruker.Saksbehandler): TilAttestering.Innvilget {
                if (simulering.harFeilutbetalinger()) {
                    /**
                     * Kun en nødbrems for tilfeller som i utgangspunktet skal være håndtert og forhindret av andre mekanismer.
                     */
                    throw IllegalStateException("Simulering inneholder feilutbetalinger")
                }
                return TilAttestering.Innvilget(
                    id = id,
                    opprettet = opprettet,
                    sakId = sakId,
                    saksnummer = saksnummer,
                    søknad = søknad,
                    oppgaveId = oppgaveId,
                    fnr = fnr,
                    beregning = beregning,
                    simulering = simulering,
                    saksbehandler = saksbehandler,
                    fritekstTilBrev = fritekstTilBrev,
                    stønadsperiode = stønadsperiode,
                    grunnlagsdata = grunnlagsdata,
                    vilkårsvurderinger = vilkårsvurderinger,
                    attesteringer = attesteringer,
                    avkorting = avkorting,
                    sakstype = sakstype,
                )
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
                override val fnr: Fnr,
                override val beregning: Beregning,
                override val saksbehandler: NavIdentBruker.Saksbehandler,
                override val attesteringer: Attesteringshistorikk,
                override val fritekstTilBrev: String,
                override val stønadsperiode: Stønadsperiode,
                override val grunnlagsdata: Grunnlagsdata,
                override val vilkårsvurderinger: Vilkårsvurderinger.Søknadsbehandling,
                override val avkorting: AvkortingVedSøknadsbehandling.Håndtert.KanIkkeHåndtere,
                override val sakstype: Sakstype,
            ) : Avslag() {
                override val periode: Periode = stønadsperiode.periode
                override val simulering: Simulering? = null

                private val avslagsgrunnForBeregning: List<Avslagsgrunn> =
                    when (val vurdering = VurderAvslagGrunnetBeregning.vurderAvslagGrunnetBeregning(beregning)) {
                        is AvslagGrunnetBeregning.Ja -> listOf(vurdering.grunn.toAvslagsgrunn())
                        is AvslagGrunnetBeregning.Nei -> emptyList()
                    }

                init {
                    kastHvisGrunnlagsdataOgVilkårsvurderingerPeriodenOgBehandlingensPerioderErUlike()
                }

                override fun copyInternal(
                    stønadsperiode: Stønadsperiode,
                    grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderinger.Søknadsbehandling,
                ): MedBeregning {
                    return copy(
                        stønadsperiode = stønadsperiode,
                        grunnlagsdata = grunnlagsdataOgVilkårsvurderinger.grunnlagsdata,
                        vilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger.vilkårsvurderinger,
                    )
                }

                override fun nyOppgaveId(nyOppgaveId: OppgaveId): MedBeregning {
                    return this.copy(oppgaveId = nyOppgaveId)
                }

                override fun accept(visitor: SøknadsbehandlingVisitor) {
                    visitor.visit(this)
                }

                fun tilAttestering(saksbehandler: NavIdentBruker.Saksbehandler): TilAttestering.Avslag.MedBeregning =
                    TilAttestering.Avslag.MedBeregning(
                        id = id,
                        opprettet = opprettet,
                        sakId = sakId,
                        saksnummer = saksnummer,
                        søknad = søknad,
                        oppgaveId = oppgaveId,
                        fnr = fnr,
                        beregning = beregning,
                        saksbehandler = saksbehandler,
                        fritekstTilBrev = fritekstTilBrev,
                        stønadsperiode = stønadsperiode,
                        grunnlagsdata = grunnlagsdata,
                        vilkårsvurderinger = vilkårsvurderinger,
                        attesteringer = attesteringer,
                        avkorting = avkorting,
                        sakstype = sakstype,
                    )

                // TODO fiks typing/gyldig tilstand/vilkår fradrag?
                override val avslagsgrunner: List<Avslagsgrunn> = when (val vilkår = vilkårsvurderinger.vurdering) {
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

                override val beregning = null
                override val simulering: Simulering? = null

                override fun copyInternal(
                    stønadsperiode: Stønadsperiode,
                    grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderinger.Søknadsbehandling,
                ): UtenBeregning {
                    return copy(
                        stønadsperiode = stønadsperiode,
                        grunnlagsdata = grunnlagsdataOgVilkårsvurderinger.grunnlagsdata,
                        vilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger.vilkårsvurderinger,
                    )
                }

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
                        id = id,
                        opprettet = opprettet,
                        sakId = sakId,
                        saksnummer = saksnummer,
                        søknad = søknad,
                        oppgaveId = oppgaveId,
                        fnr = fnr,
                        saksbehandler = saksbehandler,
                        fritekstTilBrev = fritekstTilBrev,
                        stønadsperiode = stønadsperiode,
                        grunnlagsdata = grunnlagsdata,
                        vilkårsvurderinger = vilkårsvurderinger,
                        attesteringer = attesteringer,
                        avkorting = avkorting,
                        sakstype = sakstype,
                    )

                // TODO fiks typing/gyldig tilstand/vilkår fradrag?
                override val avslagsgrunner: List<Avslagsgrunn> = when (val vilkår = vilkårsvurderinger.vurdering) {
                    is Vilkårsvurderingsresultat.Avslag -> vilkår.avslagsgrunner
                    is Vilkårsvurderingsresultat.Innvilget -> emptyList()
                    is Vilkårsvurderingsresultat.Uavklart -> emptyList()
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
        abstract override val fnr: Fnr
        abstract override val saksbehandler: NavIdentBruker.Saksbehandler
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
            override val fnr: Fnr,
            override val beregning: Beregning,
            override val simulering: Simulering,
            override val saksbehandler: NavIdentBruker.Saksbehandler,
            override val attesteringer: Attesteringshistorikk,
            override val fritekstTilBrev: String,
            override val stønadsperiode: Stønadsperiode,
            override val grunnlagsdata: Grunnlagsdata,
            override val vilkårsvurderinger: Vilkårsvurderinger.Søknadsbehandling,
            override val avkorting: AvkortingVedSøknadsbehandling.Iverksatt,
            override val sakstype: Sakstype,
        ) : Iverksatt() {
            override val periode: Periode = stønadsperiode.periode

            init {
                grunnlagsdataOgVilkårsvurderinger.krevAlleVilkårInnvilget()
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
                override val fnr: Fnr,
                override val beregning: Beregning,
                override val saksbehandler: NavIdentBruker.Saksbehandler,
                override val attesteringer: Attesteringshistorikk,
                override val fritekstTilBrev: String,
                override val stønadsperiode: Stønadsperiode,
                override val grunnlagsdata: Grunnlagsdata,
                override val vilkårsvurderinger: Vilkårsvurderinger.Søknadsbehandling,
                override val avkorting: AvkortingVedSøknadsbehandling.Iverksatt.KanIkkeHåndtere,
                override val sakstype: Sakstype,
            ) : Avslag() {
                override val periode: Periode = stønadsperiode.periode
                override val simulering: Simulering? = null

                init {
                    grunnlagsdataOgVilkårsvurderinger.krevAlleVilkårInnvilget()
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
                override val avslagsgrunner: List<Avslagsgrunn> = when (val vilkår = vilkårsvurderinger.vurdering) {
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
                override val periode: Periode = stønadsperiode.periode

                override val beregning = null
                override val simulering: Simulering? = null

                init {
                    grunnlagsdataOgVilkårsvurderinger.krevMinstEttAvslag()
                    kastHvisGrunnlagsdataOgVilkårsvurderingerPeriodenOgBehandlingensPerioderErUlike()
                }

                override fun accept(visitor: SøknadsbehandlingVisitor) {
                    visitor.visit(this)
                }

                // TODO fiks typing/gyldig tilstand/vilkår fradrag?
                override val avslagsgrunner: List<Avslagsgrunn> = when (val vilkår = vilkårsvurderinger.vurdering) {
                    is Vilkårsvurderingsresultat.Avslag -> vilkår.avslagsgrunner
                    is Vilkårsvurderingsresultat.Innvilget -> emptyList()
                    is Vilkårsvurderingsresultat.Uavklart -> emptyList()
                }
            }
        }
    }
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
