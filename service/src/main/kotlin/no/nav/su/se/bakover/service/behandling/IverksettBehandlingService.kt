package no.nav.su.se.bakover.service.behandling

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.getOrElse
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.client.person.MicrosoftGraphApiOppslag
import no.nav.su.se.bakover.client.person.MicrosoftGraphApiOppslagFeil
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

        val attestantNavn = hentNavnForNavIdent(attestant)
            .getOrHandle { return KunneIkkeIverksetteBehandling.FikkIkkeHentetSaksbehandlerEllerAttestant.left() }
        val saksbehandlerNavn = hentNavnForNavIdent(behandling.saksbehandler()!!)
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

    private fun hentNavnForNavIdent(navIdent: NavIdentBruker): Either<MicrosoftGraphApiOppslagFeil, String> {
        return microsoftGraphApiClient.hentBrukerinformasjonForNavIdent(navIdent)
            .map { it.displayName }
    }

    private fun iverksettInnvilgning(
        person: Person,
        behandling: Behandling,
        attestant: NavIdentBruker.Attestant,
        behandlingId: UUID,
    ): Either<KunneIkkeIverksetteBehandling, IverksattBehandling> {

        val attestantNavn = hentNavnForNavIdent(attestant)
            .getOrHandle { return KunneIkkeIverksetteBehandling.FikkIkkeHentetSaksbehandlerEllerAttestant.left() }
        val saksbehandlerNavn = hentNavnForNavIdent(behandling.saksbehandler()!!)
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

    fun opprettManglendeJournalpostOgBrevdistribusjon(): OpprettManglendeJournalpostOgBrevdistribusjonResultat {
        return OpprettManglendeJournalpostOgBrevdistribusjonResultat(
            journalpostresultat = listOf(),
            brevbestillingsresultat = listOf()
        )
    }
}
