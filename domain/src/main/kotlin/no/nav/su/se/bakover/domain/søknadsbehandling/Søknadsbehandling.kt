package no.nav.su.se.bakover.domain.søknadsbehandling

import arrow.core.Either
import arrow.core.NonEmptyList
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.NavIdentBruker
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.domain.avkorting.AvkortingVedSøknadsbehandling
import no.nav.su.se.bakover.domain.avkorting.Avkortingsplan
import no.nav.su.se.bakover.domain.behandling.Attesteringshistorikk
import no.nav.su.se.bakover.domain.behandling.AvslagGrunnetBeregning
import no.nav.su.se.bakover.domain.behandling.BehandlingMedAttestering
import no.nav.su.se.bakover.domain.behandling.BehandlingMedOppgave
import no.nav.su.se.bakover.domain.behandling.MedSaksbehandlerHistorikk
import no.nav.su.se.bakover.domain.behandling.VurderAvslagGrunnetBeregning
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.beregning.BeregningStrategyFactory
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.domain.grunnlag.EksterneGrunnlag
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag.Fradragsgrunnlag.Companion.perioder
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.grunnlag.GrunnlagsdataOgVilkårsvurderinger
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.sak.Sakstype
import no.nav.su.se.bakover.domain.sak.SimulerUtbetalingFeilet
import no.nav.su.se.bakover.domain.satser.SatsFactory
import no.nav.su.se.bakover.domain.skatt.EksternGrunnlagSkattRequest
import no.nav.su.se.bakover.domain.søknad.LukkSøknadCommand
import no.nav.su.se.bakover.domain.søknad.Søknad
import no.nav.su.se.bakover.domain.søknadsbehandling.VilkårsvurdertSøknadsbehandling.Companion.opprett
import no.nav.su.se.bakover.domain.søknadsbehandling.stønadsperiode.Aldersvurdering
import no.nav.su.se.bakover.domain.søknadsbehandling.stønadsperiode.Stønadsperiode
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
import no.nav.su.se.bakover.domain.vilkår.formue.LeggTilFormuevilkårRequest
import no.nav.su.se.bakover.domain.vilkår.inneholderAlle
import no.nav.su.se.bakover.domain.visitor.Visitable
import java.time.Clock

