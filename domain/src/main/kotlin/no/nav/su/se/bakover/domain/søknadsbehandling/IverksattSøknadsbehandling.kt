package no.nav.su.se.bakover.domain.søknadsbehandling

import arrow.core.NonEmptyList
import arrow.core.getOrElse
import arrow.core.left
import behandling.søknadsbehandling.domain.GrunnlagsdataOgVilkårsvurderingerSøknadsbehandling
import beregning.domain.Beregning
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.Stønadsperiode
import no.nav.su.se.bakover.common.domain.attestering.Attesteringshistorikk
import no.nav.su.se.bakover.common.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.extensions.toNonEmptyList
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.domain.behandling.AvslagGrunnetBeregning
import no.nav.su.se.bakover.domain.behandling.VurderAvslagGrunnetBeregning
import no.nav.su.se.bakover.domain.søknad.Søknad
import no.nav.su.se.bakover.domain.søknadsbehandling.avslag.ErAvslag
import no.nav.su.se.bakover.domain.søknadsbehandling.stønadsperiode.Aldersvurdering
import no.nav.su.se.bakover.domain.vilkår.uføreVilkår
import vilkår.common.domain.Avslagsgrunn
import vilkår.uføre.domain.Uføregrunnlag
import vilkår.vurderinger.domain.EksterneGrunnlagSkatt
import vilkår.vurderinger.domain.krevAlleVilkårInnvilget
import vilkår.vurderinger.domain.krevMinstEttAvslag
import økonomi.domain.simulering.Simulering
import java.util.UUID

sealed interface IverksattSøknadsbehandling : Søknadsbehandling, KanGenerereBrev {
    abstract override val id: SøknadsbehandlingId
    abstract override val opprettet: Tidspunkt
    abstract override val sakId: UUID
    abstract override val saksnummer: Saksnummer
    abstract override val søknad: Søknad.Journalført.MedOppgave
    abstract override val oppgaveId: OppgaveId
    abstract override val fnr: Fnr
    abstract override val saksbehandler: NavIdentBruker.Saksbehandler
    abstract override val attesteringer: Attesteringshistorikk
    abstract override val aldersvurdering: Aldersvurdering

    override fun leggTilSkatt(skatt: EksterneGrunnlagSkatt) = KunneIkkeLeggeTilSkattegrunnlag.UgyldigTilstand.left()
    override fun oppdaterOppgaveId(oppgaveId: OppgaveId): Søknadsbehandling = throw IllegalStateException("Skal ikke kunne oppdatere oppgave for en iverksatt søknadsbehandling $id")

    data class Innvilget(
        override val id: SøknadsbehandlingId,
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
        override val grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderingerSøknadsbehandling,
        override val sakstype: Sakstype,
    ) : IverksattSøknadsbehandling, KanGenerereInnvilgelsesbrev {
        override val stønadsperiode: Stønadsperiode = aldersvurdering.stønadsperiode

        override val periode: Periode = aldersvurdering.stønadsperiode.periode

        init {
            grunnlagsdataOgVilkårsvurderinger.krevAlleVilkårInnvilget()
            kastHvisGrunnlagsdataOgVilkårsvurderingerPeriodenOgBehandlingensPerioderErUlike()
            grunnlagsdata.kastHvisIkkeAlleBosituasjonerErFullstendig()
        }

        override fun skalSendeVedtaksbrev(): Boolean {
            return true
        }

        /**
         * @return null dersom man kaller denne for en alderssak.
         * @throws IllegalStateException Dersom søknadsbehandlingen mangler uføregrunnlag. Dette skal ikke skje. Initen skal også verifisere dette.
         *
         * Se også tilsvarende implementasjon for revurdering: [no.nav.su.se.bakover.domain.revurdering.IverksattRevurdering.Innvilget.hentUføregrunnlag]
         */
        fun hentUføregrunnlag(): NonEmptyList<Uføregrunnlag>? {
            return when (this.sakstype) {
                Sakstype.ALDER -> null

                Sakstype.UFØRE -> {
                    this.vilkårsvurderinger.uføreVilkår()
                        .getOrElse { throw IllegalStateException("Søknadsbehandling uføre: ${this.id} mangler uføregrunnlag") }
                        .grunnlag
                        .toNonEmptyList()
                }
            }
        }
    }

    sealed interface Avslag : IverksattSøknadsbehandling, ErAvslag, KanGenerereAvslagsbrev {
        data class MedBeregning(
            override val id: SøknadsbehandlingId,
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
            override val grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderingerSøknadsbehandling,
            override val sakstype: Sakstype,
        ) : Avslag {
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

            private val avslagsgrunnForBeregning: List<Avslagsgrunn> =
                when (val vurdering = VurderAvslagGrunnetBeregning.vurderAvslagGrunnetBeregning(beregning)) {
                    is AvslagGrunnetBeregning.Ja -> listOf(vurdering.grunn.toAvslagsgrunn())
                    is AvslagGrunnetBeregning.Nei -> emptyList()
                }

            // TODO fiks typing/gyldig tilstand/vilkår fradrag?
            override val avslagsgrunner: List<Avslagsgrunn> = vilkårsvurderinger.avslagsgrunner + avslagsgrunnForBeregning
        }

        data class UtenBeregning(
            override val id: SøknadsbehandlingId,
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
            override val grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderingerSøknadsbehandling,
            override val sakstype: Sakstype,
        ) : Avslag {
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

            // TODO fiks typing/gyldig tilstand/vilkår fradrag?
            override val avslagsgrunner: List<Avslagsgrunn> = vilkårsvurderinger.avslagsgrunner
        }
    }
}
