package no.nav.su.se.bakover.domain.søknadsbehandling

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.nonEmptyListOf
import arrow.core.right
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.domain.avkorting.AvkortingVedSøknadsbehandling
import no.nav.su.se.bakover.domain.behandling.Attesteringshistorikk
import no.nav.su.se.bakover.domain.behandling.avslag.Avslagsgrunn
import no.nav.su.se.bakover.domain.grunnlag.EksterneGrunnlagSkatt
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag.Bosituasjon.Companion.inneholderUfullstendigeBosituasjoner
import no.nav.su.se.bakover.domain.grunnlag.GrunnlagsdataOgVilkårsvurderinger
import no.nav.su.se.bakover.domain.grunnlag.OpplysningspliktBeskrivelse
import no.nav.su.se.bakover.domain.grunnlag.Opplysningspliktgrunnlag
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.sak.Saksnummer
import no.nav.su.se.bakover.domain.sak.Sakstype
import no.nav.su.se.bakover.domain.søknad.Søknad
import no.nav.su.se.bakover.domain.søknadsbehandling.avslag.ErAvslag
import no.nav.su.se.bakover.domain.søknadsbehandling.stønadsperiode.Aldersvurdering
import no.nav.su.se.bakover.domain.søknadsbehandling.stønadsperiode.Stønadsperiode
import no.nav.su.se.bakover.domain.vilkår.OpplysningspliktVilkår
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderingsresultat
import no.nav.su.se.bakover.domain.vilkår.VurderingsperiodeOpplysningsplikt
import java.time.Clock
import java.util.UUID

