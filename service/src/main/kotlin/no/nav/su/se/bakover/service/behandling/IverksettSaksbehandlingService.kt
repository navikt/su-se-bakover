package no.nav.su.se.bakover.service.behandling

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.right
import no.nav.su.se.bakover.client.person.MicrosoftGraphApiOppslag
import no.nav.su.se.bakover.database.SaksbehandlingRepo
import no.nav.su.se.bakover.database.behandling.BehandlingRepo
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.behandling.BehandlingMetrics
import no.nav.su.se.bakover.domain.behandling.Saksbehandling
import no.nav.su.se.bakover.service.AttesterRequest
import no.nav.su.se.bakover.service.oppgave.OppgaveService
import no.nav.su.se.bakover.service.person.PersonService
import no.nav.su.se.bakover.service.statistikk.EventObserver
import no.nav.su.se.bakover.service.utbetaling.KunneIkkeUtbetale
import no.nav.su.se.bakover.service.utbetaling.UtbetalingService
import no.nav.su.se.bakover.service.vedtak.snapshot.OpprettVedtakssnapshotService
import org.slf4j.LoggerFactory
import java.time.Clock

class IverksettSaksbehandlingService(
    private val behandlingRepo: BehandlingRepo,
    private val utbetalingService: UtbetalingService,
    private val oppgaveService: OppgaveService,
    private val personService: PersonService,
    private val opprettVedtakssnapshotService: OpprettVedtakssnapshotService,
    private val behandlingMetrics: BehandlingMetrics,
    private val clock: Clock,
    private val microsoftGraphApiClient: MicrosoftGraphApiOppslag,
    private val journalførIverksettingService: JournalførIverksettingService,
    private val distribuerIverksettingsbrevService: DistribuerIverksettingsbrevService,
    private val saksbehandlingRepo: SaksbehandlingRepo
) {
    private val observers: MutableList<EventObserver> = mutableListOf()

    fun addObserver(observer: EventObserver) {
        observers.add(observer)
    }

    private val log = LoggerFactory.getLogger(this::class.java)

    internal fun iverksett(request: AttesterRequest.Søknadsbehandling.Iverksett): Either<KunneIkkeIverksetteBehandling, Saksbehandling> {
        val behandlingId = request.behandlingId
        val attestant = request.attestant

        val behandling = saksbehandlingRepo.hent(behandlingId)

        // val person: Person = personService.hentPerson(behandling.fnr).getOrElse {
        //     log.error("Kunne ikke iverksette behandling; fant ikke person")
        //     return KunneIkkeIverksetteBehandling.FantIkkePerson.left()
        // }
        return when (behandling) {
            is Saksbehandling.Søknadsbehandling.TilAttestering.Innvilget -> {
                iverksettInnvilgning(
                    behandling = behandling,
                    attestant = attestant
                )
                saksbehandlingRepo.hent(behandling.id).right()
            }
            else -> throw NotImplementedError()
        }
        //
        // return behandling.iverksett(attestant) // invoke first to perform state-check
        //     .mapLeft {
        //         KunneIkkeIverksetteBehandling.AttestantOgSaksbehandlerKanIkkeVæreSammePerson
        //     }
        //     .map { iverksattBehandling ->
        //         return when (iverksattBehandling.status()) {
        //             Behandling.BehandlingsStatus.IVERKSATT_AVSLAG -> iverksettAvslag(
        //                 person = person,
        //                 behandling = iverksattBehandling,
        //                 attestant = attestant,
        //             )
        //             Behandling.BehandlingsStatus.IVERKSATT_INNVILGET -> iverksettInnvilgning(
        //                 behandling = iverksattBehandling,
        //                 attestant = attestant
        //             ).map {
        //                 IverksattBehandling.UtenMangler(behandling)
        //             }
        //             else -> throw Behandling.TilstandException(
        //                 state = iverksattBehandling.status(),
        //                 operation = iverksattBehandling::iverksett.toString()
        //             )
        //         }.also {
        //             it.map {
        //                 observers.forEach { observer -> observer.handle(Event.Statistikk.BehandlingIverksatt(it)) }
        //             }
        //         }
        //     }
    }

    // private fun iverksettAvslag(
    //     person: Person,
    //     behandling: Saksbehandling.Søknadsbehandling.,
    //     attestant: NavIdentBruker.Attestant
    // ): Either<KunneIkkeIverksetteBehandling, IverksattBehandling> {
    //
    //     val avslag = Avslag(
    //         opprettet = Tidspunkt.now(clock),
    //         avslagsgrunner = behandling.utledAvslagsgrunner(),
    //         harEktefelle = behandling.behandlingsinformasjon().harEktefelle(),
    //         beregning = behandling.beregning()
    //     )
    //
    //     val journalpostId = opprettJournalpostForAvslag(
    //         behandling = behandling,
    //         person = person,
    //         avslag = avslag,
    //         attestant = attestant,
    //     ).getOrHandle {
    //         log.error("Behandling ${behandling.id} ble ikke iverksatt siden vi ikke klarte journalføre. Saksbehandleren må prøve på nytt.")
    //         return it.left()
    //     }.journalpostId
    //
    //     behandlingRepo.oppdaterAttestering(behandling.id, Attestering.Iverksatt(attestant))
    //     behandlingRepo.oppdaterBehandlingStatus(behandling.id, behandling.status())
    //     log.info("Iverksatt avslag for behandling ${behandling.id} med journalpost $journalpostId")
    //     behandlingMetrics.incrementAvslåttCounter(BehandlingMetrics.AvslåttHandlinger.PERSISTERT)
    //     val brevResultat = distribuerIverksettingsbrevService.distribuerBrev(behandling) {
    //         behandlingMetrics.incrementAvslåttCounter(BehandlingMetrics.AvslåttHandlinger.DISTRIBUERT_BREV)
    //     }
    //
    //     val oppgaveResultat = oppgaveService.lukkOppgave(behandling.oppgaveId())
    //         .mapLeft {
    //             log.error("Kunne ikke lukke oppgave ved avslag for behandling ${behandling.id}. Dette må gjøres manuelt.")
    //             IverksattBehandling.MedMangler.KunneIkkeLukkeOppgave(behandling)
    //         }
    //         .map {
    //             log.info("Lukket oppgave ${behandling.oppgaveId()} ved avslag for behandling ${behandling.id}")
    //             // TODO jah: Vurder behandling.oppdaterOppgaveId(null), men den kan ikke være null atm.
    //             behandlingMetrics.incrementAvslåttCounter(BehandlingMetrics.AvslåttHandlinger.LUKKET_OPPGAVE)
    //             IverksattBehandling.UtenMangler(behandling)
    //         }
    //
    //     opprettVedtakssnapshotService.opprettVedtak(
    //         vedtakssnapshot = Vedtakssnapshot.Avslag.createFromBehandling(behandling, avslag.avslagsgrunner)
    //     )
    //
    //     return brevResultat
    //         .mapLeft {
    //             IverksattBehandling.MedMangler.KunneIkkeDistribuereBrev(behandling)
    //         }
    //         .flatMap { oppgaveResultat }.fold(
    //             { it.right() },
    //             { it.right() }
    //         )
    // }
    //
    // private fun opprettJournalpostForAvslag(
    //     behandling: Behandling,
    //     person: Person,
    //     avslag: Avslag,
    //     attestant: NavIdentBruker,
    // ): Either<KunneIkkeIverksetteBehandling, OpprettetJournalpostForIverksetting> {
    //     val saksbehandlerNavn = hentNavnForNavIdent(behandling.saksbehandler()!!).getOrHandle { return it.left() }
    //     val attestantNavn = hentNavnForNavIdent(attestant).getOrHandle { return it.left() }
    //
    //     return journalførIverksettingService.opprettJournalpost(
    //         behandling,
    //         AvslagBrevRequest(
    //             person = person,
    //             avslag = avslag,
    //             saksbehandlerNavn = saksbehandlerNavn,
    //             attestantNavn = attestantNavn
    //         )
    //     ).map {
    //         behandlingMetrics.incrementAvslåttCounter(BehandlingMetrics.AvslåttHandlinger.JOURNALFØRT)
    //         OpprettetJournalpostForIverksetting(
    //             sakId = behandling.sakId,
    //             behandlingId = behandling.id,
    //             journalpostId = it
    //         )
    //     }
    // }

    private fun iverksettInnvilgning(
        behandling: Saksbehandling.Søknadsbehandling.TilAttestering.Innvilget,
        attestant: NavIdentBruker.Attestant
    ): Either<KunneIkkeIverksetteBehandling, Unit> {
        return utbetalingService.utbetal(
            sakId = behandling.sakId,
            attestant = attestant,
            beregning = behandling.beregning,
            simulering = behandling.simulering
        ).mapLeft {
            log.error("Kunne ikke innvilge behandling ${behandling.id} siden utbetaling feilet. Feiltype: $it")
            when (it) {
                KunneIkkeUtbetale.SimuleringHarBlittEndretSidenSaksbehandlerSimulerte -> KunneIkkeIverksetteBehandling.SimuleringHarBlittEndretSidenSaksbehandlerSimulerte
                KunneIkkeUtbetale.Protokollfeil -> KunneIkkeIverksetteBehandling.KunneIkkeUtbetale
                KunneIkkeUtbetale.KunneIkkeSimulere -> KunneIkkeIverksetteBehandling.KunneIkkeKontrollsimulere
            }
        }.flatMap { oversendtUtbetaling ->
            saksbehandlingRepo.lagre(
                Saksbehandling.Søknadsbehandling.Attestert.Iverksatt.OversendtOppdrag(
                    id = behandling.id,
                    opprettet = behandling.opprettet,
                    sakId = behandling.sakId,
                    søknad = behandling.søknad,
                    oppgaveId = behandling.oppgaveId,
                    behandlingsinformasjon = behandling.behandlingsinformasjon,
                    beregning = behandling.beregning,
                    simulering = behandling.simulering,
                    saksbehandler = behandling.saksbehandler,
                    fnr = behandling.fnr,
                    attestering = Attestering.Iverksatt(attestant),
                    utbetaling = oversendtUtbetaling
                )
            )

            // TODO VEDTAKSSNAPSHOT
            // opprettVedtakssnapshotService.opprettVedtak(
            //     vedtakssnapshot = Vedtakssnapshot.Innvilgelse.createFromBehandling(behandling, oversendtUtbetaling)
            // )
            log.info("Behandling ${behandling.id} innvilget med utbetaling ${oversendtUtbetaling.id}")
            // TODO metrics behandlingMetrics.incrementInnvilgetCounter(BehandlingMetrics.InnvilgetHandlinger.PERSISTERT)

            return Unit.right()
        }
    }

    private fun hentNavnForNavIdent(navIdent: NavIdentBruker): Either<KunneIkkeIverksetteBehandling.FikkIkkeHentetSaksbehandlerEllerAttestant, String> {
        return microsoftGraphApiClient.hentBrukerinformasjonForNavIdent(navIdent)
            .mapLeft { KunneIkkeIverksetteBehandling.FikkIkkeHentetSaksbehandlerEllerAttestant }
            .map { it.displayName }
    }
}
