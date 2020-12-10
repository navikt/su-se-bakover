package no.nav.su.se.bakover.service.behandling

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.getOrElse
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.right
import arrow.core.rightIfNotNull
import no.nav.su.se.bakover.client.person.MicrosoftGraphApiOppslag
import no.nav.su.se.bakover.client.person.MicrosoftGraphApiOppslagFeil
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.database.behandling.BehandlingRepo
import no.nav.su.se.bakover.database.hendelseslogg.HendelsesloggRepo
import no.nav.su.se.bakover.database.søknad.SøknadRepo
import no.nav.su.se.bakover.domain.AktørId
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Person
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.behandling.Behandling
import no.nav.su.se.bakover.domain.behandling.BehandlingMetrics
import no.nav.su.se.bakover.domain.behandling.BehandlingMetrics.UnderkjentHandlinger
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.behandling.NySøknadsbehandling
import no.nav.su.se.bakover.domain.behandling.avslag.Avslag
import no.nav.su.se.bakover.domain.behandling.avslag.AvslagBrevRequest
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradrag
import no.nav.su.se.bakover.domain.brev.LagBrevRequest
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.person.PersonOppslag
import no.nav.su.se.bakover.domain.vedtak.snapshot.Vedtakssnapshot
import no.nav.su.se.bakover.service.brev.BrevService
import no.nav.su.se.bakover.service.oppgave.OppgaveService
import no.nav.su.se.bakover.service.søknad.SøknadService
import no.nav.su.se.bakover.service.utbetaling.KunneIkkeUtbetale
import no.nav.su.se.bakover.service.utbetaling.UtbetalingService
import no.nav.su.se.bakover.service.vedtak.snapshot.OpprettVedtakssnapshotService
import org.slf4j.LoggerFactory
import java.time.Clock
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
    private val opprettVedtakssnapshotService: OpprettVedtakssnapshotService,
    private val behandlingMetrics: BehandlingMetrics,
    private val clock: Clock,
    private val microsoftGraphApiClient: MicrosoftGraphApiOppslag
) : BehandlingService {

    private val log = LoggerFactory.getLogger(this::class.java)

    override fun hentBehandling(behandlingId: UUID): Either<FantIkkeBehandling, Behandling> {
        return behandlingRepo.hentBehandling(behandlingId)?.right() ?: FantIkkeBehandling.left()
    }

    override fun underkjenn(
        behandlingId: UUID,
        attestering: Attestering.Underkjent
    ): Either<KunneIkkeUnderkjenneBehandling, Behandling> {
        return hentBehandling(behandlingId).mapLeft {
            log.info("Kunne ikke underkjenne ukjent behandling $behandlingId")
            KunneIkkeUnderkjenneBehandling.FantIkkeBehandling
        }.flatMap { behandling ->
            behandling.underkjenn(attestering)
                .mapLeft {
                    log.warn("Kunne ikke underkjenne behandling siden attestant og saksbehandler var samme person")
                    KunneIkkeUnderkjenneBehandling.AttestantOgSaksbehandlerKanIkkeVæreSammePerson
                }
                .map {
                    val aktørId: AktørId = personOppslag.aktørId(behandling.fnr).getOrElse {
                        log.error("Kunne ikke underkjenne behandling; fant ikke aktør id")
                        return KunneIkkeUnderkjenneBehandling.FantIkkeAktørId.left()
                    }

                    val journalpostId: JournalpostId = behandling.søknad.journalpostId
                    val eksisterendeOppgaveId = behandling.oppgaveId()
                    val nyOppgaveId = oppgaveService.opprettOppgave(
                        OppgaveConfig.Saksbehandling(
                            journalpostId = journalpostId,
                            søknadId = behandling.søknad.id,
                            aktørId = aktørId,
                            tilordnetRessurs = behandling.saksbehandler()
                        )
                    ).getOrElse {
                        log.error("Behandling $behandlingId ble ikke underkjent. Klarte ikke opprette behandlingsoppgave")
                        return@underkjenn KunneIkkeUnderkjenneBehandling.KunneIkkeOppretteOppgave.left()
                    }.also {
                        behandlingMetrics.incrementUnderkjentCounter(UnderkjentHandlinger.OPPRETTET_OPPGAVE)
                    }
                    behandling.oppdaterOppgaveId(nyOppgaveId)
                    behandlingRepo.oppdaterAttestering(behandlingId, attestering)
                    behandlingRepo.oppdaterOppgaveId(behandling.id, nyOppgaveId)
                    behandlingRepo.oppdaterBehandlingStatus(it.id, it.status())
                    log.info("Behandling $behandlingId ble underkjent. Opprettet behandlingsoppgave $nyOppgaveId")
                    hendelsesloggRepo.oppdaterHendelseslogg(it.hendelseslogg)
                    behandlingMetrics.incrementUnderkjentCounter(UnderkjentHandlinger.PERSISTERT)
                    oppgaveService.lukkOppgave(eksisterendeOppgaveId)
                        .mapLeft {
                            log.error("Kunne ikke lukke attesteringsoppgave $eksisterendeOppgaveId ved underkjenning av behandlingen. Dette må gjøres manuelt.")
                        }.map {
                            log.info("Lukket attesteringsoppgave $eksisterendeOppgaveId ved underkjenning av behandlingen")
                            behandlingMetrics.incrementUnderkjentCounter(UnderkjentHandlinger.LUKKET_OPPGAVE)
                        }
                    behandling
                }
        }
    }

    // TODO need to define responsibilities for domain and services.
    override fun oppdaterBehandlingsinformasjon(
        behandlingId: UUID,
        saksbehandler: NavIdentBruker.Saksbehandler,
        behandlingsinformasjon: Behandlingsinformasjon
    ): Either<KunneIkkeOppdatereBehandlingsinformasjon, Behandling> {
        val behandling = behandlingRepo.hentBehandling(behandlingId)
            ?: return KunneIkkeOppdatereBehandlingsinformasjon.FantIkkeBehandling.left()

        return behandling.oppdaterBehandlingsinformasjon(
            saksbehandler,
            behandlingsinformasjon
        ) // invoke first to perform state-check
            .mapLeft {
                KunneIkkeOppdatereBehandlingsinformasjon.AttestantOgSaksbehandlerKanIkkeVæreSammePerson
            }
            .map {
                behandlingRepo.slettBeregning(behandlingId)
                behandlingRepo.oppdaterBehandlingsinformasjon(behandlingId, it.behandlingsinformasjon())
                behandlingRepo.oppdaterBehandlingStatus(behandlingId, it.status())
                it
            }
    }

    // TODO need to define responsibilities for domain and services.
    override fun opprettBeregning(
        behandlingId: UUID,
        saksbehandler: NavIdentBruker.Saksbehandler,
        fraOgMed: LocalDate,
        tilOgMed: LocalDate,
        fradrag: List<Fradrag>
    ): Either<KunneIkkeBeregne, Behandling> {
        val behandling = behandlingRepo.hentBehandling(behandlingId)
            ?: return KunneIkkeBeregne.FantIkkeBehandling.left()

        return behandling.opprettBeregning(
            saksbehandler,
            fraOgMed,
            tilOgMed,
            fradrag
        ) // invoke first to perform state-check
            .mapLeft {
                KunneIkkeBeregne.AttestantOgSaksbehandlerKanIkkeVæreSammePerson
            }
            .map {
                behandlingRepo.leggTilBeregning(it.id, it.beregning()!!)
                behandlingRepo.oppdaterBehandlingStatus(behandlingId, it.status())
                it
            }
    }

    // TODO need to define responsibilities for domain and services.
    override fun simuler(
        behandlingId: UUID,
        saksbehandler: NavIdentBruker.Saksbehandler
    ): Either<KunneIkkeSimulereBehandling, Behandling> {
        val behandling = behandlingRepo.hentBehandling(behandlingId)
            ?: return KunneIkkeSimulereBehandling.FantIkkeBehandling.left()

        return behandling.leggTilSimulering(saksbehandler) {
            utbetalingService.simulerUtbetaling(behandling.sakId, saksbehandler, behandling.beregning()!!)
                .map { it.simulering }.orNull()
        }.mapLeft {
            when (it) {
                Behandling.KunneIkkeLeggeTilSimulering.AttestantOgSaksbehandlerKanIkkeVæreSammePerson -> KunneIkkeSimulereBehandling.AttestantOgSaksbehandlerKanIkkeVæreSammePerson
                Behandling.KunneIkkeLeggeTilSimulering.KunneIkkeSimulere -> KunneIkkeSimulereBehandling.KunneIkkeSimulere
            }
        }.map { simulertBehandling ->
            behandlingRepo.leggTilSimulering(behandlingId, simulertBehandling.simulering()!!)
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
            behandlingRepo.hentBehandling(behandlingId).rightIfNotNull {
                return KunneIkkeSendeTilAttestering.FantIkkeBehandling.left()
            }.flatMap {
                it.sendTilAttestering(saksbehandler)
            }.getOrElse {
                return KunneIkkeSendeTilAttestering.AttestantOgSaksbehandlerKanIkkeVæreSammePerson.left()
            }

        val aktørId = personOppslag.aktørId(behandlingTilAttestering.fnr).getOrElse {
            log.error("Fant ikke aktør-id med for fødselsnummer : ${behandlingTilAttestering.fnr}")
            return KunneIkkeSendeTilAttestering.KunneIkkeFinneAktørId.left()
        }

        val eksisterendeOppgaveId: OppgaveId = behandlingTilAttestering.oppgaveId()

        val nyOppgaveId: OppgaveId = oppgaveService.opprettOppgave(
            OppgaveConfig.Attestering(
                behandlingTilAttestering.søknad.id,
                aktørId = aktørId,
                // Første gang den sendes til attestering er attestant null, de påfølgende gangene vil den være attestanten som har underkjent.
                tilordnetRessurs = behandlingTilAttestering.attestering()?.attestant
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

        val person: Person = personOppslag.person(behandling.fnr).getOrElse {
            log.error("Kunne ikke iverksette behandling; fant ikke person")
            return KunneIkkeIverksetteBehandling.FantIkkePerson.left()
        }
        return behandling.iverksett(attestant) // invoke first to perform state-check
            .mapLeft {
                KunneIkkeIverksetteBehandling.AttestantOgSaksbehandlerKanIkkeVæreSammePerson
            }
            .map { iverksattBehandling ->
                return when (iverksattBehandling.status()) {
                    Behandling.BehandlingsStatus.IVERKSATT_AVSLAG -> iverksettAvslag(
                        person = person,
                        behandling = iverksattBehandling,
                        attestant = attestant,
                    )
                    Behandling.BehandlingsStatus.IVERKSATT_INNVILGET -> iverksettInnvilgning(
                        person = person,
                        behandling = iverksattBehandling,
                        attestant = attestant,
                        behandlingId = behandlingId,
                    )
                    else -> throw Behandling.TilstandException(
                        state = iverksattBehandling.status(),
                        operation = iverksattBehandling::iverksett.toString()
                    )
                }
            }
    }

    private fun iverksettAvslag(
        person: Person,
        behandling: Behandling,
        attestant: NavIdentBruker.Attestant
    ): Either<KunneIkkeIverksetteBehandling, IverksattBehandling> {

        val avslag = Avslag(
            opprettet = Tidspunkt.now(clock),
            avslagsgrunner = behandling.utledAvslagsgrunner(),
            harEktefelle = behandling.behandlingsinformasjon().harEktefelle(),
            beregning = behandling.beregning()

        )

        val attestantNavn = hentNavnForNavIdent(attestant.navIdent)
            .getOrHandle { return KunneIkkeIverksetteBehandling.FikkIkkeHentetSaksbehandlerEllerAttestant.left() }
        val saksbehandlerNavn = hentNavnForNavIdent(behandling.saksbehandler()!!.navIdent)
            .getOrHandle { return KunneIkkeIverksetteBehandling.FikkIkkeHentetSaksbehandlerEllerAttestant.left() }

        val journalpostId = brevService.journalførBrev(
            AvslagBrevRequest(
                person = person,
                avslag = avslag,
                saksbehandlerNavn = saksbehandlerNavn,
                attestantNavn = attestantNavn
            ),
            behandling.saksnummer
        ).map {
            behandling.oppdaterIverksattJournalpostId(it)
            it
        }.getOrElse {
            log.error("Behandling ${behandling.id} ble ikke avslått siden vi ikke klarte journalføre. Saksbehandleren må prøve på nytt.")
            return KunneIkkeIverksetteBehandling.KunneIkkeJournalføreBrev.left()
        }

        behandlingMetrics.incrementAvslåttCounter(BehandlingMetrics.AvslåttHandlinger.JOURNALFØRT)

        behandlingRepo.oppdaterIverksattJournalpostId(behandling.id, journalpostId)
        behandlingRepo.oppdaterAttestering(behandling.id, Attestering.Iverksatt(attestant))
        behandlingRepo.oppdaterBehandlingStatus(behandling.id, behandling.status())
        log.info("Iverksatt avslag for behandling ${behandling.id} med journalpost $journalpostId")
        behandlingMetrics.incrementAvslåttCounter(BehandlingMetrics.AvslåttHandlinger.PERSISTERT)
        val brevResultat = brevService.distribuerBrev(journalpostId)
            .mapLeft {
                log.error("Kunne ikke bestille brev ved avslag for behandling ${behandling.id}. Dette må gjøres manuelt.")
                IverksattBehandling.MedMangler.KunneIkkeDistribuereBrev(behandling)
            }
            .map {
                behandling.oppdaterIverksattBrevbestillingId(it)
                behandlingRepo.oppdaterIverksattBrevbestillingId(behandling.id, it)
                log.info("Bestilt avslagsbrev for behandling ${behandling.id} med bestillingsid $it")
                behandlingMetrics.incrementAvslåttCounter(BehandlingMetrics.AvslåttHandlinger.DISTRIBUERT_BREV)
                IverksattBehandling.UtenMangler(behandling)
            }

        val oppgaveResultat = oppgaveService.lukkOppgave(behandling.oppgaveId())
            .mapLeft {
                log.error("Kunne ikke lukke oppgave ved avslag for behandling ${behandling.id}. Dette må gjøres manuelt.")
                IverksattBehandling.MedMangler.KunneIkkeLukkeOppgave(behandling)
            }
            .map {
                log.info("Lukket oppgave ${behandling.oppgaveId()} ved avslag for behandling ${behandling.id}")
                // TODO jah: Vurder behandling.oppdaterOppgaveId(null), men den kan ikke være null atm.
                behandlingMetrics.incrementAvslåttCounter(BehandlingMetrics.AvslåttHandlinger.LUKKET_OPPGAVE)
                IverksattBehandling.UtenMangler(behandling)
            }

        opprettVedtakssnapshotService.opprettVedtak(
            vedtakssnapshot = Vedtakssnapshot.Avslag.createFromBehandling(behandling, avslag.avslagsgrunner)
        )

        return brevResultat.flatMap { oppgaveResultat }.fold(
            { it.right() },
            { it.right() }
        )
    }

    private fun hentNavnForNavIdent(navIdent: String): Either<MicrosoftGraphApiOppslagFeil, String> {
        return microsoftGraphApiClient.hentBrukerinformasjonForNavIdent(navIdent)
            .map { it.displayName }
    }

    private fun iverksettInnvilgning(
        person: Person,
        behandling: Behandling,
        attestant: NavIdentBruker.Attestant,
        behandlingId: UUID,
    ): Either<KunneIkkeIverksetteBehandling, IverksattBehandling> {

        val attestantNavn = hentNavnForNavIdent(attestant.navIdent)
            .getOrHandle { return KunneIkkeIverksetteBehandling.FikkIkkeHentetSaksbehandlerEllerAttestant.left() }
        val saksbehandlerNavn = hentNavnForNavIdent(behandling.saksbehandler()!!.navIdent)
            .getOrHandle { return KunneIkkeIverksetteBehandling.FikkIkkeHentetSaksbehandlerEllerAttestant.left() }

        return utbetalingService.utbetal(
            sakId = behandling.sakId,
            attestant = attestant,
            beregning = behandling.beregning()!!,
            simulering = behandling.simulering()!!
        ).mapLeft {
            log.error("Kunne ikke innvilge behandling ${behandling.id} siden utbetaling feilet. Feiltype: $it")
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
            behandlingRepo.oppdaterAttestering(behandlingId, Attestering.Iverksatt(attestant))
            behandlingRepo.oppdaterBehandlingStatus(behandlingId, behandling.status())
            log.info("Behandling ${behandling.id} innvilget med utbetaling ${oversendtUtbetaling.id}")
            behandlingMetrics.incrementInnvilgetCounter(BehandlingMetrics.InnvilgetHandlinger.PERSISTERT)

            val journalføringOgBrevResultat =
                brevService.journalførBrev(
                    LagBrevRequest.InnvilgetVedtak(
                        person,
                        behandling,
                        saksbehandlerNavn,
                        attestantNavn
                    ),
                    behandling.saksnummer
                )
                    .mapLeft {
                        log.error("Journalføring av iverksettingsbrev feilet for behandling ${behandling.id}. Dette må gjøres manuelt.")
                        IverksattBehandling.MedMangler.KunneIkkeJournalføreBrev(behandling)
                    }
                    .flatMap {
                        behandling.oppdaterIverksattJournalpostId(it)
                        behandlingRepo.oppdaterIverksattJournalpostId(behandling.id, it)
                        log.info("Journalført iverksettingsbrev $it for behandling ${behandling.id}")
                        behandlingMetrics.incrementInnvilgetCounter(BehandlingMetrics.InnvilgetHandlinger.JOURNALFØRT)
                        brevService.distribuerBrev(it)
                            .mapLeft {
                                log.error("Bestilling av iverksettingsbrev feilet for behandling ${behandling.id}. Dette må gjøres manuelt.")
                                IverksattBehandling.MedMangler.KunneIkkeDistribuereBrev(behandling)
                            }
                            .map { brevbestillingId ->
                                behandling.oppdaterIverksattBrevbestillingId(brevbestillingId)
                                behandlingRepo.oppdaterIverksattBrevbestillingId(behandling.id, brevbestillingId)
                                log.info("Bestilt iverksettingsbrev $brevbestillingId for behandling ${behandling.id}")
                                behandlingMetrics.incrementInnvilgetCounter(BehandlingMetrics.InnvilgetHandlinger.DISTRIBUERT_BREV)
                                IverksattBehandling.UtenMangler(behandling)
                            }
                    }

            val oppgaveResultat = oppgaveService.lukkOppgave(behandling.oppgaveId())
                .mapLeft {
                    log.error("Kunne ikke lukke oppgave ved innvilgelse for behandling ${behandling.id}. Dette må gjøres manuelt.")
                    IverksattBehandling.MedMangler.KunneIkkeLukkeOppgave(behandling)
                }
                .map {
                    log.info("Lukket oppgave ${behandling.oppgaveId()} ved innvilgelse for behandling ${behandling.id}")
                    // TODO jah: Vurder behandling.oppdaterOppgaveId(null), men den kan ikke være null atm.
                    behandlingMetrics.incrementInnvilgetCounter(BehandlingMetrics.InnvilgetHandlinger.LUKKET_OPPGAVE)
                    IverksattBehandling.UtenMangler(behandling)
                }

            opprettVedtakssnapshotService.opprettVedtak(
                vedtakssnapshot = Vedtakssnapshot.Innvilgelse.createFromBehandling(behandling, oversendtUtbetaling)
            )

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
            opprettet = Tidspunkt.now(clock),
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
                val attestantNavn = behandling.attestering()?.let {
                    hentNavnForNavIdent(it.attestant.navIdent)
                        .getOrHandle { return KunneIkkeLageBrevutkast.FikkIkkeHentetSaksbehandlerEllerAttestant.left() }
                }
                val saksbehandlerNavn = behandling.saksbehandler()?.let {
                    hentNavnForNavIdent(it.navIdent)
                        .getOrHandle { return KunneIkkeLageBrevutkast.FikkIkkeHentetSaksbehandlerEllerAttestant.left() }
                }
                personOppslag.person(behandling.fnr)
                    .mapLeft {
                        KunneIkkeLageBrevutkast.FantIkkePerson
                    }.flatMap { person ->
                        lagBrevRequestForBrevutkast(
                            person = person,
                            behandling = behandling,
                            saksbehandlerNavn = saksbehandlerNavn,
                            attestantNavn = attestantNavn
                        ).flatMap {
                            brevService.lagBrev(it)
                                .mapLeft { KunneIkkeLageBrevutkast.KunneIkkeLageBrev }
                        }
                    }
            }
    }

    private fun lagBrevRequestForBrevutkast(
        person: Person,
        behandling: Behandling,
        saksbehandlerNavn: String?,
        attestantNavn: String?
    ): Either<KunneIkkeLageBrevutkast, LagBrevRequest> {
        if (behandling.erInnvilget()) {
            return LagBrevRequest.InnvilgetVedtak(
                person = person,
                behandling = behandling,
                saksbehandlerNavn = saksbehandlerNavn ?: "-",
                attestantNavn = attestantNavn ?: "-"
            ).right()
        }
        if (behandling.erAvslag()) {
            return AvslagBrevRequest(
                person,
                Avslag(
                    opprettet = Tidspunkt.now(clock),
                    avslagsgrunner = behandling.utledAvslagsgrunner(),
                    harEktefelle = behandling.behandlingsinformasjon().harEktefelle(),
                    beregning = behandling.beregning()
                ),
                saksbehandlerNavn = saksbehandlerNavn ?: "-",
                attestantNavn = attestantNavn ?: "-"
            ).right()
        }
        return KunneIkkeLageBrevutkast.KanIkkeLageBrevutkastForStatus(behandling.status()).left()
    }
}
