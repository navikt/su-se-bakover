package no.nav.su.se.bakover.service.søknadsbehandling

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.database.søknadsbehandling.SøknadsbehandlingRepo
import no.nav.su.se.bakover.domain.behandling.BehandlingMetrics
import no.nav.su.se.bakover.domain.eksterneiverksettingssteg.EksterneIverksettingsstegFeil
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.service.brev.BrevService
import no.nav.su.se.bakover.service.statistikk.Event
import no.nav.su.se.bakover.service.statistikk.EventObserver
import org.slf4j.LoggerFactory

internal class FerdigstillSøknadsbehandlingService(
    private val søknadsbehandlingRepo: SøknadsbehandlingRepo,
    private val behandlingMetrics: BehandlingMetrics,
    private val brevService: BrevService,
    private val ferdigstillIverksettingService: FerdigstillIverksettingServiceImpl,
) {
    private val log = LoggerFactory.getLogger(this::class.java)
    private val observers: MutableList<EventObserver> = mutableListOf()

    fun addObserver(observer: EventObserver) {
        observers.add(observer)
    }

    fun getObservers(): List<EventObserver> = observers.toList()

    fun ferdigstillSøknadsbehandling(søknadsbehandling: Søknadsbehandling.Iverksatt.Innvilget) {
        observers.forEach { observer ->
            observer.handle(Event.Statistikk.SøknadsbehandlingStatistikk.SøknadsbehandlingIverksatt(søknadsbehandling))
        }
        opprettJournalpostOgBrevbestillingForInnvilgelse(søknadsbehandling)

        lukkOppgave(søknadsbehandling)
    }

    private fun lukkOppgave(søknadsbehandling: Søknadsbehandling): Either<FerdigstillIverksettingService.KunneIkkeFerdigstilleInnvilgelse, Unit> {
        return ferdigstillIverksettingService.lukkOppgave(søknadsbehandling.oppgaveId)
            .mapLeft {
                log.error("Kunne ikke lukke oppgave ved innvilgelse for behandling ${søknadsbehandling.id}. Dette må gjøres manuelt.")
                return FerdigstillIverksettingService.KunneIkkeFerdigstilleInnvilgelse.KunneIkkeLukkeOppgave.left()
            }.map {
                log.info("Lukket oppgave ${søknadsbehandling.oppgaveId} ved innvilgelse for behandling ${søknadsbehandling.id}")
                // TODO jah: Vurder behandling.oppdaterOppgaveId(null), men den kan ikke være null atm.
                behandlingMetrics.incrementInnvilgetCounter(BehandlingMetrics.InnvilgetHandlinger.LUKKET_OPPGAVE)
            }
    }

    private fun opprettJournalpostOgBrevbestillingForInnvilgelse(
        søknadsbehandling: Søknadsbehandling.Iverksatt.Innvilget
    ): Either<FerdigstillIverksettingService.KunneIkkeFerdigstilleInnvilgelse, Unit> {

        return opprettJournalpostForInnvilgelse(søknadsbehandling)
            .flatMap {
                it.distribuerBrev { journalpostId ->
                    brevService.distribuerBrev(journalpostId).mapLeft {
                        EksterneIverksettingsstegFeil.EksterneIverksettingsstegEtterUtbetalingFeil.KunneIkkeDistribuereBrev.FeilVedDistribueringAvBrev(
                            journalpostId
                        )
                    }
                }.map {
                    søknadsbehandlingRepo.lagre(it)
                    behandlingMetrics.incrementInnvilgetCounter(BehandlingMetrics.InnvilgetHandlinger.DISTRIBUERT_BREV)
                }
                    .mapLeft { FerdigstillIverksettingService.KunneIkkeFerdigstilleInnvilgelse.KunneIkkeDistribuereBrev }
            }
    }

    fun opprettJournalpostForInnvilgelse(
        søknadsbehandling: Søknadsbehandling.Iverksatt.Innvilget,
    ): Either<FerdigstillIverksettingService.KunneIkkeFerdigstilleInnvilgelse, Søknadsbehandling.Iverksatt.Innvilget> {
        val brevRequest = ferdigstillIverksettingService.lagBrevRequest(søknadsbehandling).getOrHandle {
            return it.left()
        }

        return søknadsbehandling.journalfør {
            brevService.journalførBrev(brevRequest, søknadsbehandling.saksnummer)
                .mapLeft { EksterneIverksettingsstegFeil.EksterneIverksettingsstegEtterUtbetalingFeil.KunneIkkeJournalføre.FeilVedJournalføring }
        }.mapLeft {
            when (it) {
                is EksterneIverksettingsstegFeil.EksterneIverksettingsstegEtterUtbetalingFeil.KunneIkkeJournalføre.AlleredeJournalført -> {
                    log.info("Behandlingen er allerede journalført med journalpostId ${it.journalpostId}")
                    return søknadsbehandling.right()
                }
                is EksterneIverksettingsstegFeil.EksterneIverksettingsstegEtterUtbetalingFeil.KunneIkkeJournalføre.FeilVedJournalføring -> {
                    log.error("Journalføring av iverksettingsbrev feilet for behandling ${søknadsbehandling.id}.")
                    FerdigstillIverksettingService.KunneIkkeFerdigstilleInnvilgelse.KunneIkkeOppretteJournalpost
                }
            }
        }.map {
            søknadsbehandlingRepo.lagre(it)
            log.info("Journalført iverksettingsbrev $it for behandling ${søknadsbehandling.id}")
            behandlingMetrics.incrementInnvilgetCounter(BehandlingMetrics.InnvilgetHandlinger.JOURNALFØRT)
            it
        }
    }
}
