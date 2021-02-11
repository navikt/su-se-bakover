package no.nav.su.se.bakover.service.søknadsbehandling

import arrow.core.Either
import arrow.core.left
import no.nav.su.se.bakover.client.person.MicrosoftGraphApiOppslag
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.database.RevurderingRepo
import no.nav.su.se.bakover.database.søknadsbehandling.SøknadsbehandlingRepo
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.behandling.BehandlingMetrics
import no.nav.su.se.bakover.domain.brev.LagBrevRequest
import no.nav.su.se.bakover.domain.eksterneiverksettingssteg.EksterneIverksettingsstegEtterUtbetaling
import no.nav.su.se.bakover.domain.eksterneiverksettingssteg.EksterneIverksettingsstegFeil.EksterneIverksettingsstegEtterUtbetalingFeil
import no.nav.su.se.bakover.domain.eksterneiverksettingssteg.EksterneIverksettingsstegFeil.EksterneIverksettingsstegForAvslagFeil
import no.nav.su.se.bakover.domain.eksterneiverksettingssteg.EksterneIverksettingsstegForAvslag
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.visitor.LagBrevRequestVisitor
import no.nav.su.se.bakover.domain.visitor.Visitable
import no.nav.su.se.bakover.service.brev.BrevService
import no.nav.su.se.bakover.service.oppgave.OppgaveService
import no.nav.su.se.bakover.service.person.PersonService
import no.nav.su.se.bakover.service.revurdering.FerdigstillRevurderingService
import no.nav.su.se.bakover.service.statistikk.EventObserver
import no.nav.su.se.bakover.service.søknadsbehandling.FerdigstillIverksettingService.KunneIkkeFerdigstilleInnvilgelse
import org.slf4j.LoggerFactory
import java.time.Clock
import java.util.UUID

