package no.nav.su.se.bakover.domain.søknadsbehandling

import arrow.core.Either
import arrow.core.NonEmptyList
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.extensions.toNonEmptyList
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.domain.avkorting.AvkortingVedSøknadsbehandling
import no.nav.su.se.bakover.domain.avkorting.AvkortingVedSøknadsbehandling.IngenAvkorting
import no.nav.su.se.bakover.domain.behandling.Attesteringshistorikk
import no.nav.su.se.bakover.domain.behandling.AvslagGrunnetBeregning
import no.nav.su.se.bakover.domain.behandling.VurderAvslagGrunnetBeregning
import no.nav.su.se.bakover.domain.behandling.avslag.Avslagsgrunn
import no.nav.su.se.bakover.domain.behandling.avslag.Avslagsgrunn.Companion.toAvslagsgrunn
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.grunnlag.EksterneGrunnlag
import no.nav.su.se.bakover.domain.grunnlag.EksterneGrunnlagSkatt
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag.Bosituasjon.Companion.inneholderUfullstendigeBosituasjoner
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.grunnlag.GrunnlagsdataOgVilkårsvurderinger
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.sak.Saksnummer
import no.nav.su.se.bakover.domain.sak.Sakstype
import no.nav.su.se.bakover.domain.sak.SimulerUtbetalingFeilet
import no.nav.su.se.bakover.domain.søknad.Søknad
import no.nav.su.se.bakover.domain.søknadsbehandling.avslag.ErAvslag
import no.nav.su.se.bakover.domain.søknadsbehandling.stønadsperiode.Aldersvurdering
import no.nav.su.se.bakover.domain.søknadsbehandling.stønadsperiode.Stønadsperiode
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderingsresultat
import java.time.Clock
import java.util.UUID

sealed class BeregnetSøknadsbehandling : Søknadsbehandling(), Søknadsbehandling.KanOppdaterePeriodeGrunnlagVilkår {
    abstract override val beregning: Beregning
    abstract override val stønadsperiode: Stønadsperiode
    abstract override val aldersvurdering: Aldersvurdering
    abstract override val avkorting: AvkortingVedSøknadsbehandling.KlarTilIverksetting
    abstract override val saksbehandler: NavIdentBruker.Saksbehandler

    override fun simuler(
        saksbehandler: NavIdentBruker.Saksbehandler,
        clock: Clock,
        simuler: (beregning: Beregning, uføregrunnlag: NonEmptyList<Grunnlag.Uføregrunnlag>?) -> Either<SimulerUtbetalingFeilet, Simulering>,
    ): Either<KunneIkkeSimulereBehandling, SimulertSøknadsbehandling> {
        return simuler(
            beregning,
            when (sakstype) {
                Sakstype.ALDER -> {
                    null
                }

                Sakstype.UFØRE -> {
                    vilkårsvurderinger.uføreVilkår()
                        .getOrElse { throw IllegalStateException("Søknadsbehandling uføre: $id mangler uføregrunnlag") }.grunnlag.toNonEmptyList()
                }
            },
        ).mapLeft {
            KunneIkkeSimulereBehandling.KunneIkkeSimulere(it)
        }.map { simulering ->
            SimulertSøknadsbehandling(
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
                aldersvurdering = aldersvurdering,
                grunnlagsdata = grunnlagsdata,
                vilkårsvurderinger = vilkårsvurderinger,
                eksterneGrunnlag = eksterneGrunnlag,
                attesteringer = attesteringer,
                søknadsbehandlingsHistorikk = søknadsbehandlingsHistorikk.leggTilNyHendelse(
                    saksbehandlingsHendelse = Søknadsbehandlingshendelse(
                        tidspunkt = Tidspunkt.now(clock),
                        saksbehandler = saksbehandler,
                        handling = SøknadsbehandlingsHandling.Simulert,
                    ),
                ),
                avkorting = avkorting,
                sakstype = sakstype,
                saksbehandler = saksbehandler,
            )
        }
    }

