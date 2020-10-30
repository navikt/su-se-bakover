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
import no.nav.su.se.bakover.domain.Behandling
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.behandling.NySøknadsbehandling
import no.nav.su.se.bakover.domain.beregning.Fradrag
import no.nav.su.se.bakover.domain.brev.LagBrevRequest
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringFeilet
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.service.behandling.BehandlingMetrics.Avslått.incrementAvslåttBehandlingPersistedCounter
import no.nav.su.se.bakover.service.behandling.BehandlingMetrics.Innvilget.incrementInnvilgetBehandlingOppgaveCounter
import no.nav.su.se.bakover.service.behandling.BehandlingMetrics.Innvilget.incrementInnvilgetBehandlingPersistedCounter
import no.nav.su.se.bakover.service.behandling.BehandlingMetrics.TilAttestering.incrementOppgaveForBehandlingTilAttesteringCounter
import no.nav.su.se.bakover.service.behandling.BehandlingMetrics.TilAttestering.incrementPersistedBehandlingTilAttesteringCounter
import no.nav.su.se.bakover.service.behandling.KunneIkkeSendeTilAttestering.InternFeil
import no.nav.su.se.bakover.service.behandling.KunneIkkeSendeTilAttestering.KunneIkkeFinneAktørId
import no.nav.su.se.bakover.service.behandling.KunneIkkeSendeTilAttestering.UgyldigKombinasjonSakOgBehandling
import no.nav.su.se.bakover.service.brev.BrevService
import no.nav.su.se.bakover.service.oppgave.OppgaveService
import no.nav.su.se.bakover.service.sak.SakService
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
    private val sakService: SakService,
    private val personOppslag: PersonOppslag,
    private val brevService: BrevService
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
        sakId: UUID,
        behandlingId: UUID,
        saksbehandler: NavIdentBruker.Saksbehandler,
    ): Either<KunneIkkeSendeTilAttestering, Behandling> {

        val sak = sakService.hentSak(sakId).getOrElse {
            log.info("Fant ikke sak med sakId : $sakId")
            return UgyldigKombinasjonSakOgBehandling.left()
        }

        val behandling = sak.behandlinger()
            .firstOrNull { it.id == behandlingId }?.sendTilAttestering(saksbehandler)
            ?: return UgyldigKombinasjonSakOgBehandling.left()
                .also { log.info("Fant ikke behandling $behandlingId på sak med id $sakId") }

        val aktørId = personOppslag.aktørId(sak.fnr).getOrElse {
            log.error("Fant ikke aktør-id med for fødselsnummer : ${sak.fnr}")
            return KunneIkkeFinneAktørId.left()
        }

        oppgaveService.opprettOppgave(
            OppgaveConfig.Attestering(
                behandling.sakId.toString(),
                aktørId = aktørId
            )
        ).mapLeft {
            log.error("Kunne ikke opprette Attestering oppgave")
            return InternFeil.left()
        }

        behandlingRepo.settSaksbehandler(behandlingId, saksbehandler)
        behandlingRepo.oppdaterBehandlingStatus(behandlingId, behandling.status())
        incrementPersistedBehandlingTilAttesteringCounter()

        oppgaveService.ferdigstillFørstegangsoppgave(
            aktørId = aktørId
        ).map {
            incrementOppgaveForBehandlingTilAttesteringCounter()
        }
        return behandling.right()
    }

    // TODO need to define responsibilities for domain and services.
    // TODO refactor the beast
    override fun iverksett(
        behandlingId: UUID,
        attestant: NavIdentBruker.Attestant
    ): Either<Behandling.IverksettFeil, Behandling> {
        return behandlingRepo.hentBehandling(behandlingId)!!.iverksett(attestant) // invoke first to perform state-check
            .map { behandling ->
                return when (behandling.status()) {
                    Behandling.BehandlingsStatus.IVERKSATT_AVSLAG -> iverksettAvslag(
                        behandlingId = behandlingId,
                        attestant = attestant,
                        behandling = behandling
                    )
                    Behandling.BehandlingsStatus.IVERKSATT_INNVILGET -> iverksettInnvilgning(
                        behandling = behandling,
                        attestant = attestant,
                        behandlingId = behandlingId
                    )
                    else -> throw Behandling.TilstandException(
                        state = behandling.status(),
                        operation = behandling::iverksett.toString()
                    )
                }
            }
    }

    private fun iverksettAvslag(
        behandlingId: UUID,
        attestant: NavIdentBruker.Attestant,
        behandling: Behandling
    ): Either<Behandling.IverksettFeil, Behandling> {

        behandlingRepo.attester(behandlingId, attestant)
        behandlingRepo.oppdaterBehandlingStatus(behandlingId, behandling.status())
        incrementAvslåttBehandlingPersistedCounter()

        return journalførOgDistribuerBrev(
            request = LagBrevRequest.AvslagsVedtak(behandling = behandling),
            sakId = behandling.sakId,
            incrementJournalførtCounter = BehandlingMetrics.Avslått::incrementAvslåttBehandlingJournalførtCounter,
            incrementDistribuertBrevCounter = BehandlingMetrics.Avslått::incrementAvslåttBehandlingDistribuertBrevCounter
        ).map { behandling }
    }

    private fun iverksettInnvilgning(
        behandling: Behandling,
        attestant: NavIdentBruker.Attestant,
        behandlingId: UUID
    ): Either<Behandling.IverksettFeil, Behandling> {
        val aktørId = personOppslag.aktørId(behandling.fnr).getOrElse {
            log.error("Lukk attesteringsoppgave: Fant ikke aktør-id med for fødselsnummer : ${behandling.fnr}")
            return Behandling.IverksettFeil.FantIkkeAktørId.left()
        }
        return utbetalingService.utbetal(
            sakId = behandling.sakId,
            attestant = attestant,
            beregning = behandling.beregning()!!,
            simulering = behandling.simulering()!!
        ).mapLeft {
            when (it) {
                KunneIkkeUtbetale.SimuleringHarBlittEndretSidenSaksbehandlerSimulerte -> Behandling.IverksettFeil.SimuleringHarBlittEndretSidenSaksbehandlerSimulerte
                KunneIkkeUtbetale.Protokollfeil -> Behandling.IverksettFeil.KunneIkkeUtbetale
                KunneIkkeUtbetale.KunneIkkeSimulere -> Behandling.IverksettFeil.KunneIkkeKontrollSimulere
            }
        }.flatMap { oversendtUtbetaling ->
            behandlingRepo.leggTilUtbetaling(
                behandlingId = behandlingId,
                utbetalingId = oversendtUtbetaling.id
            )
            behandlingRepo.attester(behandlingId, attestant)
            behandlingRepo.oppdaterBehandlingStatus(behandlingId, behandling.status())
            incrementInnvilgetBehandlingPersistedCounter()

            oppgaveService.ferdigstillAttesteringsoppgave(aktørId)
                .map { incrementInnvilgetBehandlingOppgaveCounter() }

            journalførOgDistribuerBrev(
                request = LagBrevRequest.InnvilgetVedtak(behandling = behandling),
                sakId = behandling.sakId,
                incrementJournalførtCounter = BehandlingMetrics.Innvilget::incrementInnvilgetBehandlingJournalførtCounter,
                incrementDistribuertBrevCounter = BehandlingMetrics.Innvilget::incrementInnvilgetBehandlingDistribuertBrevCounter
            ).map { behandling }
        }
    }

    private fun journalførOgDistribuerBrev(
        request: LagBrevRequest,
        sakId: UUID,
        incrementJournalførtCounter: () -> Unit,
        incrementDistribuertBrevCounter: () -> Unit,
    ): Either<Behandling.IverksettFeil, Unit> =
        brevService.journalførBrev(request, sakId)
            .mapLeft { Behandling.IverksettFeil.KunneIkkeJournalføreBrev }
            .flatMap {
                incrementJournalførtCounter()
                brevService.distribuerBrev(it)
                    .mapLeft { Behandling.IverksettFeil.KunneIkkeDistribuereBrev }
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
        return søknadService.hentSøknad(søknadId)
            .map {
                val nySøknadsbehandling = NySøknadsbehandling(
                    id = UUID.randomUUID(),
                    opprettet = now(),
                    sakId = it.sakId,
                    søknadId = søknadId
                )
                behandlingRepo.opprettSøknadsbehandling(
                    nySøknadsbehandling
                )
                behandlingRepo.hentBehandling(nySøknadsbehandling.id)!!
            }.mapLeft {
                KunneIkkeOppretteSøknadsbehandling.FantIkkeSøknad
            }
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
