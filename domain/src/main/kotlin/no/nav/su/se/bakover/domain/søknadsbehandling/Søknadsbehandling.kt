package no.nav.su.se.bakover.domain.søknadsbehandling

import arrow.core.Either
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Person
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.Søknad
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
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag.Fradragsgrunnlag.Companion.periode
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.grunnlag.KunneIkkeLageGrunnlagsdata
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingFeilet
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.person.KunneIkkeHentePerson
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderingsresultat
import no.nav.su.se.bakover.domain.visitor.Visitable
import org.slf4j.LoggerFactory
import java.time.Clock
import java.util.UUID
import kotlin.reflect.KClass

private val log = LoggerFactory.getLogger("Søknadsbehandling.kt")

sealed class Søknadsbehandling : BehandlingMedOppgave, BehandlingMedAttestering, Visitable<SøknadsbehandlingVisitor> {
    abstract val søknad: Søknad.Journalført.MedOppgave
    abstract val behandlingsinformasjon: Behandlingsinformasjon

    // TODO jah: Denne kan fjernes fra domenet og heller la mappingen ligge i infrastruktur-laget
    abstract val status: BehandlingsStatus
    abstract val stønadsperiode: Stønadsperiode?
    abstract override val grunnlagsdata: Grunnlagsdata
    abstract override val vilkårsvurderinger: Vilkårsvurderinger.Søknadsbehandling
    abstract override val attesteringer: Attesteringshistorikk

    // TODO ia: fritekst bør flyttes ut av denne klassen og til et eget konsept (som også omfatter fritekst på revurderinger)
    abstract val fritekstTilBrev: String

    val erIverksatt: Boolean by lazy { this is Iverksatt.Avslag || this is Iverksatt.Innvilget }
    val erLukket: Boolean by lazy { this is LukketSøknadsbehandling }

    sealed class KunneIkkeLukkeSøknadsbehandling {
        object KanIkkeLukkeEnAlleredeLukketSøknadsbehandling : KunneIkkeLukkeSøknadsbehandling()
        object KanIkkeLukkeEnIverksattSøknadsbehandling : KunneIkkeLukkeSøknadsbehandling()
        object KanIkkeLukkeEnSøknadsbehandlingTilAttestering : KunneIkkeLukkeSøknadsbehandling()
    }

    fun lukkSøknadsbehandling(): Either<KunneIkkeLukkeSøknadsbehandling, LukketSøknadsbehandling> {
        return LukketSøknadsbehandling.tryCreate(this)
    }

    sealed class KunneIkkeLeggeTilFradragsgrunnlag {
        object IkkeLovÅLeggeTilFradragIDenneStatusen : KunneIkkeLeggeTilFradragsgrunnlag()
        object GrunnlagetMåVæreInneforBehandlingsperioden : KunneIkkeLeggeTilFradragsgrunnlag()
        object PeriodeMangler : KunneIkkeLeggeTilFradragsgrunnlag()
        data class KunneIkkeEndreFradragsgrunnlag(val feil: KunneIkkeLageGrunnlagsdata) :
            KunneIkkeLeggeTilFradragsgrunnlag()
    }

    internal fun validerFradragsgrunnlag(fradragsgrunnlag: List<Grunnlag.Fradragsgrunnlag>): Either<KunneIkkeLeggeTilFradragsgrunnlag, Unit> {
        if (fradragsgrunnlag.isNotEmpty()) {
            if (fradragsgrunnlag.periode() != null) {
                if (!(periode inneholder fradragsgrunnlag.periode()!!)) {
                    return KunneIkkeLeggeTilFradragsgrunnlag.GrunnlagetMåVæreInneforBehandlingsperioden.left()
                }
            } else {
                return KunneIkkeLeggeTilFradragsgrunnlag.PeriodeMangler.left()
            }
        }
        return Unit.right()
    }

    sealed class KunneIkkeOppdatereBosituasjon {
        data class KlarteIkkeHentePersonIPdl(val feil: KunneIkkeHentePerson) : KunneIkkeOppdatereBosituasjon()
        data class UgyldigTilstand(val fra: KClass<out Søknadsbehandling>, val til: KClass<out Vilkårsvurdert>) :
            KunneIkkeOppdatereBosituasjon()