sealed class VilkårsvurdertSøknadsbehandling :
    Søknadsbehandling,
    Søknadsbehandling.KanOppdaterePeriodeGrunnlagVilkår {

    override fun leggTilSkatt(skatt: EksterneGrunnlagSkatt): Either<KunneIkkeLeggeTilSkattegrunnlag, Søknadsbehandling> {
        return copyInternal(
            grunnlagsdataOgVilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger.leggTilSkatt(skatt),
        ).right()
    }

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
            aldersvurdering: Aldersvurdering,
            grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderinger.Søknadsbehandling,
            attesteringer: Attesteringshistorikk,
            saksbehandlingsHistorikk: Søknadsbehandlingshistorikk,
            sakstype: Sakstype,
            saksbehandler: NavIdentBruker.Saksbehandler,
        ): VilkårsvurdertSøknadsbehandling {
            val oppdaterteGrunnlagsdataOgVilkårsvurderinger =
                if (!grunnlagsdataOgVilkårsvurderinger.harVurdertOpplysningsplikt()) {
                    grunnlagsdataOgVilkårsvurderinger.leggTil(
                        /**
                         * Legger til implisitt vilkår for oppfylt opplysningsplikt dersom dette ikke er vurdert fra før.
                         * Tar enn så lenge ikke stilling til dette vilkåret fra frontend ved søknadsbehandling.
                         */
                        lagOpplysningspliktVilkår(opprettet, aldersvurdering),
                    )
                } else { grunnlagsdataOgVilkårsvurderinger }
            return when (oppdaterteGrunnlagsdataOgVilkårsvurderinger.vilkårsvurderinger.vurdering) {
                is Vilkårsvurderingsresultat.Avslag -> {
                    Avslag(
                        id = id,
                        opprettet = opprettet,
                        sakId = sakId,
                        saksnummer = saksnummer,
                        søknad = søknad,
                        oppgaveId = oppgaveId,
                        fnr = fnr,
                        fritekstTilBrev = fritekstTilBrev,
                        aldersvurdering = aldersvurdering,
                        grunnlagsdataOgVilkårsvurderinger = oppdaterteGrunnlagsdataOgVilkårsvurderinger,
                        attesteringer = attesteringer,
                        søknadsbehandlingsHistorikk = saksbehandlingsHistorikk,
                        sakstype = sakstype,
                        saksbehandler = saksbehandler,
                    )
                }

                is Vilkårsvurderingsresultat.Innvilget -> {
                    Innvilget(
                        id = id,
                        opprettet = opprettet,
                        sakId = sakId,
                        saksnummer = saksnummer,
                        søknad = søknad,
                        oppgaveId = oppgaveId,
                        fnr = fnr,
                        fritekstTilBrev = fritekstTilBrev,
                        aldersvurdering = aldersvurdering,
                        grunnlagsdataOgVilkårsvurderinger = oppdaterteGrunnlagsdataOgVilkårsvurderinger,
                        attesteringer = attesteringer,
                        søknadsbehandlingsHistorikk = saksbehandlingsHistorikk,
                        sakstype = sakstype,
                        saksbehandler = saksbehandler,
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
                        aldersvurdering = aldersvurdering,
                        grunnlagsdataOgVilkårsvurderinger = oppdaterteGrunnlagsdataOgVilkårsvurderinger,
                        attesteringer = attesteringer,
                        søknadsbehandlingsHistorikk = saksbehandlingsHistorikk,
                        sakstype = sakstype,
                        saksbehandler = saksbehandler,
                    )
                }
            }
        }

        private fun lagOpplysningspliktVilkår(
            opprettet: Tidspunkt,
            aldersvurdering: Aldersvurdering,
        ) = OpplysningspliktVilkår.Vurdert.tryCreate(
            vurderingsperioder = nonEmptyListOf(
                VurderingsperiodeOpplysningsplikt.create(
                    id = UUID.randomUUID(),
                    opprettet = opprettet,
                    periode = aldersvurdering.stønadsperiode.periode,
                    grunnlag = Opplysningspliktgrunnlag(
                        id = UUID.randomUUID(),
                        opprettet = opprettet,
                        periode = aldersvurdering.stønadsperiode.periode,
                        beskrivelse = OpplysningspliktBeskrivelse.TilstrekkeligDokumentasjon,
                    ),
                ),
            ),
        ).getOrElse { throw IllegalArgumentException(it.toString()) }
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
        override val aldersvurdering: Aldersvurdering,
        override val grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderinger.Søknadsbehandling,
        override val attesteringer: Attesteringshistorikk,
        override val søknadsbehandlingsHistorikk: Søknadsbehandlingshistorikk,
        override val sakstype: Sakstype,
        override val saksbehandler: NavIdentBruker.Saksbehandler,
    ) : VilkårsvurdertSøknadsbehandling(), KanBeregnes {
        override val periode: Periode = aldersvurdering.stønadsperiode.periode
        override val stønadsperiode: Stønadsperiode = aldersvurdering.stønadsperiode

        override val beregning = null
        override val simulering: Simulering? = null

        /** Avkorting vurderes ikke før vi må; beregningsteget. */
        override val avkorting = AvkortingVedSøknadsbehandling.IkkeVurdert

        init {
            kastHvisGrunnlagsdataOgVilkårsvurderingerPeriodenOgBehandlingensPerioderErUlike()
            // TODO jah: Enable denne når det ikke finnes proddata med ufullstendig i denne tilstanden:
            // grunnlagsdata.kastHvisIkkeAlleBosituasjonerErFullstendig()
        }

        override fun skalSendeVedtaksbrev(): Boolean {
            return true
        }

        override fun accept(visitor: SøknadsbehandlingVisitor) {
            visitor.visit(this)
        }

        override fun copyInternal(
            stønadsperiode: Stønadsperiode,
            grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderinger.Søknadsbehandling,
            søknadsbehandlingshistorikk: Søknadsbehandlingshistorikk,
            aldersvurdering: Aldersvurdering,
        ): VilkårsvurdertSøknadsbehandling {
            return copy(
                aldersvurdering = aldersvurdering,
                grunnlagsdataOgVilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger,

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
        override val fritekstTilBrev: String,
        override val aldersvurdering: Aldersvurdering,
        override val grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderinger.Søknadsbehandling,
        override val attesteringer: Attesteringshistorikk,
        override val søknadsbehandlingsHistorikk: Søknadsbehandlingshistorikk,
        override val sakstype: Sakstype,
        override val saksbehandler: NavIdentBruker.Saksbehandler,
    ) : VilkårsvurdertSøknadsbehandling(), ErAvslag {
        override val stønadsperiode: Stønadsperiode = aldersvurdering.stønadsperiode
        override val beregning = null
        override val simulering: Simulering? = null

        override val avkorting = AvkortingVedSøknadsbehandling.IngenAvkorting

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
                grunnlagsdataOgVilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger,

                søknadsbehandlingsHistorikk = søknadsbehandlingshistorikk,
            )
        }

        override val periode: Periode = aldersvurdering.stønadsperiode.periode

        init {
            kastHvisGrunnlagsdataOgVilkårsvurderingerPeriodenOgBehandlingensPerioderErUlike()
        }

        override fun accept(visitor: SøknadsbehandlingVisitor) {
            visitor.visit(this)
        }

        /**
         * Til bruk der systemet har behov for å gjøre handling
         * Se eksempel: [AvslåPgaManglendeDokumentasjon.kt]
         */
        fun tilAttestering(
            fritekstTilBrev: String,
        ): Either<ValideringsfeilAttestering, SøknadsbehandlingTilAttestering.Avslag.UtenBeregning> {
            if (grunnlagsdata.bosituasjon.inneholderUfullstendigeBosituasjoner()) {
                return ValideringsfeilAttestering.InneholderUfullstendigBosituasjon.left()
            }
            return SøknadsbehandlingTilAttestering.Avslag.UtenBeregning(
                id = id,
                opprettet = opprettet,
                sakId = sakId,
                saksnummer = saksnummer,
                søknad = søknad,
                oppgaveId = oppgaveId,
                fnr = fnr,
                saksbehandler = saksbehandler,
                fritekstTilBrev = fritekstTilBrev,
                aldersvurdering = aldersvurdering,
                grunnlagsdataOgVilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger,

                attesteringer = attesteringer,
                søknadsbehandlingsHistorikk = this.søknadsbehandlingsHistorikk,
                sakstype = sakstype,
            ).right()
        }

        fun tilAttesteringForSaksbehandler(
            saksbehandler: NavIdentBruker.Saksbehandler,
            fritekstTilBrev: String,
            clock: Clock,
        ): Either<ValideringsfeilAttestering, SøknadsbehandlingTilAttestering.Avslag.UtenBeregning> {
            if (grunnlagsdata.bosituasjon.inneholderUfullstendigeBosituasjoner()) {
                return ValideringsfeilAttestering.InneholderUfullstendigBosituasjon.left()
            }
            return SøknadsbehandlingTilAttestering.Avslag.UtenBeregning(
                id = id,
                opprettet = opprettet,
                sakId = sakId,
                saksnummer = saksnummer,
                søknad = søknad,
                oppgaveId = oppgaveId,
                fnr = fnr,
                saksbehandler = saksbehandler,
                fritekstTilBrev = fritekstTilBrev,
                aldersvurdering = aldersvurdering,
                grunnlagsdataOgVilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger,

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
        override val aldersvurdering: Aldersvurdering?,
        override val grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderinger.Søknadsbehandling,
        override val attesteringer: Attesteringshistorikk,
        override val søknadsbehandlingsHistorikk: Søknadsbehandlingshistorikk,

        override val sakstype: Sakstype,
        override val saksbehandler: NavIdentBruker.Saksbehandler,
    ) : VilkårsvurdertSøknadsbehandling() {
        override val stønadsperiode: Stønadsperiode? = aldersvurdering?.stønadsperiode
        override val beregning = null
        override val simulering: Simulering? = null
        override val avkorting = AvkortingVedSøknadsbehandling.IkkeVurdert

        override fun skalSendeVedtaksbrev(): Boolean {
            return true
        }

        override fun copyInternal(
            stønadsperiode: Stønadsperiode,
            grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderinger.Søknadsbehandling,
            søknadsbehandlingshistorikk: Søknadsbehandlingshistorikk,
            aldersvurdering: Aldersvurdering,
        ): Uavklart {
            return copy(
                aldersvurdering = aldersvurdering,
                grunnlagsdataOgVilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger,

                søknadsbehandlingsHistorikk = søknadsbehandlingshistorikk,
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