internal class FerdigstillIverksettingServiceImpl(
    private val søknadsbehandlingRepo: SøknadsbehandlingRepo,
    private val oppgaveService: OppgaveService,
    private val behandlingMetrics: BehandlingMetrics,
    private val microsoftGraphApiClient: MicrosoftGraphApiOppslag,
    private val personService: PersonService,
    private val brevService: BrevService,
    private val clock: Clock,
    private val revurderingRepo: RevurderingRepo
) : FerdigstillIverksettingService {
    private val log = LoggerFactory.getLogger(this::class.java)

    private val observers: MutableList<EventObserver> = mutableListOf()

    private val ferdigstillSøknadsbehandlingService = FerdigstillSøknadsbehandlingService(
        søknadsbehandlingRepo,
        behandlingMetrics,
        brevService,
        this
    )

    private val ferdigstillRevurderingService = FerdigstillRevurderingService(
        brevService, revurderingRepo, this
    )

    fun addObserver(observer: EventObserver) {
        observers.add(observer)
        ferdigstillSøknadsbehandlingService.addObserver(observer)
    }

    fun getObservers(): List<EventObserver> = observers.toList()

    override fun ferdigstillIverksetting(utbetalingId: UUID30) {
        val søknadsbehandling = søknadsbehandlingRepo.hentBehandlingForUtbetaling(utbetalingId)
        val revurdering = revurderingRepo.hentRevurderingForUtbetaling(utbetalingId)

        check(listOfNotNull(søknadsbehandling, revurdering).count() == 1) {
            "Fant ingen eller mange elementer knyttet til utbetaling: $utbetalingId. Kan ikke ferdigstille iverksetting."
        }

        søknadsbehandling?.let { ferdigstillSøknadsbehandlingService.ferdigstillSøknadsbehandling(søknadsbehandling) }
        revurdering?.let { ferdigstillRevurderingService.ferdigstillRevurdering(revurdering) }
    }

    override fun opprettManglendeJournalpostOgBrevdistribusjon(): FerdigstillIverksettingService.OpprettManglendeJournalpostOgBrevdistribusjonResultat {
        return FerdigstillIverksettingService.OpprettManglendeJournalpostOgBrevdistribusjonResultat(
            journalpostresultat = opprettManglendeJournalposteringer().map {
                it.map { behandling ->
                    FerdigstillIverksettingService.OpprettetJournalpostForIverksetting(
                        sakId = behandling.sakId,
                        behandlingId = behandling.id,
                        journalpostId = behandling.eksterneIverksettingsteg.journalpostId()!! // Skal egentlig ikke være i tilstanden VenterPåKvittering
                    )
                }
            },
            brevbestillingsresultat = opprettManglendeBrevbestillinger(),
        )
    }

    private fun opprettManglendeJournalposteringer(): List<Either<FerdigstillIverksettingService.KunneIkkeOppretteJournalpostForIverksetting, Søknadsbehandling.Iverksatt.Innvilget>> {
        return søknadsbehandlingRepo.hentIverksatteBehandlingerUtenJournalposteringer().map { søknadsbehandling ->
            if (søknadsbehandling.eksterneIverksettingsteg !is EksterneIverksettingsstegEtterUtbetaling.VenterPåKvittering) {
                return@map FerdigstillIverksettingService.KunneIkkeOppretteJournalpostForIverksetting(
                    sakId = søknadsbehandling.sakId,
                    behandlingId = søknadsbehandling.id,
                    grunn = "Kunne ikke opprette journalpost for iverksetting siden den allerede eksisterer"
                ).left()
            }
            return@map ferdigstillSøknadsbehandlingService.opprettJournalpostForInnvilgelse(
                søknadsbehandling = søknadsbehandling,
            ).mapLeft {
                FerdigstillIverksettingService.KunneIkkeOppretteJournalpostForIverksetting(
                    sakId = søknadsbehandling.sakId,
                    behandlingId = søknadsbehandling.id,
                    grunn = it.javaClass.simpleName
                )
            }
        }
    }

    private fun opprettManglendeBrevbestillinger(): List<Either<FerdigstillIverksettingService.KunneIkkeBestilleBrev, FerdigstillIverksettingService.BestiltBrev>> {
        return søknadsbehandlingRepo.hentIverksatteBehandlingerUtenBrevbestillinger().map { søknadsbehandling ->
            when (søknadsbehandling) {
                is Søknadsbehandling.Iverksatt.Avslag -> {
                    søknadsbehandling.distribuerBrev { journalpostId ->
                        brevService.distribuerBrev(journalpostId).mapLeft {
                            EksterneIverksettingsstegForAvslagFeil.KunneIkkeDistribuereBrev.FeilVedDistribueringAvBrev(
                                journalpostId
                            )
                        }
                    }.mapLeft {
                        when (it) {
                            is EksterneIverksettingsstegForAvslagFeil.KunneIkkeDistribuereBrev.AlleredeDistribuertBrev -> {
                                kunneIkkeBestilleBrev(
                                    søknadsbehandling.sakId,
                                    søknadsbehandling.id,
                                    it.journalpostId,
                                    it
                                )
                            }
                            is EksterneIverksettingsstegForAvslagFeil.KunneIkkeDistribuereBrev.FeilVedDistribueringAvBrev -> {
                                kunneIkkeBestilleBrev(
                                    søknadsbehandling.sakId,
                                    søknadsbehandling.id,
                                    it.journalpostId,
                                    it
                                )
                            }
                        }
                    }.map { avslagMedJorunalpostOgDistribuertBrev ->
                        søknadsbehandlingRepo.lagre(avslagMedJorunalpostOgDistribuertBrev)
                        behandlingMetrics.incrementAvslåttCounter(BehandlingMetrics.AvslåttHandlinger.DISTRIBUERT_BREV)
                        (avslagMedJorunalpostOgDistribuertBrev.eksterneIverksettingsteg as EksterneIverksettingsstegForAvslag.JournalførtOgDistribuertBrev).let {
                            FerdigstillIverksettingService.BestiltBrev(
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
                            EksterneIverksettingsstegEtterUtbetalingFeil.KunneIkkeDistribuereBrev.FeilVedDistribueringAvBrev(
                                journalpostId
                            )
                        }
                    }.mapLeft {
                        when (it) {
                            is EksterneIverksettingsstegEtterUtbetalingFeil.KunneIkkeDistribuereBrev.AlleredeDistribuertBrev -> {
                                kunneIkkeBestilleBrev(
                                    søknadsbehandling.sakId,
                                    søknadsbehandling.id,
                                    it.journalpostId,
                                    it
                                )
                            }
                            is EksterneIverksettingsstegEtterUtbetalingFeil.KunneIkkeDistribuereBrev.FeilVedDistribueringAvBrev -> {
                                kunneIkkeBestilleBrev(
                                    søknadsbehandling.sakId,
                                    søknadsbehandling.id,
                                    it.journalpostId,
                                    it
                                )
                            }
                            EksterneIverksettingsstegEtterUtbetalingFeil.KunneIkkeDistribuereBrev.MåJournalføresFørst -> {
                                kunneIkkeBestilleBrev(søknadsbehandling.sakId, søknadsbehandling.id, null, it)
                            }
                        }
                    }.map { innvilgetMedJournalpostOgDistribuertBrev ->
                        søknadsbehandlingRepo.lagre(innvilgetMedJournalpostOgDistribuertBrev)
                        behandlingMetrics.incrementInnvilgetCounter(BehandlingMetrics.InnvilgetHandlinger.DISTRIBUERT_BREV)
                        (innvilgetMedJournalpostOgDistribuertBrev.eksterneIverksettingsteg as EksterneIverksettingsstegEtterUtbetaling.JournalførtOgDistribuertBrev).let {
                            FerdigstillIverksettingService.BestiltBrev(
                                sakId = søknadsbehandling.sakId,
                                behandlingId = søknadsbehandling.id,
                                journalpostId = it.journalpostId,
                                brevbestillingId = it.brevbestillingId,
                            )
                        }
                    }
                }
            }
        }
    }

    private fun kunneIkkeBestilleBrev(
        sakId: UUID,
        behandlingId: UUID,
        journalpostId: JournalpostId?,
        error: Any
    ) = FerdigstillIverksettingService.KunneIkkeBestilleBrev(
        sakId = sakId,
        behandlingId = behandlingId,
        journalpostId = journalpostId,
        grunn = error.javaClass.simpleName
    )

    fun lagBrevRequest(visitable: Visitable<LagBrevRequestVisitor>): Either<KunneIkkeFerdigstilleInnvilgelse, LagBrevRequest> {
        return LagBrevRequestVisitor(
            hentPerson = { fnr ->
                personService.hentPersonMedSystembruker(fnr)
                    .mapLeft { LagBrevRequestVisitor.BrevRequestFeil.KunneIkkeHentePerson }
            },
            hentNavn = { ident ->
                hentNavnForNavIdent(ident)
                    .mapLeft { LagBrevRequestVisitor.BrevRequestFeil.KunneIkkeHenteNavnForSaksbehandlerEllerAttestant }
            },
            clock = clock,
        ).let { visitor ->
            visitable.accept(visitor)
            visitor.brevRequest
                .mapLeft {
                    when (it) {
                        LagBrevRequestVisitor.BrevRequestFeil.KunneIkkeHenteNavnForSaksbehandlerEllerAttestant -> {
                            KunneIkkeFerdigstilleInnvilgelse.FikkIkkeHentetSaksbehandlerEllerAttestant
                        }
                        LagBrevRequestVisitor.BrevRequestFeil.KunneIkkeHentePerson -> {
                            KunneIkkeFerdigstilleInnvilgelse.FantIkkePerson
                        }
                    }
                }
        }
    }

    fun hentNavnForNavIdent(navIdent: NavIdentBruker): Either<KunneIkkeFerdigstilleInnvilgelse.FikkIkkeHentetSaksbehandlerEllerAttestant, String> {
        return microsoftGraphApiClient.hentBrukerinformasjonForNavIdent(navIdent)
            .mapLeft { KunneIkkeFerdigstilleInnvilgelse.FikkIkkeHentetSaksbehandlerEllerAttestant }
            .map { it.displayName }
    }

    fun lukkOppgave(
        oppgaveId: OppgaveId
    ): Either<KunneIkkeFerdigstilleInnvilgelse.KunneIkkeLukkeOppgave, Unit> {
        return oppgaveService.lukkOppgaveMedSystembruker(oppgaveId)
            .mapLeft { KunneIkkeFerdigstilleInnvilgelse.KunneIkkeLukkeOppgave }
    }
}