    override fun leggTilSkatt(skatt: EksterneGrunnlagSkatt): Either<KunneIkkeLeggeTilSkattegrunnlag, Søknadsbehandling> =
        when (this.eksterneGrunnlag.skatt) {
            is EksterneGrunnlagSkatt.Hentet -> this.copyInternal(
                grunnlagsdataOgVilkårsvurderinger = this.grunnlagsdataOgVilkårsvurderinger.leggTilSkatt(skatt).let {
                    if (it !is GrunnlagsdataOgVilkårsvurderinger.Søknadsbehandling) throw IllegalStateException("Søknadsbehandling kan kun ha GrunnlagsdataOgVilkårsvurderinger av typen Søkndsbehandling") else it
                },
            ).right()

            EksterneGrunnlagSkatt.IkkeHentet -> KunneIkkeLeggeTilSkattegrunnlag.KanIkkeLeggeTilSkattForTilstandUtenAtDenHarBlittHentetFør.left()
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
        override val aldersvurdering: Aldersvurdering,
        override val grunnlagsdata: Grunnlagsdata,
        override val vilkårsvurderinger: Vilkårsvurderinger.Søknadsbehandling,
        override val eksterneGrunnlag: EksterneGrunnlag,
        override val attesteringer: Attesteringshistorikk,
        override val søknadsbehandlingsHistorikk: Søknadsbehandlingshistorikk,
        override val avkorting: AvkortingVedSøknadsbehandling.KlarTilIverksetting,
        override val sakstype: Sakstype,
        override val saksbehandler: NavIdentBruker.Saksbehandler,
    ) : BeregnetSøknadsbehandling() {
        override val periode: Periode = aldersvurdering.stønadsperiode.periode
        override val simulering: Simulering? = null

        override val stønadsperiode: Stønadsperiode = aldersvurdering.stønadsperiode

        override fun skalSendeVedtaksbrev(): Boolean {
            return true
        }

        init {
            kastHvisGrunnlagsdataOgVilkårsvurderingerPeriodenOgBehandlingensPerioderErUlike()
            grunnlagsdata.kastHvisIkkeAlleBosituasjonerErFullstendig()
        }

        override fun accept(visitor: SøknadsbehandlingVisitor) {
            visitor.visit(this)
        }

        override fun copyInternal(
            stønadsperiode: Stønadsperiode,
            grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderinger.Søknadsbehandling,
            søknadsbehandlingshistorikk: Søknadsbehandlingshistorikk,
            aldersvurdering: Aldersvurdering,
        ): Innvilget {
            return copy(
                aldersvurdering = aldersvurdering,
                grunnlagsdata = grunnlagsdataOgVilkårsvurderinger.grunnlagsdata,
                vilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger.vilkårsvurderinger,
                eksterneGrunnlag = grunnlagsdataOgVilkårsvurderinger.eksterneGrunnlag,
                søknadsbehandlingsHistorikk = søknadsbehandlingshistorikk,
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
        override val aldersvurdering: Aldersvurdering,
        override val grunnlagsdata: Grunnlagsdata,
        override val vilkårsvurderinger: Vilkårsvurderinger.Søknadsbehandling,
        override val eksterneGrunnlag: EksterneGrunnlag,
        override val attesteringer: Attesteringshistorikk,
        override val søknadsbehandlingsHistorikk: Søknadsbehandlingshistorikk,
        override val sakstype: Sakstype,
        override val saksbehandler: NavIdentBruker.Saksbehandler,
    ) : BeregnetSøknadsbehandling(), ErAvslag {

        override val periode: Periode = aldersvurdering.stønadsperiode.periode

        override val simulering: Simulering? = null

        override val stønadsperiode: Stønadsperiode = aldersvurdering.stønadsperiode

        /** Ingenting og avkorte ved avslag. */
        override val avkorting = IngenAvkorting

        private val avslagsgrunnForBeregning: List<Avslagsgrunn> =
            when (val vurdering = VurderAvslagGrunnetBeregning.vurderAvslagGrunnetBeregning(beregning)) {
                is AvslagGrunnetBeregning.Ja -> listOf(vurdering.grunn.toAvslagsgrunn())
                is AvslagGrunnetBeregning.Nei -> emptyList()
            }

        init {
            kastHvisGrunnlagsdataOgVilkårsvurderingerPeriodenOgBehandlingensPerioderErUlike()
            grunnlagsdata.kastHvisIkkeAlleBosituasjonerErFullstendig()
        }

        override fun skalSendeVedtaksbrev(): Boolean {
            return true
        }

        override fun copyInternal(
            stønadsperiode: Stønadsperiode,
            grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderinger.Søknadsbehandling,
            søknadsbehandlingshistorikk: Søknadsbehandlingshistorikk,
            aldersvurdering: Aldersvurdering,
        ): Avslag {
            return copy(
                aldersvurdering = aldersvurdering,
                grunnlagsdata = grunnlagsdataOgVilkårsvurderinger.grunnlagsdata,
                vilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger.vilkårsvurderinger,
                eksterneGrunnlag = grunnlagsdataOgVilkårsvurderinger.eksterneGrunnlag,
                søknadsbehandlingsHistorikk = søknadsbehandlingshistorikk,
            )
        }

        override fun accept(visitor: SøknadsbehandlingVisitor) {
            visitor.visit(this)
        }

        fun tilAttestering(
            saksbehandler: NavIdentBruker.Saksbehandler,
            fritekstTilBrev: String,
            clock: Clock,
        ): Either<ValideringsfeilAttestering, SøknadsbehandlingTilAttestering.Avslag.MedBeregning> {
            if (grunnlagsdata.bosituasjon.inneholderUfullstendigeBosituasjoner()) {
                return ValideringsfeilAttestering.InneholderUfullstendigBosituasjon.left()
            }
            return SøknadsbehandlingTilAttestering.Avslag.MedBeregning(
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
                aldersvurdering = aldersvurdering,
                grunnlagsdata = grunnlagsdata,
                vilkårsvurderinger = vilkårsvurderinger,
                eksterneGrunnlag = eksterneGrunnlag,
                attesteringer = attesteringer,
                søknadsbehandlingsHistorikk = søknadsbehandlingsHistorikk.leggTilNyHendelse(
                    saksbehandlingsHendelse = Søknadsbehandlingshendelse(
                        tidspunkt = Tidspunkt.now(clock),
                        saksbehandler = saksbehandler,
                        handling = SøknadsbehandlingsHandling.SendtTilAttestering,
                    ),
                ),
                sakstype = sakstype,
            ).right()
        }

        // TODO fiks typing/gyldig tilstand/vilkår fradrag?
        override val avslagsgrunner: List<Avslagsgrunn> = when (val vilkår = vilkårsvurderinger.vurdering) {
            is Vilkårsvurderingsresultat.Avslag -> vilkår.avslagsgrunner
            is Vilkårsvurderingsresultat.Innvilget -> emptyList()
            is Vilkårsvurderingsresultat.Uavklart -> emptyList()
        } + avslagsgrunnForBeregning
    }
}
