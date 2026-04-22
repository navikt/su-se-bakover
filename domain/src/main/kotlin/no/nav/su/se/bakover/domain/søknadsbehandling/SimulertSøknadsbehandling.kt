package no.nav.su.se.bakover.domain.søknadsbehandling

import LeggTilVedtaksbrevvalgSøknadsbehandling
import arrow.core.Either
import arrow.core.NonEmptyList
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import behandling.søknadsbehandling.domain.GrunnlagsdataOgVilkårsvurderingerSøknadsbehandling
import beregning.domain.Beregning
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.Stønadsperiode
import no.nav.su.se.bakover.common.domain.attestering.Attesteringshistorikk
import no.nav.su.se.bakover.common.domain.extensions.toNonEmptyList
import no.nav.su.se.bakover.common.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.sikkerLogg
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.domain.revurdering.Omgjøringsgrunn
import no.nav.su.se.bakover.domain.revurdering.årsak.Revurderingsårsak
import no.nav.su.se.bakover.domain.søknad.Søknad
import no.nav.su.se.bakover.domain.søknadsbehandling.brev.BrevvalgSøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.grunnlag.KunneIkkeLeggeTilSkattegrunnlag
import no.nav.su.se.bakover.domain.søknadsbehandling.simuler.KunneIkkeSimulereBehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.stønadsperiode.Aldersvurdering
import no.nav.su.se.bakover.domain.søknadsbehandling.tilAttestering.KunneIkkeSendeSøknadsbehandlingTilAttestering
import vilkår.bosituasjon.domain.grunnlag.Bosituasjon.Companion.inneholderUfullstendigeBosituasjoner
import vilkår.uføre.domain.Uføregrunnlag
import vilkår.vurderinger.domain.EksterneGrunnlagSkatt
import økonomi.domain.simulering.Simulering
import økonomi.domain.simulering.SimuleringFeilet
import java.time.Clock
import java.util.UUID

data class SimulertSøknadsbehandling(
    override val id: SøknadsbehandlingId,
    override val opprettet: Tidspunkt,
    override val sakId: UUID,
    override val saksnummer: Saksnummer,
    override val søknad: Søknad.Journalført.MedOppgave,
    override val oppgaveId: OppgaveId,
    override val fnr: Fnr,
    override val beregning: Beregning,
    override val simulering: Simulering,
    override val aldersvurdering: Aldersvurdering,
    override val grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderingerSøknadsbehandling,
    override val attesteringer: Attesteringshistorikk,
    override val søknadsbehandlingsHistorikk: Søknadsbehandlingshistorikk,
    override val sakstype: Sakstype,
    override val saksbehandler: NavIdentBruker.Saksbehandler,
    override val omgjøringsårsak: Revurderingsårsak.Årsak?,
    override val omgjøringsgrunn: Omgjøringsgrunn?,
    override val brevvalgSøknadsbehandling: BrevvalgSøknadsbehandling = BrevvalgSøknadsbehandling.IkkeValgt,

) : Søknadsbehandling,
    KanOppdaterePeriodeBosituasjonVilkår,
    KanBeregnes,
    KanSimuleres,
    KanSendesTilAttestering,
    KanGenerereInnvilgelsesbrev,
    KanOppdatereFradragsgrunnlag,
    LeggTilVedtaksbrevvalgSøknadsbehandling {
    // TODO jah: Den må enten arve bergnet sin periode, eller definere denne selv (vi kan ikke la aldersvurdering eie den). Også må init sjekke at aldersperioden har samme periode.
    override val periode: Periode = aldersvurdering.stønadsperiode.periode

    override val stønadsperiode: Stønadsperiode = aldersvurdering.stønadsperiode

    override fun erÅpen() = true
    override fun erAvsluttet() = false
    override fun erAvbrutt() = false

    init {
        kastHvisGrunnlagsdataOgVilkårsvurderingerPeriodenOgBehandlingensPerioderErUlike()
        grunnlagsdata.kastHvisIkkeAlleBosituasjonerErFullstendig()
    }
    override fun oppdaterOppgaveId(oppgaveId: OppgaveId): Søknadsbehandling = this.copy(oppgaveId = oppgaveId)
    override fun skalSendeVedtaksbrev() = brevvalgSøknadsbehandling.skalSendeBrev().isRight()

    override fun simuler(
        saksbehandler: NavIdentBruker.Saksbehandler,
        clock: Clock,
        simuler: (beregning: Beregning, uføregrunnlag: NonEmptyList<Uføregrunnlag>?) -> Either<SimuleringFeilet, Simulering>,
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
                omgjøringsårsak = omgjøringsårsak,
                omgjøringsgrunn = omgjøringsgrunn,
                brevvalgSøknadsbehandling = brevvalgSøknadsbehandling,
            )
        }
    }

    override fun tilAttestering(
        saksbehandler: NavIdentBruker.Saksbehandler,
        clock: Clock,
    ): Either<KunneIkkeSendeSøknadsbehandlingTilAttestering, SøknadsbehandlingTilAttestering.Innvilget> {
        if (grunnlagsdata.bosituasjon.inneholderUfullstendigeBosituasjoner()) {
            return KunneIkkeSendeSøknadsbehandlingTilAttestering.InneholderUfullstendigBosituasjon.left()
        }

        if (simulering.harFeilutbetalinger()) {
            /**
             * Kun en nødbrems for tilfeller som i utgangspunktet skal være håndtert og forhindret av andre mekanismer.
             */
            sikkerLogg.warn("Simulering inneholder feilutbetalinger (se vanlig log for stacktrace): $simulering")
            return KunneIkkeSendeSøknadsbehandlingTilAttestering.Feilutbetalinger(sakId.toString()).left()
        }
        if (brevvalgSøknadsbehandling !is BrevvalgSøknadsbehandling.Valgt) {
            return KunneIkkeSendeSøknadsbehandlingTilAttestering.BrevvalgMangler.left()
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
            omgjøringsårsak = omgjøringsårsak,
            omgjøringsgrunn = omgjøringsgrunn,
            brevvalgSøknadsbehandling = brevvalgSøknadsbehandling,
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

    override fun leggTilBrevvalg(
        brevvalgSøknadsbehandling: BrevvalgSøknadsbehandling.Valgt,
    ): SimulertSøknadsbehandling {
        return copy(
            brevvalgSøknadsbehandling = brevvalgSøknadsbehandling,
            saksbehandler = when (val bestemtAv = brevvalgSøknadsbehandling.bestemtAv) {
                is BrevvalgSøknadsbehandling.BestemtAv.Behandler -> NavIdentBruker.Saksbehandler(bestemtAv.ident)
                is BrevvalgSøknadsbehandling.BestemtAv.Systembruker -> saksbehandler
            },

        )
    }
}
