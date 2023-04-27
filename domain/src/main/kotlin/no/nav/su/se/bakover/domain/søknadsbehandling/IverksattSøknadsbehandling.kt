package no.nav.su.se.bakover.domain.søknadsbehandling

import no.nav.su.se.bakover.common.Fnr
import no.nav.su.se.bakover.common.NavIdentBruker
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.avkorting.AvkortingVedSøknadsbehandling
import no.nav.su.se.bakover.domain.behandling.Attesteringshistorikk
import no.nav.su.se.bakover.domain.behandling.AvslagGrunnetBeregning
import no.nav.su.se.bakover.domain.behandling.VurderAvslagGrunnetBeregning
import no.nav.su.se.bakover.domain.behandling.avslag.Avslagsgrunn
import no.nav.su.se.bakover.domain.behandling.avslag.Avslagsgrunn.Companion.toAvslagsgrunn
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.grunnlag.GrunnlagsdataOgVilkårsvurderinger
import no.nav.su.se.bakover.domain.grunnlag.krevAlleVilkårInnvilget
import no.nav.su.se.bakover.domain.grunnlag.krevMinstEttAvslag
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.sak.Saksnummer
import no.nav.su.se.bakover.domain.sak.Sakstype
import no.nav.su.se.bakover.domain.søknad.Søknad
import no.nav.su.se.bakover.domain.søknadsbehandling.avslag.ErAvslag
import no.nav.su.se.bakover.domain.søknadsbehandling.stønadsperiode.Aldersvurdering
import no.nav.su.se.bakover.domain.søknadsbehandling.stønadsperiode.Stønadsperiode
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderingsresultat
import java.util.UUID

sealed class IverksattSøknadsbehandling : Søknadsbehandling() {
    abstract override val id: UUID
    abstract override val opprettet: Tidspunkt
    abstract override val sakId: UUID
    abstract override val saksnummer: Saksnummer
    abstract override val søknad: Søknad.Journalført.MedOppgave
    abstract override val oppgaveId: OppgaveId
    abstract override val fnr: Fnr
    abstract override val saksbehandler: NavIdentBruker.Saksbehandler
    abstract override val attesteringer: Attesteringshistorikk
    abstract override val aldersvurdering: Aldersvurdering
    abstract override val avkorting: AvkortingVedSøknadsbehandling.Iverksatt

    override fun copyInternal(
        stønadsperiode: Stønadsperiode,
        grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderinger.Søknadsbehandling,
        avkorting: AvkortingVedSøknadsbehandling,
        søknadsbehandlingshistorikk: Søknadsbehandlingshistorikk,
        aldersvurdering: Aldersvurdering,
    ) = throw UnsupportedOperationException("Kan ikke kalle copyInternal på en iverksatt søknadsbehandling.")

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
        override val søknadsbehandlingsHistorikk: Søknadsbehandlingshistorikk,
        override val fritekstTilBrev: String,
        override val aldersvurdering: Aldersvurdering,
        override val grunnlagsdata: Grunnlagsdata,
        override val vilkårsvurderinger: Vilkårsvurderinger.Søknadsbehandling,
        override val avkorting: AvkortingVedSøknadsbehandling.Iverksatt,
        override val sakstype: Sakstype,
    ) : IverksattSøknadsbehandling() {
        override val stønadsperiode: Stønadsperiode = aldersvurdering.stønadsperiode
        override val periode: Periode = aldersvurdering.stønadsperiode.periode

        init {
            grunnlagsdataOgVilkårsvurderinger.krevAlleVilkårInnvilget()
            kastHvisGrunnlagsdataOgVilkårsvurderingerPeriodenOgBehandlingensPerioderErUlike()
            grunnlagsdata.kastHvisIkkeAlleBosituasjonerErFullstendig()
        }

        override fun accept(visitor: SøknadsbehandlingVisitor) {
            visitor.visit(this)
        }

        override fun skalSendeVedtaksbrev(): Boolean {
            return true
        }
    }

    sealed class Avslag : IverksattSøknadsbehandling(), ErAvslag {
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
            override val søknadsbehandlingsHistorikk: Søknadsbehandlingshistorikk,
            override val fritekstTilBrev: String,
            override val aldersvurdering: Aldersvurdering,
            override val grunnlagsdata: Grunnlagsdata,
            override val vilkårsvurderinger: Vilkårsvurderinger.Søknadsbehandling,
            override val avkorting: AvkortingVedSøknadsbehandling.Iverksatt.KanIkkeHåndtere,
            override val sakstype: Sakstype,
        ) : Avslag() {
            override val periode: Periode = aldersvurdering.stønadsperiode.periode
            override val stønadsperiode: Stønadsperiode = aldersvurdering.stønadsperiode
            override val simulering: Simulering? = null

            init {
                grunnlagsdataOgVilkårsvurderinger.krevAlleVilkårInnvilget()
                kastHvisGrunnlagsdataOgVilkårsvurderingerPeriodenOgBehandlingensPerioderErUlike()
                grunnlagsdata.kastHvisIkkeAlleBosituasjonerErFullstendig()
            }

            override fun skalSendeVedtaksbrev(): Boolean {
                return true
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
            override val søknadsbehandlingsHistorikk: Søknadsbehandlingshistorikk,
            override val fritekstTilBrev: String,
            override val aldersvurdering: Aldersvurdering,
            override val grunnlagsdata: Grunnlagsdata,
            override val vilkårsvurderinger: Vilkårsvurderinger.Søknadsbehandling,
            override val avkorting: AvkortingVedSøknadsbehandling.Iverksatt.KanIkkeHåndtere,
            override val sakstype: Sakstype,
        ) : Avslag() {
            override val periode: Periode = aldersvurdering.stønadsperiode.periode
            override val stønadsperiode: Stønadsperiode = aldersvurdering.stønadsperiode
            override val beregning = null
            override val simulering: Simulering? = null

            init {
                grunnlagsdataOgVilkårsvurderinger.krevMinstEttAvslag()
                kastHvisGrunnlagsdataOgVilkårsvurderingerPeriodenOgBehandlingensPerioderErUlike()
            }

            override fun skalSendeVedtaksbrev(): Boolean {
                return true
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
