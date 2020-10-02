package no.nav.su.se.bakover.service.behandling

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.database.behandling.BehandlingRepo
import no.nav.su.se.bakover.database.beregning.BeregningRepo
import no.nav.su.se.bakover.database.hendelseslogg.HendelsesloggRepo
import no.nav.su.se.bakover.database.oppdrag.OppdragRepo
import no.nav.su.se.bakover.domain.AktørId
import no.nav.su.se.bakover.domain.Attestant
import no.nav.su.se.bakover.domain.Behandling
import no.nav.su.se.bakover.domain.Saksbehandler
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.beregning.Fradrag
import no.nav.su.se.bakover.domain.oppdrag.NyUtbetaling
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringClient
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringFeilet
import no.nav.su.se.bakover.domain.oppdrag.utbetaling.UtbetalingPublisher
import no.nav.su.se.bakover.domain.oppgave.KunneIkkeOppretteOppgave
import no.nav.su.se.bakover.domain.oppgave.OppgaveClient
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.service.sak.SakService
import no.nav.su.se.bakover.service.søknad.FantIkkeSøknad
import no.nav.su.se.bakover.service.søknad.SøknadService
import no.nav.su.se.bakover.service.utbetaling.UtbetalingService
import java.time.LocalDate
import java.util.UUID