        data class KunneIkkeLageGrunnlagsdata(val feil: no.nav.su.se.bakover.domain.grunnlag.KunneIkkeLageGrunnlagsdata) :
            KunneIkkeOppdatereBosituasjon()
    }

    open fun leggTilFradragsgrunnlag(fradragsgrunnlag: List<Grunnlag.Fradragsgrunnlag>): Either<KunneIkkeLeggeTilFradragsgrunnlag, Vilkårsvurdert.Innvilget> =
        KunneIkkeLeggeTilFradragsgrunnlag.IkkeLovÅLeggeTilFradragIDenneStatusen.left()

    open fun oppdaterBosituasjon(
        bosituasjon: Grunnlag.Bosituasjon,
        clock: Clock,
        hentPerson: (fnr: Fnr) -> Either<KunneIkkeHentePerson, Person>,
    ): Either<KunneIkkeOppdatereBosituasjon, Vilkårsvurdert> {
        return KunneIkkeOppdatereBosituasjon.UgyldigTilstand(this::class, Vilkårsvurdert::class).left()
    }

    sealed class Vilkårsvurdert : Søknadsbehandling() {

        override fun oppdaterBosituasjon(
            bosituasjon: Grunnlag.Bosituasjon,
            clock: Clock,
            hentPerson: (fnr: Fnr) -> Either<KunneIkkeHentePerson, Person>,
        ): Either<KunneIkkeOppdatereBosituasjon, Vilkårsvurdert> {
            val oppdatertGrunnlagsdata = Grunnlagsdata.tryCreate(
                // TODO: fradragsgrunnlaget kan inneholde innteker for EPS - de bør fjernes dersom vi ikke har EPS
                fradragsgrunnlag = this.grunnlagsdata.fradragsgrunnlag, bosituasjon = listOf(bosituasjon),
            ).getOrHandle {
                return KunneIkkeOppdatereBosituasjon.KunneIkkeLageGrunnlagsdata(it).left()
            }
            // TODO: finn ut hvordan vi kan oppdatere bare det vi vil spesifikt
            val oppdatertBehandlingsinformasjon =
                this.behandlingsinformasjon.oppdaterBosituasjonOgEktefelleOgNullstillFormueForEpsHvisIngenEps(
                    bosituasjon = bosituasjon,
                ).getOrHandle { return KunneIkkeOppdatereBosituasjon.KlarteIkkeHentePersonIPdl(it).left() }
            return when (this) {
                is Avslag -> this.copy(
                    grunnlagsdata = oppdatertGrunnlagsdata,
                    behandlingsinformasjon = oppdatertBehandlingsinformasjon,
                )
                is Innvilget -> this.copy(
                    grunnlagsdata = oppdatertGrunnlagsdata,
                    behandlingsinformasjon = oppdatertBehandlingsinformasjon,
                )

                is Uavklart -> this.copy(
                    grunnlagsdata = oppdatertGrunnlagsdata,
                    behandlingsinformasjon = oppdatertBehandlingsinformasjon,
                )
            }.right()
        }

        fun tilVilkårsvurdert(behandlingsinformasjon: Behandlingsinformasjon, clock: Clock): Vilkårsvurdert =
            opprett(
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
                grunnlagsdata,
                vilkårsvurderinger,
                attesteringer,
                clock,
            )

