package no.nav.su.se.bakover.domain.søknadsbehandling

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import behandling.domain.AvslagGrunnetBeregning
import behandling.domain.VurderAvslagGrunnetBeregning
import behandling.søknadsbehandling.domain.GrunnlagsdataOgVilkårsvurderingerSøknadsbehandling
import behandling.søknadsbehandling.domain.avslag.ErAvslag
import beregning.domain.Beregning
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.Stønadsperiode
import no.nav.su.se.bakover.common.domain.attestering.Attestering
import no.nav.su.se.bakover.common.domain.attestering.Attesteringshistorikk
import no.nav.su.se.bakover.common.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.domain.revurdering.Omgjøringsgrunn
import no.nav.su.se.bakover.domain.revurdering.årsak.Revurderingsårsak
import no.nav.su.se.bakover.domain.søknad.Søknad
import no.nav.su.se.bakover.domain.søknadsbehandling.grunnlag.KunneIkkeLeggeTilSkattegrunnlag
import no.nav.su.se.bakover.domain.søknadsbehandling.stønadsperiode.Aldersvurdering
import no.nav.su.se.bakover.domain.søknadsbehandling.underkjenn.KunneIkkeUnderkjenneSøknadsbehandling
import vilkår.common.domain.Avslagsgrunn
import vilkår.vurderinger.domain.EksterneGrunnlagSkatt
import økonomi.domain.simulering.Simulering
import java.util.UUID

