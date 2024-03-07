package no.nav.su.se.bakover.domain.søknadsbehandling

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.nonEmptyListOf
import arrow.core.right
import behandling.søknadsbehandling.domain.GrunnlagsdataOgVilkårsvurderingerSøknadsbehandling
import behandling.søknadsbehandling.domain.VilkårsvurderingerSøknadsbehandling
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.Stønadsperiode
import no.nav.su.se.bakover.common.domain.attestering.Attesteringshistorikk
import no.nav.su.se.bakover.common.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.domain.søknad.Søknad
import no.nav.su.se.bakover.domain.søknadsbehandling.avslag.ErAvslag
import no.nav.su.se.bakover.domain.søknadsbehandling.grunnlag.KunneIkkeLeggeTilSkattegrunnlag
import no.nav.su.se.bakover.domain.søknadsbehandling.stønadsperiode.Aldersvurdering
import no.nav.su.se.bakover.domain.søknadsbehandling.tilAttestering.KunneIkkeSendeSøknadsbehandlingTilAttestering
import vilkår.bosituasjon.domain.grunnlag.Bosituasjon.Companion.inneholderUfullstendigeBosituasjoner
import vilkår.common.domain.Avslagsgrunn
import vilkår.common.domain.Vurdering
import vilkår.opplysningsplikt.domain.OpplysningspliktBeskrivelse
import vilkår.opplysningsplikt.domain.OpplysningspliktVilkår
import vilkår.opplysningsplikt.domain.Opplysningspliktgrunnlag
import vilkår.opplysningsplikt.domain.VurderingsperiodeOpplysningsplikt
import vilkår.vurderinger.domain.EksterneGrunnlag
import vilkår.vurderinger.domain.EksterneGrunnlagSkatt
import vilkår.vurderinger.domain.Grunnlagsdata
import økonomi.domain.simulering.Simulering
import java.time.Clock
import java.util.UUID

