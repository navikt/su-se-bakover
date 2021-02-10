package no.nav.su.se.bakover.service.søknadsbehandling

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.client.person.MicrosoftGraphApiOppslag
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.database.søknadsbehandling.SøknadsbehandlingRepo
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.behandling.BehandlingMetrics
import no.nav.su.se.bakover.domain.søknadsbehandling.LagBrevRequestVisitor
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.service.brev.BrevService
import no.nav.su.se.bakover.service.oppgave.OppgaveService
import no.nav.su.se.bakover.service.person.PersonService
import no.nav.su.se.bakover.service.statistikk.Event
import no.nav.su.se.bakover.service.statistikk.EventObserver
import org.slf4j.LoggerFactory
import java.time.Clock

internal class FerdigstillSøknadsbehandingIverksettingServiceImpl(
    private val søknadsbehandlingRepo: SøknadsbehandlingRepo,
    private val oppgaveService: OppgaveService,
    private val behandlingMetrics: BehandlingMetrics,
    private val microsoftGraphApiClient: MicrosoftGraphApiOppslag,
    private val personService: PersonService,
    private val brevService: BrevService,
    private val clock: Clock,
) : FerdigstillSøknadsbehandingIverksettingService {
    private val log = LoggerFactory.getLogger(this::class.java)

    private val observers: MutableList<EventObserver> = mutableListOf()

    fun addObserver(observer: EventObserver) {
        observers.add(observer)
    }

    fun getObservers(): List<EventObserver> = observers.toList()

    override fun hentBehandlingForUtbetaling(utbetalingId: UUID30): Søknadsbehandling.Iverksatt.Innvilget? {
        return søknadsbehandlingRepo.hentBehandlingForUtbetaling(utbetalingId)
    }

    override fun ferdigstillInnvilgelse(
        søknadsbehandling: Søknadsbehandling.Iverksatt.Innvilget
    ) {
        observers.forEach { observer ->
            observer.handle(Event.Statistikk.SøknadsbehandlingStatistikk.SøknadsbehandlingIverksatt(søknadsbehandling))
        }
        opprettJournalpostOgBrevbestillingForInnvilgelse(søknadsbehandling)
        lukkOppgave(søknadsbehandling)
    }

    private fun lukkOppgave(søknadsbehandling: Søknadsbehandling): Either<FerdigstillSøknadsbehandingIverksettingService.KunneIkkeFerdigstilleInnvilgelse, Unit> {
        return oppgaveService.lukkOppgaveMedSystembruker(søknadsbehandling.oppgaveId)
            .mapLeft {
                log.error("Kunne ikke lukke oppgave ved innvilgelse for behandling ${søknadsbehandling.id}. Dette må gjøres manuelt.")
                return FerdigstillSøknadsbehandingIverksettingService.KunneIkkeFerdigstilleInnvilgelse.KunneIkkeOppretteOppgave.left()
            }
            .map {
                log.info("Lukket oppgave ${søknadsbehandling.oppgaveId} ved innvilgelse for behandling ${søknadsbehandling.id}")
                // TODO jah: Vurder behandling.oppdaterOppgaveId(null), men den kan ikke være null atm.
                behandlingMetrics.incrementInnvilgetCounter(BehandlingMetrics.InnvilgetHandlinger.LUKKET_OPPGAVE)
            }
    }

    override fun opprettManglendeJournalpostOgBrevdistribusjon(): FerdigstillSøknadsbehandingIverksettingService.OpprettManglendeJournalpostOgBrevdistribusjonResultat {
        return FerdigstillSøknadsbehandingIverksettingService.OpprettManglendeJournalpostOgBrevdistribusjonResultat(
            journalpostresultat = opprettManglendeJournalposteringer().map {
                it.map { behandling ->
                    FerdigstillSøknadsbehandingIverksettingService.OpprettetJournalpostForIverksetting(
                        sakId = behandling.sakId,
                        behandlingId = behandling.id,
                        journalpostId = behandling.eksterneIverksettingsteg.journalpostId()!! // Skal egentlig ikke være i tilstanden VenterPåKvittering
                    )
                }
            },
            brevbestillingsresultat = opprettManglendeBrevbestillinger(),
        )
    }

    private fun opprettManglendeJournalposteringer(): List<Either<FerdigstillSøknadsbehandingIverksettingService.KunneIkkeOppretteJournalpostForIverksetting, Søknadsbehandling.Iverksatt.Innvilget>> {
        return søknadsbehandlingRepo.hentIverksatteBehandlingerUtenJournalposteringer().map { søknadsbehandling ->

            if (søknadsbehandling.eksterneIverksettingsteg !is Søknadsbehandling.Iverksatt.Innvilget.EksterneIverksettingsteg.VenterPåKvittering) {
                return@map FerdigstillSøknadsbehandingIverksettingService.KunneIkkeOppretteJournalpostForIverksetting(
                    sakId = søknadsbehandling.sakId,
                    behandlingId = søknadsbehandling.id,
                    grunn = "Kunne ikke opprette journalpost for iverksetting siden den allerede eksisterer"
                ).left()
            }
            return@map opprettJournalpostForInnvilgelse(
                søknadsbehandling = søknadsbehandling,
            ).mapLeft {
                FerdigstillSøknadsbehandingIverksettingService.KunneIkkeOppretteJournalpostForIverksetting(
                    sakId = søknadsbehandling.sakId,
                    behandlingId = søknadsbehandling.id,
                    grunn = it.javaClass.simpleName
                )
            }
        }
    }

    private fun opprettManglendeBrevbestillinger(): List<Either<FerdigstillSøknadsbehandingIverksettingService.KunneIkkeBestilleBrev, FerdigstillSøknadsbehandingIverksettingService.BestiltBrev>> {
        return søknadsbehandlingRepo.hentIverksatteBehandlingerUtenBrevbestillinger().map { søknadsbehandling ->
            when (søknadsbehandling) {
                is Søknadsbehandling.Iverksatt.Avslag -> {
                    søknadsbehandling.distribuerBrev { journalpostId ->
                        brevService.distribuerBrev(journalpostId).mapLeft {
                            Søknadsbehandling.Iverksatt.KunneIkkeDistribuereBrev.FeilVedDistribueringAvBrev(
                                journalpostId
                            )
                        }
                    }.map { avslagMedJorunalpostOgDistribuertBrev ->
                        søknadsbehandlingRepo.lagre(avslagMedJorunalpostOgDistribuertBrev)
                        behandlingMetrics.incrementAvslåttCounter(BehandlingMetrics.AvslåttHandlinger.DISTRIBUERT_BREV)
                        (avslagMedJorunalpostOgDistribuertBrev.eksterneIverksettingsteg as Søknadsbehandling.Iverksatt.Avslag.EksterneIverksettingsteg.JournalførtOgDistribuertBrev).let {
                            FerdigstillSøknadsbehandingIverksettingService.BestiltBrev(
                                sakId = søknadsbehandling.sakId,
                                behandlingId = søknadsbehandling.id,
                                journalpostId = it.journalpostId,
                                brevbestillingId = it.brevbestillingId,
                            )
                        }
                    }
                }
                is Søknadsbehandling.Iverksatt.Innvilget -> {
                    søknadsbehandling.distribuerBrev { journalpostId ->
                        brevService.distribuerBrev(journalpostId).mapLeft {
                            Søknadsbehandling.Iverksatt.KunneIkkeDistribuereBrev.FeilVedDistribueringAvBrev(
                                journalpostId
                            )
                        }
                    }.map { innvilgetMedJournalpostOgDistribuertBrev ->
                        søknadsbehandlingRepo.lagre(innvilgetMedJournalpostOgDistribuertBrev)
                        behandlingMetrics.incrementInnvilgetCounter(BehandlingMetrics.InnvilgetHandlinger.DISTRIBUERT_BREV)
                        (innvilgetMedJournalpostOgDistribuertBrev.eksterneIverksettingsteg as Søknadsbehandling.Iverksatt.Innvilget.EksterneIverksettingsteg.JournalførtOgDistribuertBrev).let {
                            FerdigstillSøknadsbehandingIverksettingService.BestiltBrev(
                                sakId = søknadsbehandling.sakId,
                                behandlingId = søknadsbehandling.id,
                                journalpostId = it.journalpostId,
                                brevbestillingId = it.brevbestillingId,
                            )
                        }
                    }
                }
            }.mapLeft {
                when (it) {
                    is Søknadsbehandling.Iverksatt.KunneIkkeDistribuereBrev.AlleredeDistribuertBrev -> FerdigstillSøknadsbehandingIverksettingService.KunneIkkeBestilleBrev(
                        sakId = søknadsbehandling.sakId,
                        behandlingId = søknadsbehandling.id,
                        journalpostId = it.journalpostId,
                        grunn = it.javaClass.simpleName
                    )
                    is Søknadsbehandling.Iverksatt.KunneIkkeDistribuereBrev.FeilVedDistribueringAvBrev -> FerdigstillSøknadsbehandingIverksettingService.KunneIkkeBestilleBrev(
                        sakId = søknadsbehandling.sakId,
                        behandlingId = søknadsbehandling.id,
                        journalpostId = it.journalpostId,
                        grunn = it.javaClass.simpleName
                    )
                    is Søknadsbehandling.Iverksatt.KunneIkkeDistribuereBrev.MåJournalføresFørst -> FerdigstillSøknadsbehandingIverksettingService.KunneIkkeBestilleBrev(
                        sakId = søknadsbehandling.sakId,
                        behandlingId = søknadsbehandling.id,
                        journalpostId = null,
                        grunn = it.javaClass.simpleName
                    )
                }
            }
        }
    }

    private fun opprettJournalpostOgBrevbestillingForInnvilgelse(
        søknadsbehandling: Søknadsbehandling.Iverksatt.Innvilget
    ): Either<FerdigstillSøknadsbehandingIverksettingService.KunneIkkeFerdigstilleInnvilgelse, Unit> {

        return opprettJournalpostForInnvilgelse(søknadsbehandling)
            .flatMap {
                it.distribuerBrev { journalpostId ->
                    brevService.distribuerBrev(journalpostId).mapLeft {
                        Søknadsbehandling.Iverksatt.KunneIkkeDistribuereBrev.FeilVedDistribueringAvBrev(
                            journalpostId
                        )
                    }
                }.map {
                    søknadsbehandlingRepo.lagre(it)
                    behandlingMetrics.incrementInnvilgetCounter(BehandlingMetrics.InnvilgetHandlinger.DISTRIBUERT_BREV)
                }
                    .mapLeft { FerdigstillSøknadsbehandingIverksettingService.KunneIkkeFerdigstilleInnvilgelse.KunneIkkeDistribuereBrev }
            }
    }

    private fun opprettJournalpostForInnvilgelse(
        søknadsbehandling: Søknadsbehandling.Iverksatt.Innvilget,
    ): Either<FerdigstillSøknadsbehandingIverksettingService.KunneIkkeFerdigstilleInnvilgelse, Søknadsbehandling.Iverksatt.Innvilget> {
        val visitor = LagBrevRequestVisitor(
            hentPerson = { fnr ->
                personService.hentPersonMedSystembruker(fnr)
                    .mapLeft { LagBrevRequestVisitor.BrevRequestFeil.KunneIkkeHentePerson }
            },
            hentNavn = { ident ->
                hentNavnForNavIdent(ident)
                    .mapLeft { LagBrevRequestVisitor.BrevRequestFeil.KunneIkkeHenteNavnForSaksbehandlerEllerAttestant }
            },
            clock = clock,
        ).apply { søknadsbehandling.accept(this) }

        val brevRequest = visitor.brevRequest.getOrHandle {
            return when (it) {
                LagBrevRequestVisitor.BrevRequestFeil.KunneIkkeHenteNavnForSaksbehandlerEllerAttestant -> {
                    FerdigstillSøknadsbehandingIverksettingService.KunneIkkeFerdigstilleInnvilgelse.FikkIkkeHentetSaksbehandlerEllerAttestant.left()
                }
                LagBrevRequestVisitor.BrevRequestFeil.KunneIkkeHentePerson -> {
                    FerdigstillSøknadsbehandingIverksettingService.KunneIkkeFerdigstilleInnvilgelse.FantIkkePerson.left()
                }
            }
        }

        return søknadsbehandling.journalfør {
            brevService.journalførBrev(brevRequest, søknadsbehandling.saksnummer)
                .mapLeft { Søknadsbehandling.Iverksatt.KunneIkkeJournalføre.FeilVedJournalføring }
        }.mapLeft {
            when (it) {
                is Søknadsbehandling.Iverksatt.KunneIkkeJournalføre.AlleredeJournalført -> {
                    log.info("Behandlingen er allerede journalført med journalpostId ${it.journalpostId}")
                    return søknadsbehandling.right()
                }
                is Søknadsbehandling.Iverksatt.KunneIkkeJournalføre.FeilVedJournalføring -> {
                    log.error("Journalføring av iverksettingsbrev feilet for behandling ${søknadsbehandling.id}.")
                    FerdigstillSøknadsbehandingIverksettingService.KunneIkkeFerdigstilleInnvilgelse.KunneIkkeOppretteJournalpost
                }
            }
        }.map {
            søknadsbehandlingRepo.lagre(it)
            log.info("Journalført iverksettingsbrev $it for behandling ${søknadsbehandling.id}")
            behandlingMetrics.incrementInnvilgetCounter(BehandlingMetrics.InnvilgetHandlinger.JOURNALFØRT)
            it
        }
    }

    private fun hentNavnForNavIdent(navIdent: NavIdentBruker): Either<FerdigstillSøknadsbehandingIverksettingService.KunneIkkeFerdigstilleInnvilgelse.FikkIkkeHentetSaksbehandlerEllerAttestant, String> {
        return microsoftGraphApiClient.hentBrukerinformasjonForNavIdent(navIdent)
            .mapLeft { FerdigstillSøknadsbehandingIverksettingService.KunneIkkeFerdigstilleInnvilgelse.FikkIkkeHentetSaksbehandlerEllerAttestant }
            .map { it.displayName }
    }
}
