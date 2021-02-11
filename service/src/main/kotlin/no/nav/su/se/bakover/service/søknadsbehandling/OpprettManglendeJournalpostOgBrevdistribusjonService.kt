package no.nav.su.se.bakover.service.søknadsbehandling

import arrow.core.Either
import arrow.core.left
import no.nav.su.se.bakover.database.søknadsbehandling.SøknadsbehandlingRepo
import no.nav.su.se.bakover.domain.behandling.BehandlingMetrics
import no.nav.su.se.bakover.domain.eksterneiverksettingssteg.EksterneIverksettingsstegEtterUtbetaling
import no.nav.su.se.bakover.domain.eksterneiverksettingssteg.EksterneIverksettingsstegFeil
import no.nav.su.se.bakover.domain.eksterneiverksettingssteg.EksterneIverksettingsstegForAvslag
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.service.brev.BrevService
import java.util.UUID

internal class OpprettManglendeJournalpostOgBrevdistribusjonService(
    private val søknadsbehandlingRepo: SøknadsbehandlingRepo,
    private val brevService: BrevService,
    private val ferdigstillSøknadsbehandlingService: FerdigstillSøknadsbehandlingService,
    private val behandlingMetrics: BehandlingMetrics

) {
    fun opprettManglendeJournalpostOgBrevdistribusjon(): FerdigstillIverksettingService.OpprettManglendeJournalpostOgBrevdistribusjonResultat {
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

    fun opprettManglendeJournalposteringer(): List<Either<FerdigstillIverksettingService.KunneIkkeOppretteJournalpostForIverksetting, Søknadsbehandling.Iverksatt.Innvilget>> {
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
                            EksterneIverksettingsstegFeil.EksterneIverksettingsstegForAvslagFeil.KunneIkkeDistribuereBrev.FeilVedDistribueringAvBrev(
                                journalpostId
                            )
                        }
                    }.mapLeft {
                        when (it) {
                            is EksterneIverksettingsstegFeil.EksterneIverksettingsstegForAvslagFeil.KunneIkkeDistribuereBrev.AlleredeDistribuertBrev -> {
                                kunneIkkeBestilleBrev(
                                    søknadsbehandling.sakId,
                                    søknadsbehandling.id,
                                    it.journalpostId,
                                    it
                                )
                            }
                            is EksterneIverksettingsstegFeil.EksterneIverksettingsstegForAvslagFeil.KunneIkkeDistribuereBrev.FeilVedDistribueringAvBrev -> {
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
                            EksterneIverksettingsstegFeil.EksterneIverksettingsstegEtterUtbetalingFeil.KunneIkkeDistribuereBrev.FeilVedDistribueringAvBrev(
                                journalpostId
                            )
                        }
                    }.mapLeft {
                        when (it) {
                            is EksterneIverksettingsstegFeil.EksterneIverksettingsstegEtterUtbetalingFeil.KunneIkkeDistribuereBrev.AlleredeDistribuertBrev -> {
                                kunneIkkeBestilleBrev(
                                    søknadsbehandling.sakId,
                                    søknadsbehandling.id,
                                    it.journalpostId,
                                    it
                                )
                            }
                            is EksterneIverksettingsstegFeil.EksterneIverksettingsstegEtterUtbetalingFeil.KunneIkkeDistribuereBrev.FeilVedDistribueringAvBrev -> {
                                kunneIkkeBestilleBrev(
                                    søknadsbehandling.sakId,
                                    søknadsbehandling.id,
                                    it.journalpostId,
                                    it
                                )
                            }
                            EksterneIverksettingsstegFeil.EksterneIverksettingsstegEtterUtbetalingFeil.KunneIkkeDistribuereBrev.MåJournalføresFørst -> {
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
}