sealed class Søknadsbehandling :
    BehandlingMedOppgave,
    BehandlingMedAttestering,
    Visitable<SøknadsbehandlingVisitor>,
    MedSaksbehandlerHistorikk<Søknadsbehandlingshendelse> {
    abstract val søknad: Søknad.Journalført.MedOppgave

    abstract val aldersvurdering: Aldersvurdering?
    abstract val stønadsperiode: Stønadsperiode?
    abstract override val grunnlagsdata: Grunnlagsdata
    abstract override val vilkårsvurderinger: Vilkårsvurderinger.Søknadsbehandling
    abstract override val eksterneGrunnlag: EksterneGrunnlag
    abstract override val attesteringer: Attesteringshistorikk
    abstract val avkorting: AvkortingVedSøknadsbehandling

    abstract override val søknadsbehandlingsHistorikk: Søknadsbehandlingshistorikk

    // TODO ia: fritekst bør flyttes ut av denne klassen og til et eget konsept (som også omfatter fritekst på revurderinger)
    abstract val fritekstTilBrev: String

    val erIverksatt: Boolean by lazy { this is IverksattSøknadsbehandling.Avslag || this is IverksattSøknadsbehandling.Innvilget }
    val erLukket: Boolean by lazy { this is LukketSøknadsbehandling }

    abstract val saksbehandler: NavIdentBruker.Saksbehandler
    abstract override val beregning: Beregning?
    abstract override val simulering: Simulering?

    fun erÅpen(): Boolean {
        return !(erIverksatt || erLukket)
    }

    val grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderinger.Søknadsbehandling
        get() = GrunnlagsdataOgVilkårsvurderinger.Søknadsbehandling(
            grunnlagsdata = grunnlagsdata,
            vilkårsvurderinger = vilkårsvurderinger,
            eksterneGrunnlag = eksterneGrunnlag,
        )

    protected fun kastHvisGrunnlagsdataOgVilkårsvurderingerPeriodenOgBehandlingensPerioderErUlike() {
        if (grunnlagsdataOgVilkårsvurderinger.periode() == null) return
        if (grunnlagsdataOgVilkårsvurderinger.periode() != periode) {
            // Det er Søknadbehandling sin oppgave og vurdere om grunnlagsdataOgVilkårsvurderinger
            // sin periode tilsvarer søknadbehandlingens periode.
            throw IllegalArgumentException("Perioden til søknadsbehandlingen: $periode var ulik grunnlagene/vilkårsvurderingene sin periode: ${grunnlagsdataOgVilkårsvurderinger.periode()}")
        }
    }

    abstract fun leggTilSkatt(skatt: EksternGrunnlagSkattRequest): Either<KunneIkkeLeggeTilSkattegrunnlag, Søknadsbehandling>

    fun lukkSøknadsbehandlingOgSøknad(
        lukkSøknadCommand: LukkSøknadCommand,
    ): Either<KunneIkkeLukkeSøknadsbehandling, LukketSøknadsbehandling> = LukketSøknadsbehandling.tryCreate(
        søknadsbehandlingSomSkalLukkes = this,
        lukkSøknadCommand = lukkSøknadCommand,
    )

    private fun validerFradragsgrunnlag(fradragsgrunnlag: List<Grunnlag.Fradragsgrunnlag>): Either<KunneIkkeLeggeTilGrunnlag.KunneIkkeLeggeTilFradragsgrunnlag, Unit> {
        if (fradragsgrunnlag.isNotEmpty()) {
            if (!periode.inneholder(fradragsgrunnlag.perioder())) {
                return KunneIkkeLeggeTilGrunnlag.KunneIkkeLeggeTilFradragsgrunnlag.GrunnlagetMåVæreInnenforBehandlingsperioden.left()
            }
        }
        return Unit.right()
    }

    fun leggTilFradragsgrunnlagFraSaksbehandler(
        saksbehandler: NavIdentBruker.Saksbehandler,
        fradragsgrunnlag: List<Grunnlag.Fradragsgrunnlag>,
        clock: Clock,
    ) = vilkårsvurder(saksbehandler).let {
        when (it) {
            is KanBeregnes -> leggTilFradragsgrunnlagInternalForSaksbehandler(saksbehandler, fradragsgrunnlag, clock)
            else -> KunneIkkeLeggeTilGrunnlag.KunneIkkeLeggeTilFradragsgrunnlag.IkkeLovÅLeggeTilFradragIDenneStatusen(
                this::class,
            ).left()
        }
    }

    protected fun leggTilFradragsgrunnlagForBeregning(
        fradragsgrunnlag: List<Grunnlag.Fradragsgrunnlag>,
    ) = vilkårsvurder(saksbehandler).let {
        when (it) {
            is KanBeregnes -> leggTilFradragsgrunnlagInternalForBeregning(fradragsgrunnlag)
            else -> KunneIkkeLeggeTilGrunnlag.KunneIkkeLeggeTilFradragsgrunnlag.IkkeLovÅLeggeTilFradragIDenneStatusen(
                this::class,
            ).left()
        }
    }

    private fun leggTilFradragsgrunnlagInternalForSaksbehandler(
        saksbehandler: NavIdentBruker.Saksbehandler,
        fradragsgrunnlag: List<Grunnlag.Fradragsgrunnlag>,
        clock: Clock,
    ) = validerFradragsgrunnlag(fradragsgrunnlag).map {
        copyInternal(
            grunnlagsdataOgVilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger.leggTilFradragsgrunnlag(
                fradragsgrunnlag,
            ),
            avkorting = avkorting,
            søknadsbehandlingshistorikk = this.søknadsbehandlingsHistorikk.leggTilNyHendelse(
                Søknadsbehandlingshendelse(
                    tidspunkt = Tidspunkt.now(clock),
                    saksbehandler = saksbehandler,
                    handling = SøknadsbehandlingsHandling.OppdatertFradragsgrunnlag,
                ),
            ),
        ).vilkårsvurder(saksbehandler) as VilkårsvurdertSøknadsbehandling.Innvilget // TODO cast
    }

    private fun leggTilFradragsgrunnlagInternalForBeregning(
        fradragsgrunnlag: List<Grunnlag.Fradragsgrunnlag>,
    ) = validerFradragsgrunnlag(fradragsgrunnlag).map {
        copyInternal(
            grunnlagsdataOgVilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger.leggTilFradragsgrunnlag(
                fradragsgrunnlag,
            ),
            avkorting = avkorting,
            søknadsbehandlingshistorikk = this.søknadsbehandlingsHistorikk,
        ).vilkårsvurder(saksbehandler) as VilkårsvurdertSøknadsbehandling.Innvilget // TODO cast
    }

    /**
     * TODO("bør vi skille på oppdatering og fullføring (ufullstendig vs fullstendig bosituasjon)")
     * Ideelt sett så ville vi ikke tatt inn hendelse, men pga vi har mistet contexten på dette tidspunktet, så tar vi den inn
     */
    fun oppdaterBosituasjon(
        saksbehandler: NavIdentBruker.Saksbehandler,
        bosituasjon: Grunnlag.Bosituasjon.Fullstendig,
        hendelse: Søknadsbehandlingshendelse,
    ): Either<KunneIkkeLeggeTilGrunnlag.KunneIkkeOppdatereBosituasjon, VilkårsvurdertSøknadsbehandling> {
        return if (this is KanOppdaterePeriodeGrunnlagVilkår) {
            if (this.periode != bosituasjon.periode) {
                return KunneIkkeLeggeTilGrunnlag.KunneIkkeOppdatereBosituasjon.GrunnlagetMåVæreInnenforBehandlingsperioden.left()
            }
            oppdaterBosituasjonInternal(saksbehandler, bosituasjon, hendelse).right()
        } else {
            KunneIkkeLeggeTilGrunnlag.KunneIkkeOppdatereBosituasjon.UgyldigTilstand(
                this::class,
                VilkårsvurdertSøknadsbehandling::class,
            ).left()
        }
    }

    /**
     * Ideelt sett så ville vi ikke tatt inn hendelse, men pga vi har mistet contexten på dette tidspunktet, så tar vi den inn
     */
    private fun oppdaterBosituasjonInternal(
        saksbehandler: NavIdentBruker.Saksbehandler,
        bosituasjon: Grunnlag.Bosituasjon.Fullstendig,
        hendelse: Søknadsbehandlingshendelse,
    ): VilkårsvurdertSøknadsbehandling {
        return grunnlagsdataOgVilkårsvurderinger.oppdaterBosituasjon(listOf(bosituasjon)).let {
            copyInternal(
                grunnlagsdataOgVilkårsvurderinger = it,
                avkorting = avkorting,
                søknadsbehandlingshistorikk = this.søknadsbehandlingsHistorikk.leggTilNyHendelse(hendelse),
            ).vilkårsvurder(saksbehandler)
        }
    }

    /**
     * Eksponerer deler av subklassenes copy-konstruktør slik at vi kan eliminere behovet for duplikate implementasjoner i hver subklasse.
     */
    protected abstract fun copyInternal(
        stønadsperiode: Stønadsperiode = this.stønadsperiode!!,
        grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderinger.Søknadsbehandling = this.grunnlagsdataOgVilkårsvurderinger,
        avkorting: AvkortingVedSøknadsbehandling = this.avkorting,
        søknadsbehandlingshistorikk: Søknadsbehandlingshistorikk = this.søknadsbehandlingsHistorikk,
        aldersvurdering: Aldersvurdering = this.aldersvurdering!!,
    ): Søknadsbehandling

    fun leggTilUtenlandsopphold(
        saksbehandler: NavIdentBruker.Saksbehandler,
        utenlandsopphold: UtenlandsoppholdVilkår.Vurdert,
        clock: Clock,
    ) = if (this is KanOppdaterePeriodeGrunnlagVilkår) {
        leggTilUtenlandsoppholdInternal(saksbehandler, utenlandsopphold, clock)
    } else {
        KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilUtenlandsopphold.IkkeLovÅLeggeTilUtenlandsoppholdIDenneStatusen(
            fra = this::class,
            til = VilkårsvurdertSøknadsbehandling::class,
        ).left()
    }

    private fun leggTilUtenlandsoppholdInternal(
        saksbehandler: NavIdentBruker.Saksbehandler,
        vilkår: UtenlandsoppholdVilkår.Vurdert,
        clock: Clock,
    ) = valider<KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilUtenlandsopphold>(vilkår).map {
        copyInternal(
            grunnlagsdataOgVilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger.leggTil(vilkår),
            avkorting = avkorting,
            søknadsbehandlingshistorikk = this.søknadsbehandlingsHistorikk.leggTilNyHendelse(
                Søknadsbehandlingshendelse(
                    tidspunkt = Tidspunkt.now(clock),
                    saksbehandler = saksbehandler,
                    handling = SøknadsbehandlingsHandling.OppdatertUtenlandsopphold,
                ),
            ),
        ).vilkårsvurder(saksbehandler)
    }

    fun vilkårsvurder(saksbehandler: NavIdentBruker.Saksbehandler): VilkårsvurdertSøknadsbehandling = opprett(
        id = id,
        opprettet = opprettet,
        sakId = sakId,
        saksnummer = saksnummer,
        søknad = søknad,
        oppgaveId = oppgaveId,
        fnr = fnr,
        fritekstTilBrev = fritekstTilBrev,
        aldersvurdering = aldersvurdering!!,
        grunnlagsdataOgVilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger,
        attesteringer = attesteringer,
        saksbehandlingsHistorikk = søknadsbehandlingsHistorikk,
        avkorting = avkorting.uhåndtert(),
        sakstype = sakstype,
        saksbehandler = saksbehandler,
    )

    interface KanOppdaterePeriodeGrunnlagVilkår
    interface KanBeregnes : KanOppdaterePeriodeGrunnlagVilkår {
        val avkorting: AvkortingVedSøknadsbehandling.Uhåndtert
    }

    fun leggTilFormuegrunnlag(
        request: LeggTilFormuevilkårRequest,
        formuegrenserFactory: FormuegrenserFactory,
    ) = if (this is KanOppdaterePeriodeGrunnlagVilkår) {
        leggTilFormuevilkårInternal(request, formuegrenserFactory)
    } else {
        KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilFormuevilkår.UgyldigTilstand(
            fra = this::class,
            til = VilkårsvurdertSøknadsbehandling::class,
        ).left()
    }

    private fun leggTilFormuevilkårInternal(
        request: LeggTilFormuevilkårRequest,
        formuegrenserFactory: FormuegrenserFactory,
    ): Either<KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilFormuevilkår, VilkårsvurdertSøknadsbehandling> {
        val vilkår = request.toDomain(
            bosituasjon = grunnlagsdata.bosituasjon,
            behandlingsperiode = stønadsperiode?.periode
                ?: throw IllegalStateException("Burde ha hatt en stønadsperiode på dette tidspunktet. id $id"),
            formuegrenserFactory = formuegrenserFactory,
        ).getOrElse {
            return KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilFormuevilkår.KunneIkkeMappeTilDomenet(it).left()
        }
        return valider<KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilFormuevilkår>(vilkår).map {
            copyInternal(
                grunnlagsdataOgVilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger.oppdaterFormuevilkår(vilkår),
                avkorting = avkorting,
                søknadsbehandlingshistorikk = this.søknadsbehandlingsHistorikk.leggTilNyHendelse(
                    request.handling(request.tidspunkt),
                ),
            ).vilkårsvurder(request.saksbehandler)
        }
    }

    fun leggTilAldersvurderingOgStønadsperiodeForAvslagPgaManglendeDokumentasjon(
        aldersvurdering: Aldersvurdering.SkalIkkeVurderes,
        formuegrenserFactory: FormuegrenserFactory,
        clock: Clock,
        avkorting: AvkortingVedSøknadsbehandling,
    ): VilkårsvurdertSøknadsbehandling {
        return grunnlagsdataOgVilkårsvurderinger.oppdaterStønadsperiode(
            stønadsperiode = aldersvurdering.stønadsperiode,
            formuegrenserFactory = formuegrenserFactory,
            clock = clock,
        ).getOrElse {
            throw IllegalArgumentException("Feil ved oppdatering av stønadsperiode for grunnlagsdata og vilkårsvurderinger. id $id")
        }.let {
            copyInternal(
                stønadsperiode = aldersvurdering.stønadsperiode,
                aldersvurdering = aldersvurdering,
                grunnlagsdataOgVilkårsvurderinger = it,
                avkorting = avkorting,
                søknadsbehandlingshistorikk = this.søknadsbehandlingsHistorikk,
            ).vilkårsvurder(saksbehandler)
        }
    }

    fun oppdaterStønadsperiode(
        oppdatertStønadsperiode: Stønadsperiode,
        formuegrenserFactory: FormuegrenserFactory,
        clock: Clock,
        avkorting: AvkortingVedSøknadsbehandling,
    ): Either<KunneIkkeOppdatereStønadsperiode, VilkårsvurdertSøknadsbehandling> =
        if (this is KanOppdaterePeriodeGrunnlagVilkår) {
            oppdaterStønadsperiodeInternal(
                oppdatertStønadsperiode = oppdatertStønadsperiode,
                formuegrenserFactory = formuegrenserFactory,
                clock = clock,
                avkorting = avkorting,
            )
        } else {
            KunneIkkeOppdatereStønadsperiode.UgyldigTilstand(this::class).left()
        }

    private fun oppdaterStønadsperiodeInternal(
        oppdatertStønadsperiode: Stønadsperiode,
        formuegrenserFactory: FormuegrenserFactory,
        clock: Clock,
        avkorting: AvkortingVedSøknadsbehandling,
    ): Either<KunneIkkeOppdatereStønadsperiode, VilkårsvurdertSøknadsbehandling> {
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
                avkorting = avkorting,
                søknadsbehandlingshistorikk = this.søknadsbehandlingsHistorikk,
            ).vilkårsvurder(saksbehandler)
        }
    }

    fun oppdaterStønadsperiodeForSaksbehandler(
        saksbehandler: NavIdentBruker.Saksbehandler,
        aldersvurdering: Aldersvurdering.Vurdert,
        formuegrenserFactory: FormuegrenserFactory,
        clock: Clock,
        avkorting: AvkortingVedSøknadsbehandling,
    ): Either<KunneIkkeOppdatereStønadsperiode, VilkårsvurdertSøknadsbehandling> =
        if (this is KanOppdaterePeriodeGrunnlagVilkår) {
            oppdaterStønadsperiodeInternalForSaksbehandler(
                saksbehandler = saksbehandler,
                aldersvurdering = aldersvurdering,
                formuegrenserFactory = formuegrenserFactory,
                clock = clock,
                avkorting = avkorting,
            )
        } else {
            KunneIkkeOppdatereStønadsperiode.UgyldigTilstand(this::class).left()
        }

    private fun oppdaterStønadsperiodeInternalForSaksbehandler(
        saksbehandler: NavIdentBruker.Saksbehandler,
        aldersvurdering: Aldersvurdering.Vurdert,
        formuegrenserFactory: FormuegrenserFactory,
        clock: Clock,
        avkorting: AvkortingVedSøknadsbehandling,
    ): Either<KunneIkkeOppdatereStønadsperiode, VilkårsvurdertSøknadsbehandling> {
        return grunnlagsdataOgVilkårsvurderinger.oppdaterStønadsperiode(
            stønadsperiode = aldersvurdering.stønadsperiode,
            formuegrenserFactory = formuegrenserFactory,
            clock = clock,
        ).mapLeft {
            KunneIkkeOppdatereStønadsperiode.KunneIkkeOppdatereGrunnlagsdata(it)
        }.map {
            copyInternal(
                stønadsperiode = aldersvurdering.stønadsperiode,
                aldersvurdering = aldersvurdering,
                grunnlagsdataOgVilkårsvurderinger = it,
                avkorting = avkorting,
                søknadsbehandlingshistorikk = this.søknadsbehandlingsHistorikk.leggTilNyHendelse(
                    Søknadsbehandlingshendelse(
                        tidspunkt = Tidspunkt.now(clock),
                        saksbehandler = saksbehandler,
                        handling = SøknadsbehandlingsHandling.OppdatertStønadsperiode,
                    ),
                ),
            ).vilkårsvurder(saksbehandler)
        }
    }

    /**
     * Denne brukes i forbindelse med at systemet må legge inn opplysningsplikt vilkåret, da
     * dette ikke er et steg som saksbehandler gjør frontend
     *
     * Den andre funksjonen [leggTilOpplysningspliktVilkårForSaksbehandler], brukes i forbindelse med lukking / avslåPgaManglende dokumentasjon. Det er en saksbehandler handling,
     * som vi bruker for å oppdatere handlingene.
     */
    fun leggTilOpplysningspliktVilkår(
        opplysningspliktVilkår: OpplysningspliktVilkår.Vurdert,
    ): Either<KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilOpplysningsplikt, VilkårsvurdertSøknadsbehandling> {
        return if (this is KanOppdaterePeriodeGrunnlagVilkår) {
            leggTilOpplysningspliktVilkårInternal(opplysningspliktVilkår)
        } else {
            KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilOpplysningsplikt.UgyldigTilstand(this::class).left()
        }
    }

    private fun leggTilOpplysningspliktVilkårInternal(
        vilkår: OpplysningspliktVilkår.Vurdert,
    ): Either<KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilOpplysningsplikt, VilkårsvurdertSøknadsbehandling> {
        return valider<KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilOpplysningsplikt>(vilkår).map {
            copyInternal(
                grunnlagsdataOgVilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger.leggTil(vilkår),
                avkorting = avkorting,
                søknadsbehandlingshistorikk = this.søknadsbehandlingsHistorikk,
            ).vilkårsvurder(saksbehandler)
        }
    }

    /**
     * Denne brukes i forbindelse med lukking / avslåPgaManglende dokumentasjon. Det er en saksbehandler handling,
     * som vi bruker for å oppdatere handlingene.
     *
     * Den andre funksjonen [leggTilOpplysningspliktVilkår], er for at systemet skal legge inn vilkåret, da dette
     * ikke gjøres frontend
     */
    fun leggTilOpplysningspliktVilkårForSaksbehandler(
        saksbehandler: NavIdentBruker.Saksbehandler,
        opplysningspliktVilkår: OpplysningspliktVilkår.Vurdert,
        clock: Clock,
    ): Either<KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilOpplysningsplikt, VilkårsvurdertSøknadsbehandling> {
        return if (this is KanOppdaterePeriodeGrunnlagVilkår) {
            leggTilOpplysningspliktVilkårInternalForSaksbehandler(saksbehandler, opplysningspliktVilkår, clock)
        } else {
            KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilOpplysningsplikt.UgyldigTilstand(this::class).left()
        }
    }

    private fun leggTilOpplysningspliktVilkårInternalForSaksbehandler(
        saksbehandler: NavIdentBruker.Saksbehandler,
        vilkår: OpplysningspliktVilkår.Vurdert,
        clock: Clock,
    ): Either<KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilOpplysningsplikt, VilkårsvurdertSøknadsbehandling> {
        return valider<KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilOpplysningsplikt>(vilkår).map {
            copyInternal(
                grunnlagsdataOgVilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger.leggTil(vilkår),
                avkorting = avkorting,
                søknadsbehandlingshistorikk = this.søknadsbehandlingsHistorikk.leggTilNyHendelse(
                    Søknadsbehandlingshendelse(
                        tidspunkt = Tidspunkt.now(clock),
                        saksbehandler = saksbehandler,
                        handling = SøknadsbehandlingsHandling.OppdatertOpplysningsplikt,
                    ),
                ),
            ).vilkårsvurder(saksbehandler)
        }
    }

    fun leggTilPensjonsVilkår(
        vilkår: PensjonsVilkår.Vurdert,
        saksbehandler: NavIdentBruker.Saksbehandler,
    ): Either<KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilPensjonsVilkår, VilkårsvurdertSøknadsbehandling> {
        return if (this is KanOppdaterePeriodeGrunnlagVilkår) {
            leggTilPensjonsVilkårInternal(saksbehandler, vilkår)
        } else {
            KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilPensjonsVilkår.UgyldigTilstand(this::class).left()
        }
    }

    private fun leggTilPensjonsVilkårInternal(
        saksbehandler: NavIdentBruker.Saksbehandler,
        vilkår: PensjonsVilkår.Vurdert,
    ): Either<KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilPensjonsVilkår, VilkårsvurdertSøknadsbehandling> {
        return valider<KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilPensjonsVilkår>(vilkår).map {
            copyInternal(
                grunnlagsdataOgVilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger.leggTil(vilkår),
                avkorting = avkorting,
                søknadsbehandlingshistorikk = this.søknadsbehandlingsHistorikk,
            ).vilkårsvurder(saksbehandler)
        }
    }

    fun leggTilPersonligOppmøteVilkår(
        saksbehandler: NavIdentBruker.Saksbehandler,
        vilkår: PersonligOppmøteVilkår.Vurdert,
        clock: Clock,
    ): Either<KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilPersonligOppmøteVilkår, VilkårsvurdertSøknadsbehandling> {
        return if (this is KanOppdaterePeriodeGrunnlagVilkår) {
            leggTilPersonligOppmøteVilkårInternal(saksbehandler, vilkår, clock)
        } else {
            KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilPersonligOppmøteVilkår.UgyldigTilstand(
                this::class,
                VilkårsvurdertSøknadsbehandling::class,
            ).left()
        }
    }

    private fun leggTilPersonligOppmøteVilkårInternal(
        saksbehandler: NavIdentBruker.Saksbehandler,
        vilkår: PersonligOppmøteVilkår.Vurdert,
        clock: Clock,
    ): Either<KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilPersonligOppmøteVilkår, VilkårsvurdertSøknadsbehandling> {
        return valider<KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilPersonligOppmøteVilkår>(vilkår).map {
            copyInternal(
                grunnlagsdataOgVilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger.leggTil(vilkår),
                avkorting = avkorting,
                søknadsbehandlingshistorikk = this.søknadsbehandlingsHistorikk.leggTilNyHendelse(
                    Søknadsbehandlingshendelse(
                        tidspunkt = Tidspunkt.now(clock),
                        saksbehandler = saksbehandler,
                        handling = SøknadsbehandlingsHandling.OppdatertPersonligOppmøte,
                    ),
                ),
            ).vilkårsvurder(saksbehandler)
        }
    }

    fun leggTilLovligOpphold(
        saksbehandler: NavIdentBruker.Saksbehandler,
        lovligOppholdVilkår: LovligOppholdVilkår.Vurdert,
        clock: Clock,
    ) = if (this is KanOppdaterePeriodeGrunnlagVilkår) {
        leggTilLovligOppholdInternal(
            saksbehandler,
            lovligOppholdVilkår,
            clock,
        )
    } else {
        KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilLovligOpphold.UgyldigTilstand.Søknadsbehandling(
            this::class,
            VilkårsvurdertSøknadsbehandling::class,
        ).left()
    }

    private fun leggTilLovligOppholdInternal(
        saksbehandler: NavIdentBruker.Saksbehandler,
        lovligOppholdVilkår: LovligOppholdVilkår.Vurdert,
        clock: Clock,
    ) = valider<KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilLovligOpphold>(lovligOppholdVilkår).map {
        copyInternal(
            grunnlagsdataOgVilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger.leggTil(lovligOppholdVilkår),
            avkorting = avkorting,
            søknadsbehandlingshistorikk = this.søknadsbehandlingsHistorikk.leggTilNyHendelse(
                Søknadsbehandlingshendelse(
                    tidspunkt = Tidspunkt.now(clock),
                    saksbehandler = saksbehandler,
                    handling = SøknadsbehandlingsHandling.OppdatertLovligOpphold,
                ),
            ),
        ).vilkårsvurder(saksbehandler)
    }

    fun beregn(
        nySaksbehandler: NavIdentBruker.Saksbehandler,
        begrunnelse: String?,
        clock: Clock,
        satsFactory: SatsFactory,
    ): Either<KunneIkkeBeregne, BeregnetSøknadsbehandling> {
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
        clock: Clock,
    ): Either<KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilInstitusjonsoppholdVilkår, VilkårsvurdertSøknadsbehandling> {
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
                avkorting = avkorting,
                søknadsbehandlingshistorikk = this.søknadsbehandlingsHistorikk.leggTilNyHendelse(
                    Søknadsbehandlingshendelse(
                        tidspunkt = Tidspunkt.now(clock),
                        saksbehandler = saksbehandler,
                        handling = SøknadsbehandlingsHandling.OppdatertInstitusjonsopphold,
                    ),
                ),
            ).vilkårsvurder(saksbehandler).right()
        } else {
            KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilInstitusjonsoppholdVilkår.UgyldigTilstand(this::class).left()
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T : KunneIkkeLeggeTilVilkår> valider(vilkår: Vilkår): Either<T, Unit> {
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
        clock: Clock,
    ): Either<KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilUførevilkår, VilkårsvurdertSøknadsbehandling> {
        return if (this is KanOppdaterePeriodeGrunnlagVilkår) {
            leggTilUførevilkårInternal(saksbehandler, uførhet, clock)
        } else {
            KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilUførevilkår.UgyldigTilstand(
                this::class,
                VilkårsvurdertSøknadsbehandling::class,
            )
                .left()
        }
    }

    private fun leggTilUførevilkårInternal(
        saksbehandler: NavIdentBruker.Saksbehandler,
        vilkår: UføreVilkår.Vurdert,
        clock: Clock,
    ): Either<KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilUførevilkår, VilkårsvurdertSøknadsbehandling> {
        return valider<KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilUførevilkår>(vilkår).map {
            copyInternal(
                grunnlagsdataOgVilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger.leggTil(vilkår),
                avkorting = avkorting,
                søknadsbehandlingshistorikk = this.søknadsbehandlingsHistorikk.leggTilNyHendelse(
                    Søknadsbehandlingshendelse(
                        tidspunkt = Tidspunkt.now(clock),
                        saksbehandler = saksbehandler,
                        handling = SøknadsbehandlingsHandling.OppdatertUførhet,
                    ),
                ),
            ).vilkårsvurder(saksbehandler)
        }
    }

    fun leggTilFamiliegjenforeningvilkår(
        saksbehandler: NavIdentBruker.Saksbehandler,
        familiegjenforening: FamiliegjenforeningVilkår.Vurdert,
    ): Either<KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilFamiliegjenforeningVilkår, VilkårsvurdertSøknadsbehandling> {
        return if (this is KanOppdaterePeriodeGrunnlagVilkår) {
            leggTilFamiliegjenforeningvilkårInternal(saksbehandler, familiegjenforening)
        } else {
            KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilFamiliegjenforeningVilkår.UgyldigTilstand(
                this::class,
                VilkårsvurdertSøknadsbehandling::class,
            ).left()
        }
    }

    private fun leggTilFamiliegjenforeningvilkårInternal(
        saksbehandler: NavIdentBruker.Saksbehandler,
        vilkår: FamiliegjenforeningVilkår.Vurdert,
    ): Either<KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilFamiliegjenforeningVilkår, VilkårsvurdertSøknadsbehandling> {
        return valider<KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilFamiliegjenforeningVilkår>(vilkår).map {
            copyInternal(
                grunnlagsdataOgVilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger.leggTil(vilkår),
                avkorting = avkorting,
                søknadsbehandlingshistorikk = this.søknadsbehandlingsHistorikk,
            ).vilkårsvurder(saksbehandler)
        }
    }

    fun leggTilFlyktningVilkår(
        saksbehandler: NavIdentBruker.Saksbehandler,
        vilkår: FlyktningVilkår.Vurdert,
        clock: Clock,
    ): Either<KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilFlyktningVilkår, VilkårsvurdertSøknadsbehandling> {
        return if (this is KanOppdaterePeriodeGrunnlagVilkår) {
            leggTilFlyktningVilkårInternal(saksbehandler, vilkår, clock)
        } else {
            KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilFlyktningVilkår.UgyldigTilstand(
                fra = this::class,
                til = VilkårsvurdertSøknadsbehandling::class,
            ).left()
        }
    }

    private fun leggTilFlyktningVilkårInternal(
        saksbehandler: NavIdentBruker.Saksbehandler,
        vilkår: FlyktningVilkår.Vurdert,
        clock: Clock,
    ): Either<KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilFlyktningVilkår, VilkårsvurdertSøknadsbehandling> {
        return copyInternal(
            grunnlagsdataOgVilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger.leggTil(vilkår),
            avkorting = avkorting,
            søknadsbehandlingshistorikk = this.søknadsbehandlingsHistorikk.leggTilNyHendelse(
                Søknadsbehandlingshendelse(
                    tidspunkt = Tidspunkt.now(clock),
                    saksbehandler = saksbehandler,
                    handling = SøknadsbehandlingsHandling.OppdatertFlyktning,
                ),
            ),
        ).vilkårsvurder(saksbehandler).right()
    }

    fun leggTilFastOppholdINorgeVilkår(
        saksbehandler: NavIdentBruker.Saksbehandler,
        vilkår: FastOppholdINorgeVilkår.Vurdert,
        clock: Clock,
    ): Either<KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilFastOppholdINorgeVilkår, VilkårsvurdertSøknadsbehandling> {
        return if (this is KanOppdaterePeriodeGrunnlagVilkår) {
            leggTilFastOppholdINorgeVilkårInternal(saksbehandler, vilkår, clock)
        } else {
            KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilFastOppholdINorgeVilkår.UgyldigTilstand(
                fra = this::class,
                til = VilkårsvurdertSøknadsbehandling::class,
            ).left()
        }
    }

    private fun leggTilFastOppholdINorgeVilkårInternal(
        saksbehandler: NavIdentBruker.Saksbehandler,
        vilkår: FastOppholdINorgeVilkår.Vurdert,
        clock: Clock,
    ): Either<KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilFastOppholdINorgeVilkår, VilkårsvurdertSøknadsbehandling> {
        return copyInternal(
            grunnlagsdataOgVilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger.leggTil(vilkår),
            avkorting = avkorting,
            søknadsbehandlingshistorikk = this.søknadsbehandlingsHistorikk.leggTilNyHendelse(
                Søknadsbehandlingshendelse(
                    tidspunkt = Tidspunkt.now(clock),
                    saksbehandler = saksbehandler,
                    handling = SøknadsbehandlingsHandling.OppdatertFastOppholdINorge,
                ),
            ),
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
        clock: Clock,
        simuler: (beregning: Beregning, uføregrunnlag: NonEmptyList<Grunnlag.Uføregrunnlag>?) -> Either<SimulerUtbetalingFeilet, Simulering>,
    ): Either<KunneIkkeSimulereBehandling, SimulertSøknadsbehandling> {
        return KunneIkkeSimulereBehandling.UgyldigTilstand(this::class).left()
    }

    private fun beregnInternal(
        nySaksbehandler: NavIdentBruker.Saksbehandler,
        søknadsbehandling: KanBeregnes,
        begrunnelse: String?,
        clock: Clock,
        beregningStrategyFactory: BeregningStrategyFactory,
    ): Either<KunneIkkeBeregne, BeregnetSøknadsbehandling> {
        return when (val avkort = søknadsbehandling.avkorting) {
            is AvkortingVedSøknadsbehandling.Uhåndtert.IngenUtestående -> {
                beregnUtenAvkorting(
                    begrunnelse = begrunnelse,
                    beregningStrategyFactory = beregningStrategyFactory,
                ).getOrElse { return it.left() }
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
                ).getOrElse { return it.left() }
            }
        }.let { (behandling, beregning) ->
            val nySøknadsbehandlingshistorikk = behandling.søknadsbehandlingsHistorikk.leggTilNyHendelse(
                saksbehandlingsHendelse = Søknadsbehandlingshendelse(
                    tidspunkt = Tidspunkt.now(clock),
                    saksbehandler = nySaksbehandler,
                    handling = SøknadsbehandlingsHandling.Beregnet,
                ),
            )
            when (VurderAvslagGrunnetBeregning.vurderAvslagGrunnetBeregning(beregning)) {
                is AvslagGrunnetBeregning.Ja -> BeregnetSøknadsbehandling.Avslag(
                    id = behandling.id,
                    opprettet = behandling.opprettet,
                    sakId = behandling.sakId,
                    saksnummer = behandling.saksnummer,
                    søknad = behandling.søknad,
                    oppgaveId = behandling.oppgaveId,
                    fnr = behandling.fnr,
                    beregning = beregning,
                    fritekstTilBrev = behandling.fritekstTilBrev,
                    aldersvurdering = behandling.aldersvurdering!!,
                    grunnlagsdata = behandling.grunnlagsdata,
                    vilkårsvurderinger = behandling.vilkårsvurderinger,
                    eksterneGrunnlag = behandling.eksterneGrunnlag,
                    attesteringer = behandling.attesteringer,
                    søknadsbehandlingsHistorikk = nySøknadsbehandlingshistorikk,
                    avkorting = behandling.avkorting.håndter().kanIkke(),
                    sakstype = behandling.sakstype,
                    saksbehandler = nySaksbehandler,
                )

                AvslagGrunnetBeregning.Nei -> {
                    BeregnetSøknadsbehandling.Innvilget(
                        id = behandling.id,
                        opprettet = behandling.opprettet,
                        sakId = behandling.sakId,
                        saksnummer = behandling.saksnummer,
                        søknad = behandling.søknad,
                        oppgaveId = behandling.oppgaveId,
                        fnr = behandling.fnr,
                        beregning = beregning,
                        fritekstTilBrev = behandling.fritekstTilBrev,
                        aldersvurdering = behandling.aldersvurdering!!,
                        grunnlagsdata = behandling.grunnlagsdata,
                        vilkårsvurderinger = behandling.vilkårsvurderinger,
                        eksterneGrunnlag = behandling.eksterneGrunnlag,
                        attesteringer = behandling.attesteringer,
                        søknadsbehandlingsHistorikk = nySøknadsbehandlingshistorikk,
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
        begrunnelse: String?,
        beregningStrategyFactory: BeregningStrategyFactory,
    ): Either<KunneIkkeBeregne, Pair<VilkårsvurdertSøknadsbehandling, Beregning>> {
        return leggTilFradragsgrunnlagForBeregning(
            fradragsgrunnlag = grunnlagsdata.fradragsgrunnlag.filterNot { it.fradragstype == Fradragstype.AvkortingUtenlandsopphold },
        ).getOrElse {
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
    ): Either<KunneIkkeBeregne, Pair<VilkårsvurdertSøknadsbehandling, Beregning>> {
        return beregnUtenAvkorting(
            begrunnelse,
            beregningStrategyFactory,
        ).map { (utenAvkorting, beregningUtenAvkorting) ->
            val fradragForAvkorting = Avkortingsplan(
                feilutbetaltBeløp = avkorting.avkortingsvarsel.hentUtbetalteBeløp().sum(),
                beregning = beregningUtenAvkorting,
                clock = clock,
            ).lagFradrag().getOrElse {
                return when (it) {
                    Avkortingsplan.KunneIkkeLageAvkortingsplan.AvkortingErUfullstendig -> {
                        KunneIkkeBeregne.AvkortingErUfullstendig.left()
                    }
                }
            }

            val medAvkorting = utenAvkorting.leggTilFradragsgrunnlagForBeregning(
                fradragsgrunnlag = utenAvkorting.grunnlagsdata.fradragsgrunnlag + fradragForAvkorting,
            ).getOrElse { return KunneIkkeBeregne.UgyldigTilstandForEndringAvFradrag(it).left() }

            medAvkorting to gjørBeregning(
                søknadsbehandling = medAvkorting,
                begrunnelse = begrunnelse,
                beregningStrategyFactory = beregningStrategyFactory,
            )
        }
    }
}

// Her trikses det litt for å få til at funksjonen returnerer den samme konkrete typen som den kalles på.
// Teoretisk sett skal ikke UNCHECKED_CAST være noe problem i dette tilfellet siden T er begrenset til subklasser av Søknadsbehandling.
// ... i hvert fall så lenge alle subklassene av Søknadsbehandling er data classes
@Suppress("UNCHECKED_CAST")
fun <T : Søknadsbehandling> T.medFritekstTilBrev(fritekstTilBrev: String): T = (
    // Her caster vi til Søknadsbehandling for å unngå å måtte ha en else-branch
    when (val x = this as Søknadsbehandling) {
        is BeregnetSøknadsbehandling.Avslag -> x.copy(fritekstTilBrev = fritekstTilBrev)
        is BeregnetSøknadsbehandling.Innvilget -> x.copy(fritekstTilBrev = fritekstTilBrev)
        is IverksattSøknadsbehandling.Avslag.MedBeregning -> x.copy(fritekstTilBrev = fritekstTilBrev)
        is IverksattSøknadsbehandling.Avslag.UtenBeregning -> x.copy(fritekstTilBrev = fritekstTilBrev)
        is IverksattSøknadsbehandling.Innvilget -> x.copy(fritekstTilBrev = fritekstTilBrev)
        is SimulertSøknadsbehandling -> x.copy(fritekstTilBrev = fritekstTilBrev)
        is SøknadsbehandlingTilAttestering.Avslag.MedBeregning -> x.copy(fritekstTilBrev = fritekstTilBrev)
        is SøknadsbehandlingTilAttestering.Avslag.UtenBeregning -> x.copy(fritekstTilBrev = fritekstTilBrev)
        is SøknadsbehandlingTilAttestering.Innvilget -> x.copy(fritekstTilBrev = fritekstTilBrev)
        is UnderkjentSøknadsbehandling.Avslag.MedBeregning -> x.copy(fritekstTilBrev = fritekstTilBrev)
        is UnderkjentSøknadsbehandling.Avslag.UtenBeregning -> x.copy(fritekstTilBrev = fritekstTilBrev)
        is UnderkjentSøknadsbehandling.Innvilget -> x.copy(fritekstTilBrev = fritekstTilBrev)
        is VilkårsvurdertSøknadsbehandling.Avslag -> x.copy(fritekstTilBrev = fritekstTilBrev)
        is VilkårsvurdertSøknadsbehandling.Innvilget -> x.copy(fritekstTilBrev = fritekstTilBrev)
        is VilkårsvurdertSøknadsbehandling.Uavklart -> x.copy(fritekstTilBrev = fritekstTilBrev)
        is LukketSøknadsbehandling -> throw IllegalArgumentException("Det støttes ikke å endre fritekstTilBrev på en lukket søknadsbehandling.")
    }
    // ... og så caster vi tilbake til T for at Kotlin skal henge med i svingene
    ) as T
