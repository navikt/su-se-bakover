package no.nav.su.se.bakover.service.behandling

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.client.person.PersonOppslag
import no.nav.su.se.bakover.common.Tidspunkt.Companion.now
import no.nav.su.se.bakover.database.behandling.BehandlingRepo
import no.nav.su.se.bakover.database.beregning.BeregningRepo
import no.nav.su.se.bakover.database.hendelseslogg.HendelsesloggRepo
import no.nav.su.se.bakover.database.søknad.SøknadRepo
import no.nav.su.se.bakover.domain.AktørId
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.behandling.Behandling
import no.nav.su.se.bakover.domain.behandling.BehandlingMetrics
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.behandling.NySøknadsbehandling
import no.nav.su.se.bakover.domain.beregning.Fradrag
import no.nav.su.se.bakover.domain.brev.LagBrevRequest
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringFeilet
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.service.brev.BrevService
import no.nav.su.se.bakover.service.oppgave.OppgaveService
import no.nav.su.se.bakover.service.søknad.SøknadService
import no.nav.su.se.bakover.service.utbetaling.KunneIkkeUtbetale
import no.nav.su.se.bakover.service.utbetaling.UtbetalingService
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.util.UUID

internal class BehandlingServiceImpl(
    private val behandlingRepo: BehandlingRepo,
    private val hendelsesloggRepo: HendelsesloggRepo,
    private val beregningRepo: BeregningRepo,
    private val utbetalingService: UtbetalingService,
    private val oppgaveService: OppgaveService,
    private val søknadService: SøknadService, // TODO use services or repos? probably services
    private val søknadRepo: SøknadRepo,
    private val personOppslag: PersonOppslag,
    private val brevService: BrevService,
    private val behandlingMetrics: BehandlingMetrics
) : BehandlingService {

    private val log = LoggerFactory.getLogger(this::class.java)

    override fun hentBehandling(behandlingId: UUID): Either<FantIkkeBehandling, Behandling> {
        return behandlingRepo.hentBehandling(behandlingId)?.right() ?: FantIkkeBehandling.left()
    }

    override fun underkjenn(
        begrunnelse: String,
        attestant: NavIdentBruker.Attestant,
        behandling: Behandling
    ): Either<Behandling.KunneIkkeUnderkjenne, Behandling> {
        return behandling.underkjenn(begrunnelse, attestant)
            .map {
                behandlingMetrics.incrementUnderkjentCounter()
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
                beregningRepo.opprettBeregningForBehandling(behandlingId, it.beregning()!!)
                behandlingRepo.oppdaterBehandlingStatus(behandlingId, it.status())
                it
            }
    }

    // TODO need to define responsibilities for domain and services.
    override fun simuler(behandlingId: UUID, saksbehandler: NavIdentBruker): Either<SimuleringFeilet, Behandling> {
        val behandling = behandlingRepo.hentBehandling(behandlingId)!!

        return utbetalingService.simulerUtbetaling(behandling.sakId, saksbehandler, behandling.beregning()!!)
            .map { simulertUtbetaling ->
                behandling.leggTilSimulering(simulertUtbetaling.simulering)
                behandlingRepo.leggTilSimulering(behandlingId, simulertUtbetaling.simulering)
                behandlingRepo.oppdaterBehandlingStatus(behandlingId, behandling.status())
                behandlingRepo.hentBehandling(behandlingId)!!
            }
    }

    // TODO need to define responsibilities for domain and services.
    override fun sendTilAttestering(
        behandlingId: UUID,
        saksbehandler: NavIdentBruker.Saksbehandler,
    ): Either<KunneIkkeSendeTilAttestering, Behandling> {

        val behandlingTilAttestering: Behandling =
            behandlingRepo.hentBehandling(behandlingId)?.sendTilAttestering(saksbehandler)
                ?: return KunneIkkeSendeTilAttestering.FantIkkeBehandling.left()

        val aktørId = personOppslag.aktørId(behandlingTilAttestering.fnr).getOrElse {
            log.error("Fant ikke aktør-id med for fødselsnummer : ${behandlingTilAttestering.fnr}")
            return KunneIkkeSendeTilAttestering.KunneIkkeFinneAktørId.left()
        }

        oppgaveService.opprettOppgave(
            OppgaveConfig.Attestering(
                behandlingTilAttestering.sakId.toString(),
                aktørId = aktørId
            )
        ).mapLeft {
            log.error("Kunne ikke opprette Attestering oppgave")
            return KunneIkkeSendeTilAttestering.InternFeil.left()
        }

        behandlingRepo.settSaksbehandler(behandlingId, saksbehandler)
        behandlingRepo.oppdaterBehandlingStatus(behandlingId, behandlingTilAttestering.status())
        behandlingMetrics.incrementTilAttesteringCounter(BehandlingMetrics.TilAttesteringHandlinger.PERSISTERT)

        oppgaveService.lukkOppgave(behandlingTilAttestering.oppgaveId()).map {
            behandlingMetrics.incrementTilAttesteringCounter(BehandlingMetrics.TilAttesteringHandlinger.OPPGAVE)
        }.mapLeft {
            log.error("Klarte ikke å lukke oppgave. kall til oppgave for oppgaveId ${behandlingTilAttestering.oppgaveId()} feilet")
        }
        return behandlingTilAttestering.right()
    }

    // TODO need to define responsibilities for domain and services.
    // TODO refactor the beast
    override fun iverksett(
        behandlingId: UUID,
        attestant: NavIdentBruker.Attestant
    ): Either<Behandling.KunneIkkeIverksetteBehandling, Behandling> {
        val behandling = behandlingRepo.hentBehandling(behandlingId)
            ?: return Behandling.KunneIkkeIverksetteBehandling.FantIkkeBehandling.left()

        return behandling.iverksett(attestant) // invoke first to perform state-check
            .map { iverksattBehandling ->
                val aktørId = personOppslag.aktørId(behandling.fnr).getOrElse {
                    log.error("Lukk attesteringsoppgave: Fant ikke aktør-id med for fødselsnummer : ${behandling.fnr}")
                    return Behandling.KunneIkkeIverksetteBehandling.FantIkkeAktørId.left()
                }
                return when (iverksattBehandling.status()) {
                    Behandling.BehandlingsStatus.IVERKSATT_AVSLAG -> iverksettAvslag(
                        behandlingId = behandlingId,
                        attestant = attestant,
                        behandling = iverksattBehandling,
                        aktørId = aktørId
                    )
                    Behandling.BehandlingsStatus.IVERKSATT_INNVILGET -> iverksettInnvilgning(
                        behandling = iverksattBehandling,
                        attestant = attestant,
                        behandlingId = behandlingId,
                        aktørId = aktørId
                    )
                    else -> throw Behandling.TilstandException(
                        state = iverksattBehandling.status(),
                        operation = iverksattBehandling::iverksett.toString()
                    )
                }
            }
    }

    private fun iverksettAvslag(
        behandlingId: UUID,
        attestant: NavIdentBruker.Attestant,
        behandling: Behandling,
        aktørId: AktørId
    ): Either<Behandling.KunneIkkeIverksetteBehandling, Behandling> {

        behandlingRepo.attester(behandlingId, attestant)
        behandlingRepo.oppdaterBehandlingStatus(behandlingId, behandling.status())

        behandlingMetrics.incrementAvslåttCounter(BehandlingMetrics.AvslåttHandlinger.PERSISTERT)

        oppgaveService.ferdigstillAttesteringsoppgave(aktørId)
            .map { behandlingMetrics.incrementAvslåttCounter(BehandlingMetrics.AvslåttHandlinger.LUKKET_OPPGAVE) }

        return journalførOgDistribuerBrev(
            request = LagBrevRequest.AvslagsVedtak(behandling = behandling),
            sakId = behandling.sakId,
            incrementJournalførtCounter = { behandlingMetrics.incrementAvslåttCounter(BehandlingMetrics.AvslåttHandlinger.JOURNALFØRT) },
            incrementDistribuertBrevCounter = { behandlingMetrics.incrementAvslåttCounter(BehandlingMetrics.AvslåttHandlinger.DISTRIBUERT_BREV) }
        ).map { behandling }
    }

    private fun iverksettInnvilgning(
        behandling: Behandling,
        attestant: NavIdentBruker.Attestant,
        behandlingId: UUID,
        aktørId: AktørId
    ): Either<Behandling.KunneIkkeIverksetteBehandling, Behandling> {
        return utbetalingService.utbetal(
            sakId = behandling.sakId,
            attestant = attestant,
            beregning = behandling.beregning()!!,
            simulering = behandling.simulering()!!
        ).mapLeft {
            when (it) {
                KunneIkkeUtbetale.SimuleringHarBlittEndretSidenSaksbehandlerSimulerte -> Behandling.KunneIkkeIverksetteBehandling.SimuleringHarBlittEndretSidenSaksbehandlerSimulerte
                KunneIkkeUtbetale.Protokollfeil -> Behandling.KunneIkkeIverksetteBehandling.KunneIkkeUtbetale
                KunneIkkeUtbetale.KunneIkkeSimulere -> Behandling.KunneIkkeIverksetteBehandling.KunneIkkeKontrollsimulere
            }
        }.flatMap { oversendtUtbetaling ->
            behandlingRepo.leggTilUtbetaling(
                behandlingId = behandlingId,
                utbetalingId = oversendtUtbetaling.id
            )
            behandlingRepo.attester(behandlingId, attestant)
            behandlingRepo.oppdaterBehandlingStatus(behandlingId, behandling.status())
            behandlingMetrics.incrementInnvilgetCounter(BehandlingMetrics.InnvilgetHandlinger.PERSISTERT)

            oppgaveService.ferdigstillAttesteringsoppgave(aktørId)
                .map { behandlingMetrics.incrementInnvilgetCounter(BehandlingMetrics.InnvilgetHandlinger.OPPGAVE) }

            journalførOgDistribuerBrev(
                request = LagBrevRequest.InnvilgetVedtak(behandling = behandling),
                sakId = behandling.sakId,
                incrementJournalførtCounter = { behandlingMetrics.incrementInnvilgetCounter(BehandlingMetrics.InnvilgetHandlinger.JOURNALFØRT) },
                incrementDistribuertBrevCounter = { behandlingMetrics.incrementInnvilgetCounter(BehandlingMetrics.InnvilgetHandlinger.DISTRIBUERT_BREV) }
            ).map { behandling }
        }
    }

    private fun journalførOgDistribuerBrev(
        request: LagBrevRequest,
        sakId: UUID,
        incrementJournalførtCounter: () -> Unit,
        incrementDistribuertBrevCounter: () -> Unit,
    ): Either<Behandling.KunneIkkeIverksetteBehandling, Unit> =
        brevService.journalførBrev(request, sakId)
            .mapLeft { Behandling.KunneIkkeIverksetteBehandling.KunneIkkeJournalføreBrev }
            .flatMap {
                incrementJournalførtCounter()
                brevService.distribuerBrev(it)
                    .mapLeft { Behandling.KunneIkkeIverksetteBehandling.KunneIkkeDistribuereBrev }
                    .map {
                        incrementDistribuertBrevCounter()
                        Unit
                    }
            }

    // TODO need to define responsibilities for domain and services.
    override fun opprettSøknadsbehandling(
        søknadId: UUID
    ): Either<KunneIkkeOppretteSøknadsbehandling, Behandling> {
        // TODO: sjekk at det ikke finnes eksisterende behandling som ikke er avsluttet
        // TODO: + sjekk at søknad ikke er lukket
        val søknad = søknadService.hentSøknad(søknadId).getOrElse {
            return KunneIkkeOppretteSøknadsbehandling.FantIkkeSøknad.left()
        }
        // TODO Prøv å opprette oppgaven hvis den mangler?
        val oppgaveId = søknad.oppgaveId ?: return KunneIkkeOppretteSøknadsbehandling.SøknadManglerOppgave.left()

        val nySøknadsbehandling = NySøknadsbehandling(
            id = UUID.randomUUID(),
            opprettet = now(),
            sakId = søknad.sakId,
            søknadId = søknadId,
            oppgaveId = oppgaveId
        )
        behandlingRepo.opprettSøknadsbehandling(
            nySøknadsbehandling
        )
        return behandlingRepo.hentBehandling(nySøknadsbehandling.id)!!.right()
    }

    override fun lagBrevutkast(behandlingId: UUID): Either<KunneIkkeLageBrevutkast, ByteArray> {
        return hentBehandling(behandlingId)
            .mapLeft { KunneIkkeLageBrevutkast.FantIkkeBehandling }
            .flatMap { behandling ->
                brevService.lagBrev(lagBrevRequest(behandling))
                    .mapLeft { KunneIkkeLageBrevutkast.KunneIkkeLageBrev }
                    .map { it }
            }
    }

    private fun lagBrevRequest(behandling: Behandling) = when (behandling.erInnvilget()) {
        true -> LagBrevRequest.InnvilgetVedtak(behandling)
        false -> LagBrevRequest.AvslagsVedtak(behandling)
    }
}
