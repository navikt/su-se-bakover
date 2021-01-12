package no.nav.su.se.bakover.service.behandling

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.getOrElse
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.client.person.MicrosoftGraphApiOppslag
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.database.behandling.BehandlingRepo
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Person
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.behandling.Behandling
import no.nav.su.se.bakover.domain.behandling.BehandlingMetrics
import no.nav.su.se.bakover.domain.behandling.avslag.Avslag
import no.nav.su.se.bakover.domain.behandling.avslag.AvslagBrevRequest
import no.nav.su.se.bakover.domain.brev.LagBrevRequest
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.vedtak.snapshot.Vedtakssnapshot
import no.nav.su.se.bakover.service.brev.BrevService
import no.nav.su.se.bakover.service.oppgave.OppgaveService
import no.nav.su.se.bakover.service.person.PersonService
import no.nav.su.se.bakover.service.statistikk.Event
import no.nav.su.se.bakover.service.statistikk.EventObserver
import no.nav.su.se.bakover.service.utbetaling.KunneIkkeUtbetale
import no.nav.su.se.bakover.service.utbetaling.UtbetalingService
import no.nav.su.se.bakover.service.vedtak.snapshot.OpprettVedtakssnapshotService
import org.slf4j.LoggerFactory
import java.time.Clock
import java.util.UUID

