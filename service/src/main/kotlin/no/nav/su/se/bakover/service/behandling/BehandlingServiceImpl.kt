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
import no.nav.su.se.bakover.domain.AvsluttetBegrunnelse
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
            .map {
                behandlingRepo.oppdaterBehandlingStatus(it.id, it.status())
                hendelsesloggRepo.oppdaterHendelseslogg(it.hendelseslogg)
                behandlingRepo.hentBehandling(it.id)!!
            }
    }

    // TODO need to define responsibilities for domain and services.
    override fun oppdaterBehandlingsinformasjon(
        behandlingId: UUID,
        behandlingsinformasjon: Behandlingsinformasjon
    ): Behandling {
        return behandlingRepo.hentBehandling(behandlingId)!!
            .oppdaterBehandlingsinformasjon(behandlingsinformasjon) // invoke first to perform state-check
            .let {
                beregningRepo.slettBeregningForBehandling(behandlingId)
                behandlingRepo.oppdaterBehandlingsinformasjon(behandlingId, it.behandlingsinformasjon())
                behandlingRepo.oppdaterBehandlingStatus(behandlingId, it.status())
                it
            }
    }

    // TODO need to define responsibilities for domain and services.
    override fun opprettBeregning(
        behandlingId: UUID,
        fraOgMed: LocalDate,
        tilOgMed: LocalDate,
        fradrag: List<Fradrag>
    ): Behandling {
        return behandlingRepo.hentBehandling(behandlingId)!!
            .opprettBeregning(fraOgMed, tilOgMed, fradrag) // invoke first to perform state-check
            .let {
                beregningRepo.slettBeregningForBehandling(behandlingId)
                beregningRepo.opprettBeregningForBehandling(behandlingId, it.beregning()!!)
                behandlingRepo.oppdaterBehandlingStatus(behandlingId, it.status())
                it
            }
    }

    // TODO need to define responsibilities for domain and services.
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
            .map {
                val beforeUpdate = behandling.copy() // need ref to existing utbetaling to delete
                return behandling.simuler(utbetaling).map { behandling -> // invoke first to perform state-check
                    beforeUpdate.utbetaling()?.let { utbetalingService.slettUtbetaling(it) }
                    utbetalingService.opprettUtbetaling(oppdrag.id, utbetaling)
                    utbetalingService.addSimulering(utbetaling.id, it)
                    behandlingRepo.leggTilUtbetaling(behandlingId, utbetaling.id)
                    behandlingRepo.oppdaterBehandlingStatus(behandling.id, behandling.status())
                    behandlingRepo.hentBehandling(behandlingId)!!
                }
            }
    }

    // TODO need to define responsibilities for domain and services.
    override fun sendTilAttestering(
        behandlingId: UUID,
        aktørId: AktørId,
        saksbehandler: Saksbehandler
    ): Either<KunneIkkeOppretteOppgave, Behandling> {
        return behandlingRepo.hentBehandling(behandlingId)!!
            .sendTilAttestering(aktørId, saksbehandler) // invoke first to perform state-check
            .let { behandling ->
                oppgaveClient.opprettOppgave(
                    OppgaveConfig.Attestering(
                        behandling.sakId.toString(),
                        aktørId = aktørId
                    )
                ).map {
                    behandlingRepo.settSaksbehandler(behandlingId, saksbehandler)
                    behandlingRepo.oppdaterBehandlingStatus(behandlingId, behandling.status())
                    behandling
                }
            }
    }

    // TODO need to define responsibilities for domain and services.
    override fun iverksett(behandlingId: UUID, attestant: Attestant): Either<Behandling.IverksettFeil, Behandling> {
        return behandlingRepo.hentBehandling(behandlingId)!!.iverksett(attestant) // invoke first to perform state-check
            .map { behandling ->
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
                    behandlingRepo.attester(behandlingId, attestant)
                    behandlingRepo.oppdaterBehandlingStatus(behandlingId, behandling.status())
                    behandling
                }
            }
    }

    // TODO need to define responsibilities for domain and services.
    override fun opprettSøknadsbehandling(sakId: UUID, søknadId: UUID): Either<FantIkkeSøknad, Behandling> {
        return søknadService.hentSøknad(søknadId)
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

    override fun slettBehandlingForBehandling(søknadId: UUID, avsluttetBegrunnelse: AvsluttetBegrunnelse) {
        return behandlingRepo.slettBehandlingForBehandling(søknadId, avsluttetBegrunnelse)
    }
}
