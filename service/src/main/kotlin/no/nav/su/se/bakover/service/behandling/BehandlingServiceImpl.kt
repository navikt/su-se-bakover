package no.nav.su.se.bakover.service.behandling

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.Tidspunkt.Companion.now
import no.nav.su.se.bakover.database.behandling.BehandlingRepo
import no.nav.su.se.bakover.database.hendelseslogg.HendelsesloggRepo
import no.nav.su.se.bakover.database.søknad.SøknadRepo
import no.nav.su.se.bakover.domain.AktørId
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.behandling.Behandling
import no.nav.su.se.bakover.domain.behandling.BehandlingMetrics
import no.nav.su.se.bakover.domain.behandling.BehandlingMetrics.UnderkjentHandlinger
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.behandling.NySøknadsbehandling
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradrag
import no.nav.su.se.bakover.domain.brev.LagBrevRequest
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringFeilet
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.person.PersonOppslag
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
    private val utbetalingService: UtbetalingService,
    private val oppgaveService: OppgaveService,
    private val søknadService: SøknadService,
    private val søknadRepo: SøknadRepo, // TODO use services or repos? probably services
    private val personOppslag: PersonOppslag,
    private val brevService: BrevService,
    private val behandlingMetrics: BehandlingMetrics
) : BehandlingService {

    private val log = LoggerFactory.getLogger(this::class.java)

    override fun hentBehandling(behandlingId: UUID): Either<FantIkkeBehandling, Behandling> {
        return behandlingRepo.hentBehandling(behandlingId)?.right() ?: FantIkkeBehandling.left()
    }

    override fun underkjenn(
        behandlingId: UUID,
        attestant: NavIdentBruker.Attestant,
        begrunnelse: String
    ): Either<KunneIkkeUnderkjenneBehandling, Behandling> {
        return hentBehandling(behandlingId).mapLeft {
            KunneIkkeUnderkjenneBehandling.FantIkkeBehandling
        }.flatMap { behandling ->
            behandling.underkjenn(begrunnelse, attestant)
                .mapLeft {
                    KunneIkkeUnderkjenneBehandling.AttestantOgSaksbehandlerKanIkkeVæreSammePerson
                }
                .map {
                    val aktørId: AktørId = personOppslag.aktørId(behandling.fnr).getOrElse {
                        return KunneIkkeUnderkjenneBehandling.FantIkkeAktørId.left()
                    }

                    val journalpostId: JournalpostId = behandling.søknad.journalpostId
                    val eksisterendeOppgaveId = behandling.oppgaveId()
                    val nyOppgaveId = oppgaveService.opprettOppgave(
                        OppgaveConfig.Saksbehandling(
                            journalpostId = journalpostId,
                            sakId = behandling.sakId,
                            aktørId = aktørId,
                            tilordnetRessurs = behandling.saksbehandler()
                        )
                    ).getOrElse {
                        log.error("Kunne ikke opprette behandlingsoppgave ved underkjenning. Avbryter handlingen.")
                        return@underkjenn KunneIkkeUnderkjenneBehandling.KunneIkkeOppretteOppgave.left()
                    }.also {
                        behandlingMetrics.incrementUnderkjentCounter(UnderkjentHandlinger.OPPRETTET_OPPGAVE)
                    }
                    behandling.oppdaterOppgaveId(nyOppgaveId)
                    behandlingRepo.oppdaterAttestant(behandlingId, attestant)
                    behandlingRepo.oppdaterOppgaveId(behandling.id, nyOppgaveId)
                    behandlingRepo.oppdaterBehandlingStatus(it.id, it.status())
                    hendelsesloggRepo.oppdaterHendelseslogg(it.hendelseslogg)
                    behandlingMetrics.incrementUnderkjentCounter(UnderkjentHandlinger.PERSISTERT)
                    oppgaveService.lukkOppgave(eksisterendeOppgaveId)
                        .mapLeft {
                            log.error("Kunne ikke lukke attesteringsoppgave $eksisterendeOppgaveId ved underkjenning av behandlingen. Dette må gjøres manuelt.")
                        }.map {
                            behandlingMetrics.incrementUnderkjentCounter(UnderkjentHandlinger.LUKKET_OPPGAVE)
                        }
                    behandling
                }
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
                behandlingRepo.slettBeregning(behandlingId)
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
                behandlingRepo.leggTilBeregning(it.id, it.beregning()!!)
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

        val eksisterendeOppgaveId: OppgaveId = behandlingTilAttestering.oppgaveId()

        val nyOppgaveId: OppgaveId = oppgaveService.opprettOppgave(
            OppgaveConfig.Attestering(
                behandlingTilAttestering.sakId,
                aktørId = aktørId,
                // Første gang den sendes til attestering er attestant null, de påfølgende gangene vil den være attestanten som har underkjent.
                tilordnetRessurs = behandlingTilAttestering.attestant()
            )
        ).getOrElse {
            log.error("Kunne ikke opprette Attesteringsoppgave. Avbryter handlingen.")
            return KunneIkkeSendeTilAttestering.KunneIkkeOppretteOppgave.left()
        }.also {
            behandlingMetrics.incrementTilAttesteringCounter(BehandlingMetrics.TilAttesteringHandlinger.OPPRETTET_OPPGAVE)
        }
        behandlingTilAttestering.oppdaterOppgaveId(nyOppgaveId)
        behandlingRepo.oppdaterOppgaveId(behandlingTilAttestering.id, nyOppgaveId)
        behandlingRepo.settSaksbehandler(behandlingId, saksbehandler)
        behandlingRepo.oppdaterBehandlingStatus(behandlingId, behandlingTilAttestering.status())
        behandlingMetrics.incrementTilAttesteringCounter(BehandlingMetrics.TilAttesteringHandlinger.PERSISTERT)

        oppgaveService.lukkOppgave(eksisterendeOppgaveId).map {
            behandlingMetrics.incrementTilAttesteringCounter(BehandlingMetrics.TilAttesteringHandlinger.LUKKET_OPPGAVE)
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
    ): Either<KunneIkkeIverksetteBehandling, IverksattBehandling> {
        val behandling = behandlingRepo.hentBehandling(behandlingId)
            ?: return KunneIkkeIverksetteBehandling.FantIkkeBehandling.left()

        return behandling.iverksett(attestant) // invoke first to perform state-check
            .mapLeft {
                KunneIkkeIverksetteBehandling.AttestantOgSaksbehandlerKanIkkeVæreSammePerson
            }
            .map { iverksattBehandling ->
                return when (iverksattBehandling.status()) {
                    Behandling.BehandlingsStatus.IVERKSATT_AVSLAG -> iverksettAvslag(
                        behandling = iverksattBehandling,
                        attestant = attestant
                    )
                    Behandling.BehandlingsStatus.IVERKSATT_INNVILGET -> iverksettInnvilgning(
                        behandling = iverksattBehandling,
                        attestant = attestant,
                        behandlingId = behandlingId
                    )
                    else -> throw Behandling.TilstandException(
                        state = iverksattBehandling.status(),
                        operation = iverksattBehandling::iverksett.toString()
                    )
                }
            }
    }

    private fun iverksettAvslag(
        behandling: Behandling,
        attestant: NavIdentBruker.Attestant
    ): Either<KunneIkkeIverksetteBehandling, IverksattBehandling> {

        val journalpostId = brevService.journalførBrev(LagBrevRequest.AvslagsVedtak(behandling), behandling.sakId)
            .map {
                behandling.oppdaterIverksattJournalpostId(it)
                it
            }
            .getOrElse {
                return KunneIkkeIverksetteBehandling.KunneIkkeJournalføreBrev.left()
            }
        behandlingMetrics.incrementAvslåttCounter(BehandlingMetrics.AvslåttHandlinger.JOURNALFØRT)

        behandlingRepo.oppdaterIverksattJournalpostId(behandling.id, journalpostId)
        behandlingRepo.oppdaterAttestant(behandling.id, attestant)
        behandlingRepo.oppdaterBehandlingStatus(behandling.id, behandling.status())
        behandlingMetrics.incrementAvslåttCounter(BehandlingMetrics.AvslåttHandlinger.PERSISTERT)

        val brevResultat = brevService.distribuerBrev(journalpostId)
            .mapLeft { IverksattBehandling.MedMangler.KunneIkkeDistribuereBrev(behandling) }
            .map {
                behandling.oppdaterIverksattBrevbestillingId(it)
                behandlingRepo.oppdaterIverksattBrevbestillingId(behandling.id, it)
                behandlingMetrics.incrementAvslåttCounter(BehandlingMetrics.AvslåttHandlinger.DISTRIBUERT_BREV)
                IverksattBehandling.UtenMangler(behandling)
            }

        val oppgaveResultat = oppgaveService.lukkOppgave(behandling.oppgaveId())
            .mapLeft {
                log.error("Kunne ikke lukke oppgave ved iverksetting av avslag. Dette må gjøres manuelt.")
                IverksattBehandling.MedMangler.KunneIkkeLukkeOppgave(behandling)
            }
            .map {
                // TODO jah: Vurder behandling.oppdaterOppgaveId(null), men den kan ikke være null atm.
                behandlingMetrics.incrementAvslåttCounter(BehandlingMetrics.AvslåttHandlinger.LUKKET_OPPGAVE)
                IverksattBehandling.UtenMangler(behandling)
            }

        return brevResultat.flatMap { oppgaveResultat }.fold(
            { it.right() },
            { it.right() }
        )
    }

    private fun iverksettInnvilgning(
        behandling: Behandling,
        attestant: NavIdentBruker.Attestant,
        behandlingId: UUID
    ): Either<KunneIkkeIverksetteBehandling, IverksattBehandling> {

        return utbetalingService.utbetal(
            sakId = behandling.sakId,
            attestant = attestant,
            beregning = behandling.beregning()!!,
            simulering = behandling.simulering()!!
        ).mapLeft {
            when (it) {
                KunneIkkeUtbetale.SimuleringHarBlittEndretSidenSaksbehandlerSimulerte -> KunneIkkeIverksetteBehandling.SimuleringHarBlittEndretSidenSaksbehandlerSimulerte
                KunneIkkeUtbetale.Protokollfeil -> KunneIkkeIverksetteBehandling.KunneIkkeUtbetale
                KunneIkkeUtbetale.KunneIkkeSimulere -> KunneIkkeIverksetteBehandling.KunneIkkeKontrollsimulere
            }
        }.flatMap { oversendtUtbetaling ->
            behandlingRepo.leggTilUtbetaling(
                behandlingId = behandlingId,
                utbetalingId = oversendtUtbetaling.id
            )
            behandlingRepo.oppdaterAttestant(behandlingId, attestant)
            behandlingRepo.oppdaterBehandlingStatus(behandlingId, behandling.status())
            behandlingMetrics.incrementInnvilgetCounter(BehandlingMetrics.InnvilgetHandlinger.PERSISTERT)

            val journalføringOgBrevResultat = brevService.journalførBrev(LagBrevRequest.InnvilgetVedtak(behandling), behandling.sakId)
                .mapLeft {
                    IverksattBehandling.MedMangler.KunneIkkeJournalføreBrev(behandling)
                }
                .flatMap {
                    behandling.oppdaterIverksattJournalpostId(it)
                    behandlingRepo.oppdaterIverksattJournalpostId(behandling.id, it)
                    behandlingMetrics.incrementInnvilgetCounter(BehandlingMetrics.InnvilgetHandlinger.JOURNALFØRT)
                    brevService.distribuerBrev(it)
                        .mapLeft { IverksattBehandling.MedMangler.KunneIkkeDistribuereBrev(behandling) }
                        .map { brevbestillingId ->
                            behandling.oppdaterIverksattBrevbestillingId(brevbestillingId)
                            behandlingRepo.oppdaterIverksattBrevbestillingId(behandling.id, brevbestillingId)
                            behandlingMetrics.incrementInnvilgetCounter(BehandlingMetrics.InnvilgetHandlinger.DISTRIBUERT_BREV)
                            IverksattBehandling.UtenMangler(behandling)
                        }
                }

            val oppgaveResultat = oppgaveService.lukkOppgave(behandling.oppgaveId())
                .mapLeft {
                    log.error("Kunne ikke lukke oppgave ved innvilget iverksetting. Behandlingen er sendt til utbetaling og er iverksatt. Oppgaven må lukkes manuelt.")
                    IverksattBehandling.MedMangler.KunneIkkeLukkeOppgave(behandling)
                }
                .map {
                    // TODO jah: Vurder behandling.oppdaterOppgaveId(null), men den kan ikke være null atm.
                    behandlingMetrics.incrementInnvilgetCounter(BehandlingMetrics.InnvilgetHandlinger.LUKKET_OPPGAVE)
                    IverksattBehandling.UtenMangler(behandling)
                }

            return journalføringOgBrevResultat.flatMap { oppgaveResultat }.fold(
                { it.right() },
                { it.right() }
            )
        }
    }

    override fun opprettSøknadsbehandling(
        søknadId: UUID
    ): Either<KunneIkkeOppretteSøknadsbehandling, Behandling> {
        val søknad = søknadService.hentSøknad(søknadId).getOrElse {
            return KunneIkkeOppretteSøknadsbehandling.FantIkkeSøknad.left()
        }
        if (søknad is Søknad.Lukket) {
            return KunneIkkeOppretteSøknadsbehandling.SøknadErLukket.left()
        }
        if (søknad !is Søknad.Journalført.MedOppgave) {
            // TODO Prøv å opprette oppgaven hvis den mangler?
            return KunneIkkeOppretteSøknadsbehandling.SøknadManglerOppgave.left()
        }
        if (søknadRepo.harSøknadPåbegyntBehandling(søknad.id)) {
            // Dersom man legger til avslutting av behandlinger, må denne spørringa spesifiseres.
            return KunneIkkeOppretteSøknadsbehandling.SøknadHarAlleredeBehandling.left()
        }
        val nySøknadsbehandling = NySøknadsbehandling(
            id = UUID.randomUUID(),
            opprettet = now(),
            sakId = søknad.sakId,
            søknadId = søknad.id,
            oppgaveId = søknad.oppgaveId
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