        fun tilBeregnet(beregning: Beregning): Beregnet =
            Beregnet.opprett(
                id,
                opprettet,
                sakId,
                saksnummer,
                søknad,
                oppgaveId,
                behandlingsinformasjon,
                fnr,
                beregning,
                fritekstTilBrev,
                stønadsperiode!!,
                grunnlagsdata,
                vilkårsvurderinger,
                attesteringer,
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
                stønadsperiode: Stønadsperiode?,
                grunnlagsdata: Grunnlagsdata,
                vilkårsvurderinger: Vilkårsvurderinger.Søknadsbehandling,
                attesteringer: Attesteringshistorikk,
                clock: Clock,
            ): Vilkårsvurdert {
                val oppdaterteVilkårsvurderinger = vilkårsvurderinger.oppdater(
                    stønadsperiode = stønadsperiode!!,
                    behandlingsinformasjon = behandlingsinformasjon,
                    grunnlagsdata = grunnlagsdata,
                    clock = clock,
                )
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
        ) : Vilkårsvurdert() {

            override val status: BehandlingsStatus = BehandlingsStatus.VILKÅRSVURDERT_INNVILGET

            override val periode: Periode = stønadsperiode.periode

            override fun accept(visitor: SøknadsbehandlingVisitor) {
                visitor.visit(this)
            }

            override fun leggTilFradragsgrunnlag(fradragsgrunnlag: List<Grunnlag.Fradragsgrunnlag>): Either<KunneIkkeLeggeTilFradragsgrunnlag, Innvilget> {
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
                    grunnlagsdata = Grunnlagsdata.tryCreate(
                        fradragsgrunnlag = fradragsgrunnlag,
                        bosituasjon = this.grunnlagsdata.bosituasjon,
                    ).getOrHandle {
                        return KunneIkkeLeggeTilFradragsgrunnlag.KunneIkkeEndreFradragsgrunnlag(it).left()
                    },
                    vilkårsvurderinger,
                    attesteringer,
                ).right()
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
        ) : Vilkårsvurdert(), ErAvslag {

            override val status: BehandlingsStatus = BehandlingsStatus.VILKÅRSVURDERT_AVSLAG
            override val periode: Periode = stønadsperiode.periode

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
                )

            // TODO fiks typing/gyldig tilstand/vilkår fradrag?
            override val avslagsgrunner: List<Avslagsgrunn> = when (val vilkår = vilkårsvurderinger.resultat) {
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
            override val behandlingsinformasjon: Behandlingsinformasjon,
            override val fnr: Fnr,
            override val fritekstTilBrev: String,
            override val stønadsperiode: Stønadsperiode?,
            override val grunnlagsdata: Grunnlagsdata,
            override val vilkårsvurderinger: Vilkårsvurderinger.Søknadsbehandling,
            override val attesteringer: Attesteringshistorikk,
        ) : Vilkårsvurdert() {

            override val status: BehandlingsStatus = BehandlingsStatus.OPPRETTET
            override val periode: Periode
                get() = stønadsperiode?.periode ?: throw StønadsperiodeIkkeDefinertException("Periode er ikke satt")

            override fun accept(visitor: SøknadsbehandlingVisitor) {
                visitor.visit(this)
            }
        }

        data class StønadsperiodeIkkeDefinertException(
            val msg: String = "Sønadsperiode er ikke definert",
        ) : RuntimeException(msg)
    }

    sealed class Beregnet : Søknadsbehandling() {
        abstract override val behandlingsinformasjon: Behandlingsinformasjon
        abstract val beregning: Beregning
        abstract override val stønadsperiode: Stønadsperiode

        override fun oppdaterBosituasjon(
            bosituasjon: Grunnlag.Bosituasjon,
            clock: Clock,
            hentPerson: (fnr: Fnr) -> Either<KunneIkkeHentePerson, Person>,
        ): Either<KunneIkkeOppdatereBosituasjon, Vilkårsvurdert> {
            val oppdatertGrunnlagsdata = Grunnlagsdata.tryCreate(
                // TODO: fradragsgrunnlaget kan inneholde innteker for EPS - de bør fjernes dersom vi ikke har EPS
                fradragsgrunnlag = this.grunnlagsdata.fradragsgrunnlag, bosituasjon = listOf(bosituasjon),
            ).getOrHandle {
                return KunneIkkeOppdatereBosituasjon.KunneIkkeLageGrunnlagsdata(it).left()
            }
            val oppdatertBehandlingsinformasjon =
                this.behandlingsinformasjon.oppdaterBosituasjonOgEktefelleOgNullstillFormueForEpsHvisIngenEps(
                    bosituasjon = bosituasjon,
                ).getOrHandle { return KunneIkkeOppdatereBosituasjon.KlarteIkkeHentePersonIPdl(it).left() }

            return when (this) {
                is Avslag -> this.tilVilkårsvurdert(
                    behandlingsinformasjon = oppdatertBehandlingsinformasjon,
                    grunnlagsdata = oppdatertGrunnlagsdata,
                    clock = clock,
                )
                is Innvilget -> this.tilVilkårsvurdert(
                    behandlingsinformasjon = oppdatertBehandlingsinformasjon,
                    grunnlagsdata = oppdatertGrunnlagsdata,
                    clock = clock,
                )
            }.right()
        }

