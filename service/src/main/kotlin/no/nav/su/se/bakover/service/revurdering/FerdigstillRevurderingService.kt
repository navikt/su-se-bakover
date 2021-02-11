package no.nav.su.se.bakover.service.revurdering

import arrow.core.Either
import no.nav.su.se.bakover.database.RevurderingRepo
import no.nav.su.se.bakover.domain.brev.LagBrevRequest
import no.nav.su.se.bakover.domain.eksterneiverksettingssteg.EksterneIverksettingsstegFeil
import no.nav.su.se.bakover.domain.revurdering.IverksattRevurdering
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

    fun ferdigstillRevurdering(revurdering: IverksattRevurdering) {
        ferdigstillIverksettingService.lagBrevRequest(revurdering)
            .mapLeft { throw RuntimeException("") }
            .map { brevRequest ->
                journalførRevurdering(revurdering, brevRequest)
                    .mapLeft { throw RuntimeException("") }
                    .map { journalførtRevurdering ->
                        revurderingRepo.lagre(journalførtRevurdering)
                        distribuerBrevForRevurdering(journalførtRevurdering)
                            .mapLeft { throw RuntimeException("") }
                            .map { journalførtOgDistribuert ->
                                revurderingRepo.lagre(journalførtOgDistribuert)
                                ferdigstillIverksettingService.lukkOppgave(
                                    journalførtOgDistribuert.oppgaveId
                                )
                            }
                    }
            }
    }

    private fun journalførRevurdering(
        revurdering: IverksattRevurdering,
        brevRequest: LagBrevRequest
    ): Either<EksterneIverksettingsstegFeil.EksterneIverksettingsstegEtterUtbetalingFeil.KunneIkkeJournalføre, IverksattRevurdering> {
        return revurdering.journalfør {
            brevService.journalførBrev(brevRequest, revurdering.tilRevurdering.saksnummer)
                .mapLeft { EksterneIverksettingsstegFeil.EksterneIverksettingsstegEtterUtbetalingFeil.KunneIkkeJournalføre.FeilVedJournalføring }
        }
    }

    private fun distribuerBrevForRevurdering(revurdering: IverksattRevurdering): Either<FerdigstillIverksettingService.KunneIkkeFerdigstilleInnvilgelse.KunneIkkeDistribuereBrev, IverksattRevurdering> {
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
}
