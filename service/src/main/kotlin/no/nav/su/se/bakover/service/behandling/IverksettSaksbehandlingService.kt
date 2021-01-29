package no.nav.su.se.bakover.service.behandling

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.getOrElse
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.client.person.MicrosoftGraphApiOppslag
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.database.SaksbehandlingRepo
import no.nav.su.se.bakover.database.behandling.BehandlingRepo
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Person
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.behandling.Behandling
import no.nav.su.se.bakover.domain.behandling.BehandlingMetrics
import no.nav.su.se.bakover.domain.behandling.Statusovergang
import no.nav.su.se.bakover.domain.behandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.behandling.avslag.Avslag
import no.nav.su.se.bakover.domain.behandling.avslag.AvslagBrevRequest
import no.nav.su.se.bakover.domain.vedtak.snapshot.Vedtakssnapshot
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

    internal fun iverksettInnvilgning(
        søknadsbehandling: Søknadsbehandling.TilAttestering.Innvilget,
        attestant: NavIdentBruker.Attestant
    ): Either<Statusovergang.KunneIkkeIverksetteSøknadsbehandling.KunneIkkeUtbetale, UUID30> {
        return utbetalingService.utbetal(
            sakId = søknadsbehandling.sakId,
            attestant = attestant,
            beregning = søknadsbehandling.beregning,
            simulering = søknadsbehandling.simulering
        ).mapLeft {
            log.error("Kunne ikke innvilge behandling ${søknadsbehandling.id} siden utbetaling feilet. Feiltype: $it")
            when (it) {
                KunneIkkeUtbetale.SimuleringHarBlittEndretSidenSaksbehandlerSimulerte -> Statusovergang.KunneIkkeIverksetteSøknadsbehandling.KunneIkkeUtbetale.SimuleringHarBlittEndretSidenSaksbehandlerSimulerte
                KunneIkkeUtbetale.Protokollfeil -> Statusovergang.KunneIkkeIverksetteSøknadsbehandling.KunneIkkeUtbetale.TekniskFeil
                KunneIkkeUtbetale.KunneIkkeSimulere -> Statusovergang.KunneIkkeIverksetteSøknadsbehandling.KunneIkkeUtbetale.KunneIkkeKontrollsimulere
            }
        }.map { oversendtUtbetaling ->
            // TODO fix fix
            // opprettVedtakssnapshotService.opprettVedtak(
            //     vedtakssnapshot = Vedtakssnapshot.Innvilgelse.createFromBehandling(søknadsbehandling, oversendtUtbetaling)
            // )
            // behandlingMetrics.incrementInnvilgetCounter(BehandlingMetrics.InnvilgetHandlinger.PERSISTERT)

            log.info("Behandling ${søknadsbehandling.id} innvilget med utbetaling ${oversendtUtbetaling.id}")

            oversendtUtbetaling.id
        }
    }

    internal fun iverksettAvslag(
        behandling: Søknadsbehandling.Iverksatt.Avslag
    ): Either<KunneIkkeIverksetteBehandling, Søknadsbehandling.Iverksatt> {
        val person: Person = personService.hentPerson(behandling.fnr).getOrElse {
            log.error("Kunne ikke iverksette behandling med id:${behandling.id}; fant ikke person")
            return Statusovergang.KunneIkkeIverksetteSøknadsbehandling.FantIkkePerson.left()
        }

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
        val brevResultat = distribuerIverksettingsbrevService.distribuerBrev(behandling) {
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

        return brevResultat
            .mapLeft {
                IverksattBehandling.MedMangler.KunneIkkeDistribuereBrev(behandling)
            }
            .flatMap { oppgaveResultat }.fold(
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

        return journalførIverksettingService.opprettJournalpost(
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

    private fun hentNavnForNavIdent(navIdent: NavIdentBruker): Either<KunneIkkeIverksetteBehandling.FikkIkkeHentetSaksbehandlerEllerAttestant, String> {
        return microsoftGraphApiClient.hentBrukerinformasjonForNavIdent(navIdent)
            .mapLeft { KunneIkkeIverksetteBehandling.FikkIkkeHentetSaksbehandlerEllerAttestant }
            .map { it.displayName }
    }
}