        fun tilVilkårsvurdert(
            behandlingsinformasjon: Behandlingsinformasjon,
            grunnlagsdata: Grunnlagsdata = this.grunnlagsdata,
            clock: Clock,
        ): Vilkårsvurdert =
            Vilkårsvurdert.opprett(
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
                grunnlagsdata,
                vilkårsvurderinger,
                attesteringer,
                clock,
            )

        fun tilBeregnet(beregning: Beregning): Beregnet =
            opprett(
                id,
                opprettet,
                sakId,
                saksnummer,
                søknad,
                oppgaveId,
                behandlingsinformasjon,
                fnr,
                beregning,
                fritekstTilBrev,
                stønadsperiode,
                grunnlagsdata,
                vilkårsvurderinger,
                attesteringer,
            )

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
                beregning: Beregning,
                fritekstTilBrev: String,
                stønadsperiode: Stønadsperiode,
                grunnlagsdata: Grunnlagsdata,
                vilkårsvurderinger: Vilkårsvurderinger.Søknadsbehandling,
                attesteringer: Attesteringshistorikk,
            ): Beregnet =
                when (VurderAvslagGrunnetBeregning.vurderAvslagGrunnetBeregning(beregning)) {
                    is AvslagGrunnetBeregning.Ja -> Avslag(
                        id,
                        opprettet,
                        sakId,
                        saksnummer,
                        søknad,
                        oppgaveId,
                        behandlingsinformasjon,
                        fnr,
                        beregning,
                        fritekstTilBrev,
                        stønadsperiode,
                        grunnlagsdata,
                        vilkårsvurderinger,
                        attesteringer,
                    )
                    AvslagGrunnetBeregning.Nei -> Innvilget(
                        id,
                        opprettet,
                        sakId,
                        saksnummer,
                        søknad,
                        oppgaveId,
                        behandlingsinformasjon,
                        fnr,
                        beregning,
                        fritekstTilBrev,
                        stønadsperiode,
                        grunnlagsdata,
                        vilkårsvurderinger,
                        attesteringer,
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
            override val behandlingsinformasjon: Behandlingsinformasjon,
            override val fnr: Fnr,
            override val beregning: Beregning,
            override val fritekstTilBrev: String,
            override val stønadsperiode: Stønadsperiode,
            override val grunnlagsdata: Grunnlagsdata,
            override val vilkårsvurderinger: Vilkårsvurderinger.Søknadsbehandling,
            override val attesteringer: Attesteringshistorikk,
        ) : Beregnet() {
            override val status: BehandlingsStatus = BehandlingsStatus.BEREGNET_INNVILGET
            override val periode: Periode = stønadsperiode.periode

            override fun accept(visitor: SøknadsbehandlingVisitor) {
                visitor.visit(this)
            }

            override fun leggTilFradragsgrunnlag(fradragsgrunnlag: List<Grunnlag.Fradragsgrunnlag>): Either<KunneIkkeLeggeTilFradragsgrunnlag, Vilkårsvurdert.Innvilget> {
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
                ).right()
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
        ) : Beregnet(), ErAvslag {
            override val status: BehandlingsStatus = BehandlingsStatus.BEREGNET_AVSLAG
            override val periode: Periode = stønadsperiode.periode

            private val avslagsgrunnForBeregning: List<Avslagsgrunn> =
                when (val vurdering = VurderAvslagGrunnetBeregning.vurderAvslagGrunnetBeregning(beregning)) {
                    is AvslagGrunnetBeregning.Ja -> listOf(vurdering.grunn.toAvslagsgrunn())
                    is AvslagGrunnetBeregning.Nei -> emptyList()
                }

            override fun leggTilFradragsgrunnlag(fradragsgrunnlag: List<Grunnlag.Fradragsgrunnlag>): Either<KunneIkkeLeggeTilFradragsgrunnlag, Vilkårsvurdert.Innvilget> {
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
                )

            // TODO fiks typing/gyldig tilstand/vilkår fradrag?
            override val avslagsgrunner: List<Avslagsgrunn> = when (val vilkår = vilkårsvurderinger.resultat) {
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
        override val behandlingsinformasjon: Behandlingsinformasjon,
        override val fnr: Fnr,
        val beregning: Beregning,
        val simulering: Simulering,
        override val fritekstTilBrev: String,
        override val stønadsperiode: Stønadsperiode,
        override val grunnlagsdata: Grunnlagsdata,
        override val vilkårsvurderinger: Vilkårsvurderinger.Søknadsbehandling,
        override val attesteringer: Attesteringshistorikk,
    ) : Søknadsbehandling() {
        override val status: BehandlingsStatus = BehandlingsStatus.SIMULERT
        override val periode: Periode = stønadsperiode.periode

        override fun accept(visitor: SøknadsbehandlingVisitor) {
            visitor.visit(this)
        }

        override fun oppdaterBosituasjon(
            bosituasjon: Grunnlag.Bosituasjon,
            clock: Clock,
            hentPerson: (fnr: Fnr) -> Either<KunneIkkeHentePerson, Person>,
        ): Either<KunneIkkeOppdatereBosituasjon, Vilkårsvurdert> {
            val oppdatertGrunnlagsdata = Grunnlagsdata.tryCreate(
                // TODO: fradragsgrunnlaget kan inneholde innteker for EPS - de bør fjernes dersom vi ikke har EPS
                fradragsgrunnlag = this.grunnlagsdata.fradragsgrunnlag, bosituasjon = listOf(bosituasjon),
            ).getOrHandle {
                return KunneIkkeOppdatereBosituasjon.KunneIkkeLageGrunnlagsdata(it).left()
            }
            val oppdatertBehandlingsinformasjon =
                this.behandlingsinformasjon.oppdaterBosituasjonOgEktefelleOgNullstillFormueForEpsHvisIngenEps(
                    bosituasjon = bosituasjon,
                ).getOrHandle { return KunneIkkeOppdatereBosituasjon.KlarteIkkeHentePersonIPdl(it).left() }

            return this.tilVilkårsvurdert(
                behandlingsinformasjon = oppdatertBehandlingsinformasjon,
                grunnlagsdata = oppdatertGrunnlagsdata,
                clock = clock,
            ).right()
        }

        override fun leggTilFradragsgrunnlag(fradragsgrunnlag: List<Grunnlag.Fradragsgrunnlag>): Either<KunneIkkeLeggeTilFradragsgrunnlag, Vilkårsvurdert.Innvilget> {
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
            ).right()
        }

        fun tilVilkårsvurdert(
            behandlingsinformasjon: Behandlingsinformasjon,
            grunnlagsdata: Grunnlagsdata = this.grunnlagsdata,
            clock: Clock,
        ): Vilkårsvurdert =
            Vilkårsvurdert.opprett(
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
                grunnlagsdata,
                vilkårsvurderinger,
                attesteringer,
                clock,
            )

        fun tilBeregnet(beregning: Beregning): Beregnet =
            Beregnet.opprett(
                id,
                opprettet,
                sakId,
                saksnummer,
                søknad,
                oppgaveId,
                behandlingsinformasjon,
                fnr,
                beregning,
                fritekstTilBrev,
                stønadsperiode,
                grunnlagsdata,
                vilkårsvurderinger,
                attesteringer,
            )

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
            )

        fun tilAttestering(
            saksbehandler: NavIdentBruker.Saksbehandler,
            fritekstTilBrev: String,
        ): TilAttestering.Innvilget =
            TilAttestering.Innvilget(
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
            )
    }