class IverksettBehandlingService(
    private val behandlingRepo: BehandlingRepo,
    private val utbetalingService: UtbetalingService,
    private val oppgaveService: OppgaveService,
    private val personService: PersonService,
    private val brevService: BrevService,
    private val opprettVedtakssnapshotService: OpprettVedtakssnapshotService,
    private val behandlingMetrics: BehandlingMetrics,
    private val clock: Clock,
    private val microsoftGraphApiClient: MicrosoftGraphApiOppslag,
) {
    private val observers: MutableList<EventObserver> = mutableListOf()

    fun addObserver(observer: EventObserver) {
        observers.add(observer)
    }

    private val log = LoggerFactory.getLogger(this::class.java)

    // TODO need to define responsibilities for domain and services.
    // TODO refactor the beast
    internal fun iverksett(
        behandlingId: UUID,
        attestant: NavIdentBruker.Attestant
    ): Either<KunneIkkeIverksetteBehandling, IverksattBehandling> {
        val behandling = behandlingRepo.hentBehandling(behandlingId)
            ?: return KunneIkkeIverksetteBehandling.FantIkkeBehandling.left()

        val person: Person = personService.hentPerson(behandling.fnr).getOrElse {
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
                }.also {
                    it.map {
                        observers.forEach { observer -> observer.handle(Event.Statistikk.BehandlingIverksatt(it)) }
                    }
                }
            }
    }

    fun opprettManglendeJournalpostOgBrevdistribusjon(): OpprettManglendeJournalpostOgBrevdistribusjonResultat {
        return OpprettManglendeJournalpostOgBrevdistribusjonResultat(
            journalpostresultat = opprettManglendeJournalposteringer(),
            brevbestillingsresultat = opprettManglendeBrevbestillinger(),
        )
    }

    private fun opprettManglendeJournalposteringer() =
        behandlingRepo.hentIverksatteBehandlingerUtenJournalposteringer().map { behandling ->
            if (behandling.iverksattJournalpostId() != null) {
                return@map KunneIkkeOppretteJournalpostForIverksetting(
                    sakId = behandling.sakId,
                    behandlingId = behandling.id,
                    grunn = "Kunne ikke opprette journalpost for iverksetting siden den allerede eksisterer"
                ).left()
            }
            if (behandling.status() != Behandling.BehandlingsStatus.IVERKSATT_INNVILGET) {
                return@map KunneIkkeOppretteJournalpostForIverksetting(
                    sakId = behandling.sakId,
                    behandlingId = behandling.id,
                    grunn = "Kunne ikke opprette journalpost for status ${behandling.status()}"
                ).left()
            }
            val saksbehandlerNavn =
                hentNavnForNavIdent(behandling.saksbehandler()!!).getOrHandle {
                    return@map KunneIkkeOppretteJournalpostForIverksetting(
                        sakId = behandling.sakId,
                        behandlingId = behandling.id,
                        grunn = "Kunne ikke hente saksbehandlers navn"
                    ).left()
                }
            val attestantNavn =
                hentNavnForNavIdent(behandling.attestering()!!.attestant).getOrHandle {
                    return@map KunneIkkeOppretteJournalpostForIverksetting(
                        sakId = behandling.sakId,
                        behandlingId = behandling.id,
                        grunn = "Kunne ikke hente attestants navn "
                    ).left()
                }
            val person = personService.hentPerson(behandling.fnr).getOrElse {
                return@map KunneIkkeOppretteJournalpostForIverksetting(
                    sakId = behandling.sakId,
                    behandlingId = behandling.id,
                    grunn = "Kunne ikke hente person"
                ).left()
            }
            return@map opprettJournalpostForInnvilgelse(
                behandling = behandling,
                person = person,
                saksbehandlerNavn = saksbehandlerNavn,
                attestantNavn = attestantNavn,
            ).mapLeft {
                KunneIkkeOppretteJournalpostForIverksetting(
                    sakId = behandling.sakId,
                    behandlingId = behandling.id,
                    grunn = "Kunne ikke opprette journalpost mot eksternt system"
                )
            }
        }

    private fun opprettManglendeBrevbestillinger(): List<Either<KunneIkkeBestilleBrev, BestiltBrev>> {
        return behandlingRepo.hentIverksatteBehandlingerUtenBrevbestillinger().map { behandling ->
            val journalpostId = behandling.iverksattJournalpostId() ?: return@map KunneIkkeBestilleBrev(
                sakId = behandling.sakId,
                behandlingId = behandling.id,
                journalpostId = null,
                grunn = "Kunne ikke opprette brevbestilling siden iverksattJournalpostId er null."
            ).left()

            if (behandling.iverksattBrevbestillingId() != null) {
                return@map KunneIkkeBestilleBrev(
                    sakId = behandling.sakId,
                    behandlingId = behandling.id,
                    journalpostId = journalpostId,
                    grunn = "Kunne ikke opprette brevbestilling siden den allerde eksisterer"
                ).left()
            }

            if (behandling.status() == Behandling.BehandlingsStatus.IVERKSATT_INNVILGET) {
                return@map distribuerBrev(
                    behandling = behandling,
                    journalpostId = journalpostId,
                ).mapLeft {
                    KunneIkkeBestilleBrev(
                        sakId = behandling.sakId,
                        behandlingId = behandling.id,
                        journalpostId = journalpostId,
                        grunn = "Kunne ikke bestille brev"
                    )
                }.map {
                    BestiltBrev(
                        sakId = behandling.sakId,
                        behandlingId = behandling.id,
                        journalpostId = journalpostId,
                        brevbestillingId = it.behandling.iverksattBrevbestillingId()!!
                    )
                }
            }

            return@map KunneIkkeBestilleBrev(
                sakId = behandling.sakId,
                behandlingId = behandling.id,
                journalpostId = journalpostId,
                grunn = "Kunne ikke bestille brev for status ${behandling.status()}"
            ).left()
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

        val journalpostId = opprettJournalpostForAvslag(
            behandling = behandling,
            person = person,
            avslag = avslag,
            attestant = attestant,
        ).getOrHandle {
            log.error("Behandling ${behandling.id} ble ikke iverksatt siden vi ikke klarte journalføre. Saksbehandleren må prøve på nytt.")
            return it.left()
        }.journalpostId

        behandlingRepo.oppdaterAttestering(behandling.id, Attestering.Iverksatt(attestant))
        behandlingRepo.oppdaterBehandlingStatus(behandling.id, behandling.status())
        log.info("Iverksatt avslag for behandling ${behandling.id} med journalpost $journalpostId")
        behandlingMetrics.incrementAvslåttCounter(BehandlingMetrics.AvslåttHandlinger.PERSISTERT)
        val brevResultat = distribuerBrev(behandling, journalpostId).map {
            behandlingMetrics.incrementAvslåttCounter(BehandlingMetrics.AvslåttHandlinger.DISTRIBUERT_BREV)
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

    private fun opprettJournalpostForAvslag(
        behandling: Behandling,
        person: Person,
        avslag: Avslag,
        attestant: NavIdentBruker,
    ): Either<KunneIkkeIverksetteBehandling, OpprettetJournalpostForIverksetting> {
        val saksbehandlerNavn = hentNavnForNavIdent(behandling.saksbehandler()!!).getOrHandle { return it.left() }
        val attestantNavn = hentNavnForNavIdent(attestant).getOrHandle { return it.left() }

        return opprettJournalpost(
            behandling,
            AvslagBrevRequest(
                person = person,
                avslag = avslag,
                saksbehandlerNavn = saksbehandlerNavn,
                attestantNavn = attestantNavn
            )
        ).map {
            behandlingMetrics.incrementAvslåttCounter(BehandlingMetrics.AvslåttHandlinger.JOURNALFØRT)
            OpprettetJournalpostForIverksetting(
                sakId = behandling.sakId,
                behandlingId = behandling.id,
                journalpostId = it
            )
        }
    }

    private fun iverksettInnvilgning(
        person: Person,
        behandling: Behandling,
        attestant: NavIdentBruker.Attestant,
        behandlingId: UUID,
    ): Either<KunneIkkeIverksetteBehandling, IverksattBehandling> {

        val saksbehandlerNavn = hentNavnForNavIdent(behandling.saksbehandler()!!).getOrHandle { return it.left() }
        val attestantNavn = hentNavnForNavIdent(attestant).getOrHandle { return it.left() }

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
                opprettJournalpostForInnvilgelse(
                    behandling = behandling,
                    person = person,
                    saksbehandlerNavn = saksbehandlerNavn,
                    attestantNavn = attestantNavn
                )
                    .mapLeft {
                        log.error("Journalføring av iverksettingsbrev feilet for behandling ${behandling.id}. Dette må gjøres manuelt.")
                        IverksattBehandling.MedMangler.KunneIkkeJournalføreBrev(behandling)
                    }
                    .flatMap {
                        log.info("Journalført iverksettingsbrev $it for behandling ${behandling.id}")
                        behandlingMetrics.incrementInnvilgetCounter(BehandlingMetrics.InnvilgetHandlinger.JOURNALFØRT)
                        brevService.distribuerBrev(it.journalpostId)
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

    private fun hentNavnForNavIdent(navIdent: NavIdentBruker): Either<KunneIkkeIverksetteBehandling.FikkIkkeHentetSaksbehandlerEllerAttestant, String> {
        return microsoftGraphApiClient.hentBrukerinformasjonForNavIdent(navIdent)
            .mapLeft { KunneIkkeIverksetteBehandling.FikkIkkeHentetSaksbehandlerEllerAttestant }
            .map { it.displayName }
    }

    private fun opprettJournalpostForInnvilgelse(
        behandling: Behandling,
        person: Person,
        saksbehandlerNavn: String,
        attestantNavn: String,
    ): Either<IverksattBehandling.MedMangler, OpprettetJournalpostForIverksetting> {

        return opprettJournalpost(
            behandling,
            LagBrevRequest.InnvilgetVedtak(
                person = person,
                behandling = behandling,
                saksbehandlerNavn = saksbehandlerNavn,
                attestantNavn = attestantNavn
            )
        ).mapLeft {
            IverksattBehandling.MedMangler.KunneIkkeJournalføreBrev(behandling)
        }.map {
            OpprettetJournalpostForIverksetting(
                sakId = behandling.sakId,
                behandlingId = behandling.id,
                journalpostId = it
            )
        }
    }

    private fun opprettJournalpost(
        behandling: Behandling,
        lagBrevRequest: LagBrevRequest,
    ): Either<KunneIkkeIverksetteBehandling.KunneIkkeJournalføreBrev, JournalpostId> {
        val journalpostId = brevService.journalførBrev(
            lagBrevRequest,
            behandling.saksnummer,
        ).map {
            behandling.oppdaterIverksattJournalpostId(it)
            behandlingRepo.oppdaterIverksattJournalpostId(behandling.id, it)
            it
        }.getOrElse {
            return KunneIkkeIverksetteBehandling.KunneIkkeJournalføreBrev.left()
        }
        return journalpostId.right()
    }

    private fun distribuerBrev(
        behandling: Behandling,
        journalpostId: JournalpostId
    ): Either<IverksattBehandling.MedMangler.KunneIkkeDistribuereBrev, IverksattBehandling.UtenMangler> {
        return brevService.distribuerBrev(journalpostId)
            .mapLeft {
                log.error("Kunne ikke bestille brev ved iverksetting for behandling ${behandling.id}. Dette må gjøres manuelt.")
                IverksattBehandling.MedMangler.KunneIkkeDistribuereBrev(behandling)
            }
            .map {
                behandling.oppdaterIverksattBrevbestillingId(it)
                behandlingRepo.oppdaterIverksattBrevbestillingId(behandling.id, it)
                log.info("Bestilt iverksettingsbrev for behandling ${behandling.id} med bestillingsid $it")
                IverksattBehandling.UtenMangler(behandling)
            }
    }
}