internal class BehandlingServiceImpl(
    private val behandlingRepo: BehandlingRepo,
    private val hendelsesloggRepo: HendelsesloggRepo,
    private val beregningRepo: BeregningRepo,
    private val oppdragRepo: OppdragRepo,
    private val simuleringClient: SimuleringClient,
    private val utbetalingService: UtbetalingService, // TODO use services or repos? probably services
    private val oppgaveClient: OppgaveClient,
    private val utbetalingPublisher: UtbetalingPublisher,
    private val søknadService: SøknadService,
    private val sakService: SakService
) : BehandlingService {
    override fun hentBehandling(behandlingId: UUID): Either<FantIkkeBehandling, Behandling> {
        return behandlingRepo.hentBehandling(behandlingId)?.right() ?: FantIkkeBehandling.left()
    }

    override fun underkjenn(
        begrunnelse: String,
        attestant: Attestant,
        behandling: Behandling
    ): Either<Behandling.KunneIkkeUnderkjenne, Behandling> {
        return behandling.underkjenn(begrunnelse, attestant)
            .mapLeft { it }
            .map {
                hendelsesloggRepo.oppdaterHendelseslogg(it.hendelseslogg)
                behandlingRepo.hentBehandling(it.id)!!
            }
    }

    override fun oppdaterBehandlingsinformasjon(
        behandlingId: UUID,
        behandlingsinformasjon: Behandlingsinformasjon
    ): Behandling {
        val beforeUpdate = behandlingRepo.hentBehandling(behandlingId)!!
        beregningRepo.slettBeregningForBehandling(behandlingId)
        val updated = behandlingRepo.oppdaterBehandlingsinformasjon(
            behandlingId,
            beforeUpdate.behandlingsinformasjon().patch(behandlingsinformasjon)
        )
        // TODO fix weirdness for internal state
        val status = updated.oppdaterBehandlingsinformasjon(behandlingsinformasjon).status()
        behandlingRepo.oppdaterBehandlingStatus(behandlingId, status)
        return behandlingRepo.hentBehandling(behandlingId)!!
    }

    override fun opprettBeregning(
        behandlingId: UUID,
        fom: LocalDate,
        tom: LocalDate,
        fradrag: List<Fradrag>
    ): Behandling {
        beregningRepo.slettBeregningForBehandling(behandlingId)
        val beregnet = behandlingRepo.hentBehandling(behandlingId)!!.opprettBeregning(fom, tom, fradrag)
        beregningRepo.opprettBeregningForBehandling(behandlingId, beregnet.beregning()!!)
        behandlingRepo.oppdaterBehandlingStatus(behandlingId, beregnet.status())
        return behandlingRepo.hentBehandling(behandlingId)!!
    }

    override fun simuler(behandlingId: UUID): Either<SimuleringFeilet, Behandling> {
        val behandling = behandlingRepo.hentBehandling(behandlingId)!!
        val sak = sakService.hentSak(behandling.sakId).fold(
            { throw RuntimeException("Kunne ikke finne sak") },
            { it }
        )
        val oppdrag = oppdragRepo.hentOppdrag(behandling.sakId)!!
        val utbetaling = oppdrag.genererUtbetaling(behandling.beregning()!!, sak.fnr)
        val utbetalingTilSimulering = NyUtbetaling(oppdrag, utbetaling, Attestant("SU"))
        return simuleringClient.simulerUtbetaling(utbetalingTilSimulering)
            .mapLeft { it }
            .map {
                behandling.utbetaling()?.let { utbetalingService.slettUtbetaling(it) }
                utbetalingService.opprettUtbetaling(oppdrag.id, utbetaling)
                utbetalingService.addSimulering(utbetaling.id, it)
                behandlingRepo.leggTilUtbetaling(behandlingId, utbetaling.id)
                val oppdatert = behandlingRepo.hentBehandling(behandlingId)!!
                oppdatert.simuler(utbetaling) // TODO just to push to correct state
                behandlingRepo.oppdaterBehandlingStatus(behandling.id, oppdatert.status())
                return behandlingRepo.hentBehandling(behandlingId)!!.right()
            }
    }

    override fun sendTilAttestering(
        behandlingId: UUID,
        aktørId: AktørId,
        saksbehandler: Saksbehandler
    ): Either<KunneIkkeOppretteOppgave, Behandling> {
        val behandling = behandlingRepo.hentBehandling(behandlingId)!!
        return oppgaveClient.opprettOppgave(
            OppgaveConfig.Attestering(
                behandling.sakId.toString(),
                aktørId = aktørId
            )
        ).map {
            behandlingRepo.settSaksbehandler(behandlingId, saksbehandler)
            val oppdatert = behandlingRepo.hentBehandling(behandlingId)!!
            oppdatert.sendTilAttestering(aktørId, saksbehandler)
            behandlingRepo.oppdaterBehandlingStatus(behandlingId, oppdatert.status())
            behandlingRepo.hentBehandling(behandlingId)!!
        }
    }

    override fun iverksett(behandlingId: UUID, attestant: Attestant): Either<Behandling.IverksettFeil, Behandling> {
        val behandling = behandlingRepo.hentBehandling(behandlingId)!!
        val utbetaling = behandling.utbetaling()!!
        return utbetalingPublisher.publish(
            NyUtbetaling(
                oppdrag = oppdragRepo.hentOppdrag(behandling.sakId)!!,
                utbetaling = utbetaling,
                attestant = attestant
            )
        ).mapLeft {
            utbetalingService.addOppdragsmelding(
                utbetaling.id,
                it.oppdragsmelding
            )
            Behandling.IverksettFeil.Utbetaling("Feil ved oversendelse av utbetaling til oppdrag!")
        }.map { oppdragsmelding ->
            utbetalingService.addOppdragsmelding(
                utbetaling.id,
                oppdragsmelding
            )
            val oppdatert = behandlingRepo.hentBehandling(behandlingId)!!
            return oppdatert.iverksett(attestant)
                .mapLeft { it }
                .map {
                    behandlingRepo.attester(behandlingId, attestant)
                    behandlingRepo.oppdaterBehandlingStatus(behandlingId, oppdatert.status())
                    behandlingRepo.hentBehandling(behandlingId)!!
                }
        }
    }

    override fun opprettSøknadsbehandling(sakId: UUID, søknadId: UUID): Either<FantIkkeSøknad, Behandling> {
        return søknadService.hentSøknad(søknadId)
            .mapLeft { it }
            .map {
                behandlingRepo.opprettSøknadsbehandling(
                    sakId,
                    Behandling(
                        sakId = sakId,
                        søknad = it
                    )
                )
            }
    }
}