    sealed class TilAttestering : Søknadsbehandling() {
        abstract val saksbehandler: NavIdentBruker
        abstract fun nyOppgaveId(nyOppgaveId: OppgaveId): TilAttestering
        abstract fun tilUnderkjent(attestering: Attestering): Underkjent
        abstract override val stønadsperiode: Stønadsperiode
        abstract override val attesteringer: Attesteringshistorikk

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
        ) : TilAttestering() {
            override val status: BehandlingsStatus = BehandlingsStatus.TIL_ATTESTERING_INNVILGET
            override val periode: Periode = stønadsperiode.periode

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
            ) : Avslag() {

                // TODO fiks typing/gyldig tilstand/vilkår fradrag?
                override val avslagsgrunner: List<Avslagsgrunn> = when (val vilkår = vilkårsvurderinger.resultat) {
                    is Vilkårsvurderingsresultat.Avslag -> vilkår.avslagsgrunner
                    is Vilkårsvurderingsresultat.Innvilget -> emptyList()
                    is Vilkårsvurderingsresultat.Uavklart -> emptyList()
                }

                override val periode: Periode = stønadsperiode.periode

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

        abstract fun nyOppgaveId(nyOppgaveId: OppgaveId): Underkjent

        override fun oppdaterBosituasjon(
            bosituasjon: Grunnlag.Bosituasjon,
            clock: Clock,
            hentPerson: (fnr: Fnr) -> Either<KunneIkkeHentePerson, Person>,
        ): Either<KunneIkkeOppdatereBosituasjon, Vilkårsvurdert> {
            val oppdatertGrunnlagsdata = Grunnlagsdata.tryCreate(
                // TODO: fradragsgrunnlaget kan inneholde innteker for EPS - de bør fjernes dersom vi ikke har EPS
                fradragsgrunnlag = this.grunnlagsdata.fradragsgrunnlag, bosituasjon = listOf(bosituasjon),
            ).getOrHandle {
                return KunneIkkeOppdatereBosituasjon.KunneIkkeLageGrunnlagsdata(it).left()
            }
            val oppdatertBehandlingsinformasjon =
                this.behandlingsinformasjon.oppdaterBosituasjonOgEktefelleOgNullstillFormueForEpsHvisIngenEps(
                    bosituasjon = bosituasjon,
                ).getOrHandle { return KunneIkkeOppdatereBosituasjon.KlarteIkkeHentePersonIPdl(it).left() }

            return this.tilVilkårsvurdert(
                behandlingsinformasjon = oppdatertBehandlingsinformasjon,
                grunnlagsdata = oppdatertGrunnlagsdata,
                clock = clock,
            ).right()
        }

        fun tilVilkårsvurdert(
            behandlingsinformasjon: Behandlingsinformasjon,
            grunnlagsdata: Grunnlagsdata = this.grunnlagsdata,
            clock: Clock,
        ): Vilkårsvurdert =
            Vilkårsvurdert.opprett(
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
                grunnlagsdata,
                vilkårsvurderinger,
                attesteringer,
                clock,
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
        ) : Underkjent() {

            override fun nyOppgaveId(nyOppgaveId: OppgaveId): Innvilget {
                return this.copy(oppgaveId = nyOppgaveId)
            }

            override fun leggTilFradragsgrunnlag(fradragsgrunnlag: List<Grunnlag.Fradragsgrunnlag>): Either<KunneIkkeLeggeTilFradragsgrunnlag, Vilkårsvurdert.Innvilget> {
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
                ).right()
            }

            override val status: BehandlingsStatus = BehandlingsStatus.UNDERKJENT_INNVILGET
            override val periode: Periode = stønadsperiode.periode

            override fun accept(visitor: SøknadsbehandlingVisitor) {
                visitor.visit(this)
            }

            fun tilBeregnet(beregning: Beregning): Beregnet =
                Beregnet.opprett(
                    id,
                    opprettet,
                    sakId,
                    saksnummer,
                    søknad,
                    oppgaveId,
                    behandlingsinformasjon,
                    fnr,
                    beregning,
                    fritekstTilBrev,
                    stønadsperiode,
                    grunnlagsdata,
                    vilkårsvurderinger,
                    attesteringer,
                )

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
                )

