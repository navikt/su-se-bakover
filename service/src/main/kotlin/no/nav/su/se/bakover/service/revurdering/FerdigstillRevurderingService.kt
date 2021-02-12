package no.nav.su.se.bakover.service.revurdering

import arrow.core.Either
import no.nav.su.se.bakover.database.revurdering.RevurderingRepo
import no.nav.su.se.bakover.domain.brev.LagBrevRequest
import no.nav.su.se.bakover.domain.eksterneiverksettingssteg.EksterneIverksettingsstegFeil
import no.nav.su.se.bakover.domain.revurdering.IverksattRevurdering
import no.nav.su.se.bakover.domain.revurdering.Revurdering
import no.nav.su.se.bakover.service.brev.BrevService
import no.nav.su.se.bakover.service.søknadsbehandling.FerdigstillIverksettingService
import no.nav.su.se.bakover.service.søknadsbehandling.FerdigstillIverksettingServiceImpl
import org.slf4j.LoggerFactory

internal class FerdigstillRevurderingService(
    private val brevService: BrevService,
    private val revurderingRepo: RevurderingRepo,
    private val ferdigstillIverksettingService: FerdigstillIverksettingServiceImpl
) {
    private val log = LoggerFactory.getLogger(this::class.java)

    fun ferdigstill(revurdering: IverksattRevurdering) {
        ferdigstillIverksettingService.lagBrevRequest(revurdering)
            .mapLeft { throw KunneIkkeLageBrevRequestException(revurdering, it) }
            .map { brevRequest ->
                journalfør(revurdering, brevRequest)
                    .mapLeft { throw KunneIkkeJournalføreBrevException(revurdering, it) }
                    .map { journalførtRevurdering ->
                        revurderingRepo.lagre(journalførtRevurdering)
                        distribuerBrev(journalførtRevurdering)
                            .mapLeft { throw KunneIkkeDistribuereBrevException(revurdering, it) }
                            .map { journalførtOgDistribuert ->
                                revurderingRepo.lagre(journalførtOgDistribuert)
                                ferdigstillIverksettingService.lukkOppgave(journalførtOgDistribuert.oppgaveId)
                            }
                    }
            }
    }

    private fun journalfør(
        revurdering: IverksattRevurdering,
        brevRequest: LagBrevRequest
    ): Either<EksterneIverksettingsstegFeil.EksterneIverksettingsstegEtterUtbetalingFeil.KunneIkkeJournalføre, IverksattRevurdering> {
        return revurdering.journalfør {
            brevService.journalførBrev(brevRequest, revurdering.tilRevurdering.saksnummer)
                .mapLeft { EksterneIverksettingsstegFeil.EksterneIverksettingsstegEtterUtbetalingFeil.KunneIkkeJournalføre.FeilVedJournalføring }
        }
    }

    private fun distribuerBrev(revurdering: IverksattRevurdering): Either<FerdigstillIverksettingService.KunneIkkeFerdigstilleInnvilgelse.KunneIkkeDistribuereBrev, IverksattRevurdering> {
        return revurdering.distribuerBrev { journalpostId ->
            brevService.distribuerBrev(journalpostId).mapLeft {
                EksterneIverksettingsstegFeil.EksterneIverksettingsstegEtterUtbetalingFeil.KunneIkkeDistribuereBrev.FeilVedDistribueringAvBrev(
                    journalpostId
                )
            }
        }.mapLeft {
            FerdigstillIverksettingService.KunneIkkeFerdigstilleInnvilgelse.KunneIkkeDistribuereBrev
        }
    }

    internal data class KunneIkkeLageBrevRequestException(
        private val revurdering: Revurdering,
        private val error: FerdigstillIverksettingService.KunneIkkeFerdigstilleInnvilgelse,
        val msg: String = "Kunne ikke opprette brevrequest for revurdering: ${revurdering.id}. Original feil: ${error::class.qualifiedName}"
    ) : RuntimeException(msg)

    internal data class KunneIkkeJournalføreBrevException(
        private val revurdering: Revurdering,
        private val error: EksterneIverksettingsstegFeil.EksterneIverksettingsstegEtterUtbetalingFeil.KunneIkkeJournalføre,
        val msg: String = "Kunne ikke journalføre brev for revurdering: ${revurdering.id}. Original feil: ${error::class.qualifiedName}"
    ) : RuntimeException(msg)

    internal data class KunneIkkeDistribuereBrevException(
        private val revurdering: Revurdering,
        private val error: FerdigstillIverksettingService.KunneIkkeFerdigstilleInnvilgelse.KunneIkkeDistribuereBrev,
        val msg: String = "Kunne ikke distribuere brev for revurdering: ${revurdering.id}. Original feil: ${error::class.qualifiedName}"
    ) : RuntimeException(msg)
}