sealed interface VilkårsvurdertSøknadsbehandling :
    Søknadsbehandling,
    KanOppdaterePeriodeBosituasjonVilkår {

    abstract override fun leggTilSkatt(skatt: EksterneGrunnlagSkatt): Either<KunneIkkeLeggeTilSkattegrunnlag, VilkårsvurdertSøknadsbehandling>

    companion object {
        /**
         * @param handling støtter null her, siden vi har noen maskinelle/automatiske handlinger som vi ikke ønsker i handlingsloggen. I.e. OppdaterStønadsperiode ved avslagPgaManglendeDokumentasjon.
         */
        // Opprett burde ikke ta inn forrige tilstand. Den burde kun gi det første steget (Uavklart)
        // dersom man har lyst til at denne skal være en 'magisk' funksjon som copy(?) så burde vel disse heller ligge i data klassene?
        fun opprett(
            forrigeTilstand: KanOppdaterePeriodeGrunnlagVilkår,
            saksbehandler: NavIdentBruker.Saksbehandler,
            grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderingerSøknadsbehandling,
            tidspunkt: Tidspunkt,
            // TODO jah: 2023-06-15 Finn en bedre løsning enn bang her.
            //  Jeg tror vi setter aldersvurderingen sammen med oppdatering av stønadsperiode.
            aldersvurdering: Aldersvurdering = forrigeTilstand.aldersvurdering!!,
            handling: SøknadsbehandlingsHandling?,
        ): VilkårsvurdertSøknadsbehandling {
            val oppdaterteGrunnlagsdataOgVilkårsvurderinger =
                if (!grunnlagsdataOgVilkårsvurderinger.harVurdertOpplysningsplikt()) {
                    grunnlagsdataOgVilkårsvurderinger.oppdaterVilkår(
                        /*
                         * Legger til implisitt vilkår for oppfylt opplysningsplikt dersom dette ikke er vurdert fra før.
                         * Tar enn så lenge ikke stilling til dette vilkåret fra frontend ved søknadsbehandling.
                         */
                        lagOpplysningspliktVilkår(tidspunkt, aldersvurdering.stønadsperiode.periode),
                    )
                } else {
                    grunnlagsdataOgVilkårsvurderinger
                }

            val søknadsbehandlingshistorikk = when (handling) {
                null -> forrigeTilstand.søknadsbehandlingsHistorikk
                else -> forrigeTilstand.søknadsbehandlingsHistorikk.leggTilNyHendelse(
                    Søknadsbehandlingshendelse(
                        tidspunkt = tidspunkt,
                        saksbehandler = saksbehandler,
                        handling = handling,
                    ),
                )
            }
            return when (oppdaterteGrunnlagsdataOgVilkårsvurderinger.vilkårsvurderinger.resultat()) {
                is Vurdering.Avslag -> {
                    Avslag(
                        forrigeTilstand = forrigeTilstand,
                        aldersvurdering = aldersvurdering,
                        grunnlagsdataOgVilkårsvurderinger = oppdaterteGrunnlagsdataOgVilkårsvurderinger,
                        søknadsbehandlingsHistorikk = søknadsbehandlingshistorikk,
                        saksbehandler = saksbehandler,
                        fritekstTilBrev = forrigeTilstand.fritekstTilBrev,
                    )
                }

                is Vurdering.Innvilget -> {
                    Innvilget(
                        id = forrigeTilstand.id,
                        opprettet = forrigeTilstand.opprettet,
                        sakId = forrigeTilstand.sakId,
                        saksnummer = forrigeTilstand.saksnummer,
                        søknad = forrigeTilstand.søknad,
                        oppgaveId = forrigeTilstand.oppgaveId,
                        fnr = forrigeTilstand.fnr,
                        fritekstTilBrev = forrigeTilstand.fritekstTilBrev,
                        aldersvurdering = aldersvurdering,
                        grunnlagsdataOgVilkårsvurderinger = oppdaterteGrunnlagsdataOgVilkårsvurderinger,
                        attesteringer = forrigeTilstand.attesteringer,
                        søknadsbehandlingsHistorikk = søknadsbehandlingshistorikk,
                        sakstype = forrigeTilstand.sakstype,
                        saksbehandler = saksbehandler,
                    )
                }

                is Vurdering.Uavklart -> {
                    Uavklart(
                        id = forrigeTilstand.id,
                        opprettet = forrigeTilstand.opprettet,
                        sakId = forrigeTilstand.sakId,
                        saksnummer = forrigeTilstand.saksnummer,
                        søknad = forrigeTilstand.søknad,
                        oppgaveId = forrigeTilstand.oppgaveId,
                        fnr = forrigeTilstand.fnr,
                        fritekstTilBrev = forrigeTilstand.fritekstTilBrev,
                        aldersvurdering = aldersvurdering,
                        grunnlagsdataOgVilkårsvurderinger = oppdaterteGrunnlagsdataOgVilkårsvurderinger,
                        attesteringer = forrigeTilstand.attesteringer,
                        søknadsbehandlingsHistorikk = søknadsbehandlingshistorikk,
                        sakstype = forrigeTilstand.sakstype,
                        saksbehandler = saksbehandler,
                    )
                }
            }
        }

        private fun lagOpplysningspliktVilkår(
            opprettet: Tidspunkt,
            periode: Periode,
        ) = OpplysningspliktVilkår.Vurdert.tryCreate(
            vurderingsperioder = nonEmptyListOf(
                VurderingsperiodeOpplysningsplikt.create(
                    id = UUID.randomUUID(),
                    opprettet = opprettet,
                    periode = periode,
                    grunnlag = Opplysningspliktgrunnlag(
                        id = UUID.randomUUID(),
                        opprettet = opprettet,
                        periode = periode,
                        beskrivelse = OpplysningspliktBeskrivelse.TilstrekkeligDokumentasjon,
                    ),
                ),
            ),
        ).getOrElse { throw IllegalArgumentException(it.toString()) }
    }

    data class Innvilget(
        override val id: SøknadsbehandlingId,
        override val opprettet: Tidspunkt,
        override val sakId: UUID,
        override val saksnummer: Saksnummer,
        override val søknad: Søknad.Journalført.MedOppgave,
        override val oppgaveId: OppgaveId,
        override val fnr: Fnr,
        override val fritekstTilBrev: String,
        override val aldersvurdering: Aldersvurdering,
        override val grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderingerSøknadsbehandling,
        override val attesteringer: Attesteringshistorikk,
        override val søknadsbehandlingsHistorikk: Søknadsbehandlingshistorikk,
        override val sakstype: Sakstype,
        override val saksbehandler: NavIdentBruker.Saksbehandler,
    ) : VilkårsvurdertSøknadsbehandling, KanBeregnes, KanOppdatereFradragsgrunnlag {
        override val periode: Periode = aldersvurdering.stønadsperiode.periode
        override val stønadsperiode: Stønadsperiode = aldersvurdering.stønadsperiode

        override val beregning = null
        override val simulering: Simulering? = null

        override fun oppdaterOppgaveId(oppgaveId: OppgaveId): Søknadsbehandling = this.copy(oppgaveId = oppgaveId)
        override fun leggTilSkatt(skatt: EksterneGrunnlagSkatt): Either<KunneIkkeLeggeTilSkattegrunnlag, Innvilget> {
            return this.copy(
                grunnlagsdataOgVilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger.leggTilSkatt(skatt),
            ).right()
        }

        init {
            kastHvisGrunnlagsdataOgVilkårsvurderingerPeriodenOgBehandlingensPerioderErUlike()
            // TODO jah: Enable denne når det ikke finnes proddata med ufullstendig i denne tilstanden:
            // grunnlagsdata.kastHvisIkkeAlleBosituasjonerErFullstendig()
        }

        override fun skalSendeVedtaksbrev(): Boolean {
            return true
        }
    }

    // internal for tester
    data class Avslag internal constructor(
        override val opprettet: Tidspunkt,
        override val sakId: UUID,
        override val saksnummer: Saksnummer,
        override val fnr: Fnr,
        override val sakstype: Sakstype,
        override val oppgaveId: OppgaveId,
        override val søknad: Søknad.Journalført.MedOppgave,
        override val id: SøknadsbehandlingId,
        override val attesteringer: Attesteringshistorikk,
        override val saksbehandler: NavIdentBruker.Saksbehandler,
        override val aldersvurdering: Aldersvurdering,
        override val grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderingerSøknadsbehandling,
        override val søknadsbehandlingsHistorikk: Søknadsbehandlingshistorikk,
        override val fritekstTilBrev: String,
    ) : VilkårsvurdertSøknadsbehandling, KanSendesTilAttestering, KanGenerereAvslagsbrev, ErAvslag {
        override val periode: Periode = aldersvurdering.stønadsperiode.periode
        override val stønadsperiode: Stønadsperiode = aldersvurdering.stønadsperiode

        constructor(
            forrigeTilstand: KanOppdaterePeriodeGrunnlagVilkår,
            saksbehandler: NavIdentBruker.Saksbehandler,
            aldersvurdering: Aldersvurdering,
            grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderingerSøknadsbehandling,
            søknadsbehandlingsHistorikk: Søknadsbehandlingshistorikk,
            fritekstTilBrev: String,
        ) : this(
            opprettet = forrigeTilstand.opprettet,
            sakId = forrigeTilstand.sakId,
            saksnummer = forrigeTilstand.saksnummer,
            fnr = forrigeTilstand.fnr,
            sakstype = forrigeTilstand.sakstype,
            oppgaveId = forrigeTilstand.oppgaveId,
            søknad = forrigeTilstand.søknad,
            id = forrigeTilstand.id,
            attesteringer = forrigeTilstand.attesteringer,
            saksbehandler = saksbehandler,
            aldersvurdering = aldersvurdering,
            grunnlagsdataOgVilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger,
            søknadsbehandlingsHistorikk = søknadsbehandlingsHistorikk,
            fritekstTilBrev = fritekstTilBrev,
        )

        constructor(
            opprettet: Tidspunkt,
            oppgaveId: OppgaveId,
            saksbehandler: NavIdentBruker.Saksbehandler,
            iverksattSøknadsbehandling: IverksattSøknadsbehandling.Avslag,
            grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderingerSøknadsbehandling,
        ) : this(
            opprettet = opprettet,
            sakId = iverksattSøknadsbehandling.sakId,
            saksnummer = iverksattSøknadsbehandling.saksnummer,
            fnr = iverksattSøknadsbehandling.fnr,
            sakstype = iverksattSøknadsbehandling.sakstype,
            oppgaveId = oppgaveId,
            søknad = iverksattSøknadsbehandling.søknad,
            id = SøknadsbehandlingId.generer(),
            attesteringer = Attesteringshistorikk.empty(),
            saksbehandler = saksbehandler,
            aldersvurdering = iverksattSøknadsbehandling.aldersvurdering,
            grunnlagsdataOgVilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger,
            søknadsbehandlingsHistorikk = Søknadsbehandlingshistorikk.nyHistorikk(
                Søknadsbehandlingshendelse(
                    tidspunkt = opprettet,
                    saksbehandler = saksbehandler,
                    handling = SøknadsbehandlingsHandling.StartetBehandlingFraEtAvslag(iverksattSøknadsbehandling.id),
                ),
            ),
            fritekstTilBrev = iverksattSøknadsbehandling.fritekstTilBrev,
        )

        override val vilkårsvurderinger: VilkårsvurderingerSøknadsbehandling =
            grunnlagsdataOgVilkårsvurderinger.vilkårsvurderinger
        override val grunnlagsdata: Grunnlagsdata = grunnlagsdataOgVilkårsvurderinger.grunnlagsdata
        override val eksterneGrunnlag: EksterneGrunnlag = grunnlagsdataOgVilkårsvurderinger.eksterneGrunnlag

        override val beregning = null
        override val simulering: Simulering? = null

        override fun oppdaterOppgaveId(oppgaveId: OppgaveId): Søknadsbehandling {
            return this.copy(oppgaveId = oppgaveId)
        }

        override fun leggTilSkatt(skatt: EksterneGrunnlagSkatt): Either<KunneIkkeLeggeTilSkattegrunnlag, Avslag> {
            return this.copy(
                grunnlagsdataOgVilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger.leggTilSkatt(skatt),
            ).right()
        }

        override fun skalSendeVedtaksbrev(): Boolean {
            return true
        }

        init {
            kastHvisGrunnlagsdataOgVilkårsvurderingerPeriodenOgBehandlingensPerioderErUlike()
        }

        /**
         * Til bruk der systemet har behov for å gjøre handling
         * Se eksempel: [no.nav.su.se.bakover.domain.søknadsbehandling.iverksett.avslå.manglendedokumentasjon.avslåSøknadPgaManglendeDokumentasjon]
         */
        fun tilAttesteringForSystembruker(
            fritekstTilBrev: String,
        ): Either<KunneIkkeSendeSøknadsbehandlingTilAttestering, SøknadsbehandlingTilAttestering.Avslag.UtenBeregning> {
            if (grunnlagsdata.bosituasjon.inneholderUfullstendigeBosituasjoner()) {
                return KunneIkkeSendeSøknadsbehandlingTilAttestering.InneholderUfullstendigBosituasjon.left()
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

        override fun tilAttestering(
            saksbehandler: NavIdentBruker.Saksbehandler,
            fritekstTilBrev: String,
            clock: Clock,
        ): Either<KunneIkkeSendeSøknadsbehandlingTilAttestering, SøknadsbehandlingTilAttestering.Avslag.UtenBeregning> {
            if (grunnlagsdata.bosituasjon.inneholderUfullstendigeBosituasjoner()) {
                return KunneIkkeSendeSøknadsbehandlingTilAttestering.InneholderUfullstendigBosituasjon.left()
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
        override val avslagsgrunner: List<Avslagsgrunn> = vilkårsvurderinger.avslagsgrunner
    }

    data class Uavklart(
        override val id: SøknadsbehandlingId,
        override val opprettet: Tidspunkt,
        override val sakId: UUID,
        override val saksnummer: Saksnummer,
        override val søknad: Søknad.Journalført.MedOppgave,
        override val oppgaveId: OppgaveId,
        override val fnr: Fnr,
        override val fritekstTilBrev: String,
        override val aldersvurdering: Aldersvurdering?,
        override val grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderingerSøknadsbehandling,
        override val attesteringer: Attesteringshistorikk,
        override val søknadsbehandlingsHistorikk: Søknadsbehandlingshistorikk,
        override val sakstype: Sakstype,
        override val saksbehandler: NavIdentBruker.Saksbehandler,
    ) : VilkårsvurdertSøknadsbehandling {

        init {
            require(sakstype == søknad.type) {
                "Støtter ikke å ha forskjellige typer (uføre, alder) på en og samme sak."
            }
        }

        override val stønadsperiode: Stønadsperiode? = aldersvurdering?.stønadsperiode
        override val beregning = null
        override val simulering: Simulering? = null
        override val periode: Periode
            get() = stønadsperiode?.periode ?: throw StønadsperiodeIkkeDefinertException(id)

        override fun skalSendeVedtaksbrev(): Boolean {
            return true
        }

        override fun oppdaterOppgaveId(oppgaveId: OppgaveId): Søknadsbehandling = this.copy(oppgaveId = oppgaveId)
        override fun leggTilSkatt(skatt: EksterneGrunnlagSkatt): Either<KunneIkkeLeggeTilSkattegrunnlag, Uavklart> {
            return this.copy(
                grunnlagsdataOgVilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger.leggTilSkatt(skatt),
            ).right()
        }

        init {
            kastHvisGrunnlagsdataOgVilkårsvurderingerPeriodenOgBehandlingensPerioderErUlike()
        }

        data class StønadsperiodeIkkeDefinertException(
            val id: SøknadsbehandlingId,
        ) : RuntimeException("Sønadsperiode er ikke definert for søknadsbehandling:$id")
    }
}
