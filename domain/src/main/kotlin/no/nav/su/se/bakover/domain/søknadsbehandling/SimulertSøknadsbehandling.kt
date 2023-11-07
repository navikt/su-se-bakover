package no.nav.su.se.bakover.domain.søknadsbehandling

import arrow.core.Either
import arrow.core.NonEmptyList
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.attestering.Attesteringshistorikk
import no.nav.su.se.bakover.common.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.extensions.toNonEmptyList
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.sikkerLogg
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.grunnlag.EksterneGrunnlagSkatt
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag.Bosituasjon.Companion.inneholderUfullstendigeBosituasjoner
import no.nav.su.se.bakover.domain.grunnlag.GrunnlagsdataOgVilkårsvurderinger
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringFeilet
import no.nav.su.se.bakover.domain.søknad.Søknad
import no.nav.su.se.bakover.domain.søknadsbehandling.simuler.KunneIkkeSimulereBehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.stønadsperiode.Aldersvurdering
import no.nav.su.se.bakover.domain.søknadsbehandling.stønadsperiode.Stønadsperiode
import no.nav.su.se.bakover.domain.søknadsbehandling.tilAttestering.KunneIkkeSendeSøknadsbehandlingTilAttestering
import økonomi.domain.simulering.Simulering
import java.time.Clock
import java.util.UUID

data class SimulertSøknadsbehandling(
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
    override val aldersvurdering: Aldersvurdering,
    override val grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderinger.Søknadsbehandling,
    override val attesteringer: Attesteringshistorikk,
    override val søknadsbehandlingsHistorikk: Søknadsbehandlingshistorikk,
    override val sakstype: Sakstype,
    override val saksbehandler: NavIdentBruker.Saksbehandler,
) : Søknadsbehandling, KanOppdaterePeriodeBosituasjonVilkår, KanBeregnes, KanSimuleres, KanSendesTilAttestering, KanGenerereInnvilgelsesbrev, KanOppdatereFradragsgrunnlag {
    // TODO jah: Den må enten arve bergnet sin periode, eller definere denne selv (vi kan ikke la aldersvurdering eie den). Også må init sjekke at aldersperioden har samme periode.
    override val periode: Periode = aldersvurdering.stønadsperiode.periode

    override val stønadsperiode: Stønadsperiode = aldersvurdering.stønadsperiode

    init {
        kastHvisGrunnlagsdataOgVilkårsvurderingerPeriodenOgBehandlingensPerioderErUlike()
        grunnlagsdata.kastHvisIkkeAlleBosituasjonerErFullstendig()
    }

    override fun skalSendeVedtaksbrev(): Boolean {
        return true
    }

    override fun simuler(
        saksbehandler: NavIdentBruker.Saksbehandler,
        clock: Clock,
        simuler: (beregning: Beregning, uføregrunnlag: NonEmptyList<Grunnlag.Uføregrunnlag>?) -> Either<SimuleringFeilet, Simulering>,
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
                grunnlagsdataOgVilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger,

                attesteringer = attesteringer,
                søknadsbehandlingsHistorikk = søknadsbehandlingsHistorikk.leggTilNyHendelse(
                    saksbehandlingsHendelse = Søknadsbehandlingshendelse(
                        tidspunkt = Tidspunkt.now(clock),
                        saksbehandler = saksbehandler,
                        handling = SøknadsbehandlingsHandling.Simulert,
                    ),
                ),
                sakstype = sakstype,
                saksbehandler = saksbehandler,
            )
        }
    }

    override fun tilAttestering(
        saksbehandler: NavIdentBruker.Saksbehandler,
        fritekstTilBrev: String,
        clock: Clock,
    ): Either<KunneIkkeSendeSøknadsbehandlingTilAttestering, SøknadsbehandlingTilAttestering.Innvilget> {
        if (grunnlagsdata.bosituasjon.inneholderUfullstendigeBosituasjoner()) {
            return KunneIkkeSendeSøknadsbehandlingTilAttestering.InneholderUfullstendigBosituasjon.left()
        }

        if (simulering.harFeilutbetalinger()) {
            /**
             * Kun en nødbrems for tilfeller som i utgangspunktet skal være håndtert og forhindret av andre mekanismer.
             */
            sikkerLogg.error("Simulering inneholder feilutbetalinger (se vanlig log for stacktrace): $simulering")
            throw IllegalStateException("Simulering inneholder feilutbetalinger. Se sikkerlogg for detaljer.")
        }
        return SøknadsbehandlingTilAttestering.Innvilget(
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

    override fun leggTilSkatt(
        skatt: EksterneGrunnlagSkatt,
    ): Either<KunneIkkeLeggeTilSkattegrunnlag, SimulertSøknadsbehandling> {
        return when (this.eksterneGrunnlag.skatt) {
            is EksterneGrunnlagSkatt.Hentet -> this.copy(
                grunnlagsdataOgVilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger.leggTilSkatt(skatt),
            ).right()

            EksterneGrunnlagSkatt.IkkeHentet -> KunneIkkeLeggeTilSkattegrunnlag.KanIkkeLeggeTilSkattForTilstandUtenAtDenHarBlittHentetFør.left()
        }
    }
}