            fun tilAttestering(saksbehandler: NavIdentBruker.Saksbehandler): TilAttestering.Innvilget =
                TilAttestering.Innvilget(
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
                )
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
            ) : Avslag() {
                override val status: BehandlingsStatus = BehandlingsStatus.UNDERKJENT_AVSLAG
                override val periode: Periode = stønadsperiode.periode

                private val avslagsgrunnForBeregning: List<Avslagsgrunn> =
                    when (val vurdering = VurderAvslagGrunnetBeregning.vurderAvslagGrunnetBeregning(beregning)) {
                        is AvslagGrunnetBeregning.Ja -> listOf(vurdering.grunn.toAvslagsgrunn())
                        is AvslagGrunnetBeregning.Nei -> emptyList()
                    }

                override fun leggTilFradragsgrunnlag(fradragsgrunnlag: List<Grunnlag.Fradragsgrunnlag>): Either<KunneIkkeLeggeTilFradragsgrunnlag, Vilkårsvurdert.Innvilget> {
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
                    ).right()
                }

                override fun nyOppgaveId(nyOppgaveId: OppgaveId): MedBeregning {
                    return this.copy(oppgaveId = nyOppgaveId)
                }

                override fun accept(visitor: SøknadsbehandlingVisitor) {
                    visitor.visit(this)
                }

                fun tilBeregnet(beregning: Beregning): Beregnet =
                    Beregnet.opprett(
                        id,
                        opprettet,
                        sakId,
                        saksnummer,
                        søknad,
                        oppgaveId,
                        behandlingsinformasjon,
                        fnr,
                        beregning,
                        fritekstTilBrev,
                        stønadsperiode,
                        grunnlagsdata,
                        vilkårsvurderinger,
                        attesteringer,
                    )

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
                    )

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
            ) : Avslag() {
                override val status: BehandlingsStatus = BehandlingsStatus.UNDERKJENT_AVSLAG
                override val periode: Periode = stønadsperiode.periode

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
                    )

                // TODO fiks typing/gyldig tilstand/vilkår fradrag?
                override val avslagsgrunner: List<Avslagsgrunn> = when (val vilkår = vilkårsvurderinger.resultat) {
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
        abstract override val behandlingsinformasjon: Behandlingsinformasjon
        abstract override val fnr: Fnr
        abstract val saksbehandler: NavIdentBruker.Saksbehandler
        abstract override val attesteringer: Attesteringshistorikk
        abstract override val stønadsperiode: Stønadsperiode

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
        ) : Iverksatt() {
            override val status: BehandlingsStatus = BehandlingsStatus.IVERKSATT_INNVILGET
            override val periode: Periode = stønadsperiode.periode

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
            ) : Avslag() {
                override val status: BehandlingsStatus = BehandlingsStatus.IVERKSATT_AVSLAG
                override val periode: Periode = stønadsperiode.periode

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
            ) : Avslag() {
                override val status: BehandlingsStatus = BehandlingsStatus.IVERKSATT_AVSLAG
                override val periode: Periode = stønadsperiode.periode

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
    IVERKSATT_AVSLAG,
}

sealed class KunneIkkeIverksette {
    object AttestantOgSaksbehandlerKanIkkeVæreSammePerson : KunneIkkeIverksette()
    data class KunneIkkeUtbetale(val utbetalingFeilet: UtbetalingFeilet) : KunneIkkeIverksette()
    object FantIkkeBehandling : KunneIkkeIverksette()
    object FantIkkePerson : KunneIkkeIverksette()
    object FikkIkkeHentetSaksbehandlerEllerAttestant : KunneIkkeIverksette()
    object KunneIkkeGenerereVedtaksbrev : KunneIkkeIverksette()
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