sealed interface SøknadsbehandlingTilAttestering :
    Søknadsbehandling,
    KanGenerereBrev {
    abstract override val saksbehandler: NavIdentBruker.Saksbehandler
    override fun erÅpen() = true
    override fun erAvsluttet() = false
    override fun erAvbrutt() = false
    fun nyOppgaveId(nyOppgaveId: OppgaveId): SøknadsbehandlingTilAttestering
    fun tilUnderkjent(
        attestering: Attestering.Underkjent,
    ): Either<KunneIkkeUnderkjenneSøknadsbehandling.AttestantOgSaksbehandlerKanIkkeVæreSammePerson, UnderkjentSøknadsbehandling>

    abstract override val aldersvurdering: Aldersvurdering
    abstract override val attesteringer: Attesteringshistorikk

    override fun leggTilSkatt(skatt: EksterneGrunnlagSkatt) = KunneIkkeLeggeTilSkattegrunnlag.UgyldigTilstand.left()
    override fun oppdaterOppgaveId(oppgaveId: OppgaveId): Søknadsbehandling = throw IllegalStateException("Skal ikke kunne oppdatere oppgave for en søknadsbehandling til attestering $id")

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
        override val fritekstTilBrev: String,
        override val aldersvurdering: Aldersvurdering,
        override val grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderingerSøknadsbehandling,
        override val attesteringer: Attesteringshistorikk,
        override val søknadsbehandlingsHistorikk: Søknadsbehandlingshistorikk,
        override val sakstype: Sakstype,
        override val omgjøringsårsak: Revurderingsårsak.Årsak?,
        override val omgjøringsgrunn: Omgjøringsgrunn?,
    ) : SøknadsbehandlingTilAttestering,
        KanGenerereInnvilgelsesbrev {

        override val stønadsperiode: Stønadsperiode = aldersvurdering.stønadsperiode

        override fun skalSendeVedtaksbrev(): Boolean {
            return true
        }

        override val periode: Periode = aldersvurdering.stønadsperiode.periode

        init {
            kastHvisGrunnlagsdataOgVilkårsvurderingerPeriodenOgBehandlingensPerioderErUlike()
            grunnlagsdata.kastHvisIkkeAlleBosituasjonerErFullstendig()
        }

        override fun nyOppgaveId(nyOppgaveId: OppgaveId): Innvilget {
            return this.copy(oppgaveId = nyOppgaveId)
        }

        override fun tilUnderkjent(
            attestering: Attestering.Underkjent,
        ): Either<KunneIkkeUnderkjenneSøknadsbehandling.AttestantOgSaksbehandlerKanIkkeVæreSammePerson, UnderkjentSøknadsbehandling.Innvilget> {
            if (attestering.attestant.navIdent == saksbehandler.navIdent) {
                return KunneIkkeUnderkjenneSøknadsbehandling.AttestantOgSaksbehandlerKanIkkeVæreSammePerson.left()
            }
            return UnderkjentSøknadsbehandling.Innvilget(
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
                søknadsbehandlingsHistorikk = søknadsbehandlingsHistorikk,
                fritekstTilBrev = fritekstTilBrev,
                aldersvurdering = aldersvurdering,
                grunnlagsdataOgVilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger,
                sakstype = sakstype,
                omgjøringsårsak = omgjøringsårsak,
                omgjøringsgrunn = omgjøringsgrunn,
            ).right()
        }

        fun tilIverksatt(attestering: Attestering, fritekst: String): IverksattSøknadsbehandling.Innvilget {
            return IverksattSøknadsbehandling.Innvilget(
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
                søknadsbehandlingsHistorikk = søknadsbehandlingsHistorikk,
                fritekstTilBrev = fritekst,
                aldersvurdering = aldersvurdering,
                grunnlagsdataOgVilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger,

                sakstype = sakstype,
                omgjøringsårsak = omgjøringsårsak,
                omgjøringsgrunn = omgjøringsgrunn,
            )
        }
    }

    sealed interface Avslag :
        SøknadsbehandlingTilAttestering,
        ErAvslag,
        KanGenerereAvslagsbrev {
        override val beregning: Beregning?
        abstract override val aldersvurdering: Aldersvurdering

        fun iverksett(attestering: Attestering.Iverksatt, fritekst: String): IverksattSøknadsbehandling.Avslag {
            return when (this) {
                is MedBeregning -> this.tilIverksatt(attestering, fritekstTilBrev)
                is UtenBeregning -> this.tilIverksatt(attestering, fritekstTilBrev)
            }
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
            override val fritekstTilBrev: String,
            override val aldersvurdering: Aldersvurdering,
            override val grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderingerSøknadsbehandling,
            override val attesteringer: Attesteringshistorikk,
            override val søknadsbehandlingsHistorikk: Søknadsbehandlingshistorikk,
            override val sakstype: Sakstype,
            override val omgjøringsårsak: Revurderingsårsak.Årsak?,
            override val omgjøringsgrunn: Omgjøringsgrunn?,
        ) : Avslag {
            override val beregning: Beregning? = null
            override val stønadsperiode: Stønadsperiode = aldersvurdering.stønadsperiode

            // TODO fiks typing/gyldig tilstand/vilkår fradrag?
            override val avslagsgrunner: List<Avslagsgrunn> = vilkårsvurderinger.avslagsgrunner

            override val periode: Periode = aldersvurdering.stønadsperiode.periode
            override val simulering: Simulering? = null

            init {
                kastHvisGrunnlagsdataOgVilkårsvurderingerPeriodenOgBehandlingensPerioderErUlike()
            }

            override fun skalSendeVedtaksbrev(): Boolean {
                return true
            }

            override fun nyOppgaveId(nyOppgaveId: OppgaveId): UtenBeregning {
                return this.copy(oppgaveId = nyOppgaveId)
            }

            override fun tilUnderkjent(
                attestering: Attestering.Underkjent,
            ): Either<KunneIkkeUnderkjenneSøknadsbehandling.AttestantOgSaksbehandlerKanIkkeVæreSammePerson, UnderkjentSøknadsbehandling.Avslag.UtenBeregning> {
                if (attestering.attestant.navIdent == saksbehandler.navIdent) {
                    return KunneIkkeUnderkjenneSøknadsbehandling.AttestantOgSaksbehandlerKanIkkeVæreSammePerson.left()
                }
                return UnderkjentSøknadsbehandling.Avslag.UtenBeregning(
                    id = id,
                    opprettet = opprettet,
                    sakId = sakId,
                    saksnummer = saksnummer,
                    søknad = søknad,
                    oppgaveId = oppgaveId,
                    fnr = fnr,
                    saksbehandler = saksbehandler,
                    attesteringer = attesteringer.leggTilNyAttestering(attestering),
                    søknadsbehandlingsHistorikk = søknadsbehandlingsHistorikk,
                    fritekstTilBrev = fritekstTilBrev,
                    aldersvurdering = aldersvurdering,
                    grunnlagsdataOgVilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger,

                    sakstype = sakstype,
                    omgjøringsårsak = omgjøringsårsak,
                    omgjøringsgrunn = omgjøringsgrunn,
                ).right()
            }

            fun tilIverksatt(
                attestering: Attestering,
                fritekst: String,
            ): IverksattSøknadsbehandling.Avslag.UtenBeregning {
                return IverksattSøknadsbehandling.Avslag.UtenBeregning(
                    id = id,
                    opprettet = opprettet,
                    sakId = sakId,
                    saksnummer = saksnummer,
                    søknad = søknad,
                    oppgaveId = oppgaveId,
                    fnr = fnr,
                    saksbehandler = saksbehandler,
                    attesteringer = attesteringer.leggTilNyAttestering(attestering),
                    søknadsbehandlingsHistorikk = søknadsbehandlingsHistorikk,
                    fritekstTilBrev = fritekst,
                    aldersvurdering = aldersvurdering,
                    grunnlagsdataOgVilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger,

                    sakstype = sakstype,
                    omgjøringsårsak = omgjøringsårsak,
                    omgjøringsgrunn = omgjøringsgrunn,
                )
            }
        }

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
            override val fritekstTilBrev: String,
            override val aldersvurdering: Aldersvurdering,
            override val grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderingerSøknadsbehandling,
            override val attesteringer: Attesteringshistorikk,
            override val søknadsbehandlingsHistorikk: Søknadsbehandlingshistorikk,
            override val sakstype: Sakstype,
            override val omgjøringsårsak: Revurderingsårsak.Årsak?,
            override val omgjøringsgrunn: Omgjøringsgrunn?,
        ) : Avslag {
            override val stønadsperiode: Stønadsperiode = aldersvurdering.stønadsperiode
            private val avslagsgrunnForBeregning: List<Avslagsgrunn> =
                when (val vurdering = VurderAvslagGrunnetBeregning.vurderAvslagGrunnetBeregning(beregning)) {
                    is AvslagGrunnetBeregning.Ja -> listOf(vurdering.grunn.toAvslagsgrunn())
                    is AvslagGrunnetBeregning.Nei -> emptyList()
                }

            // TODO fiks typing/gyldig tilstand/vilkår fradrag?
            override val avslagsgrunner: List<Avslagsgrunn> = vilkårsvurderinger.avslagsgrunner + avslagsgrunnForBeregning

            override val periode: Periode = aldersvurdering.stønadsperiode.periode
            override val simulering: Simulering? = null

            init {
                kastHvisGrunnlagsdataOgVilkårsvurderingerPeriodenOgBehandlingensPerioderErUlike()
                grunnlagsdata.kastHvisIkkeAlleBosituasjonerErFullstendig()
            }

            override fun skalSendeVedtaksbrev(): Boolean {
                return true
            }

            override fun nyOppgaveId(nyOppgaveId: OppgaveId): MedBeregning {
                return this.copy(oppgaveId = nyOppgaveId)
            }

            override fun tilUnderkjent(
                attestering: Attestering.Underkjent,
            ): Either<KunneIkkeUnderkjenneSøknadsbehandling.AttestantOgSaksbehandlerKanIkkeVæreSammePerson, UnderkjentSøknadsbehandling.Avslag.MedBeregning> {
                if (attestering.attestant.navIdent == saksbehandler.navIdent) return KunneIkkeUnderkjenneSøknadsbehandling.AttestantOgSaksbehandlerKanIkkeVæreSammePerson.left()

                return UnderkjentSøknadsbehandling.Avslag.MedBeregning(
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
                    søknadsbehandlingsHistorikk = søknadsbehandlingsHistorikk,
                    fritekstTilBrev = fritekstTilBrev,
                    aldersvurdering = aldersvurdering,
                    grunnlagsdataOgVilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger,
                    sakstype = sakstype,
                    omgjøringsårsak = omgjøringsårsak,
                    omgjøringsgrunn = omgjøringsgrunn,
                ).right()
            }

            internal fun tilIverksatt(
                attestering: Attestering,
                fritekst: String,
            ): IverksattSøknadsbehandling.Avslag.MedBeregning {
                return IverksattSøknadsbehandling.Avslag.MedBeregning(
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
                    søknadsbehandlingsHistorikk = søknadsbehandlingsHistorikk,
                    fritekstTilBrev = fritekst,
                    aldersvurdering = aldersvurdering,
                    grunnlagsdataOgVilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger,
                    sakstype = sakstype,
                    omgjøringsårsak = omgjøringsårsak,
                    omgjøringsgrunn = omgjøringsgrunn,
                )
            }
        }
    }
}
