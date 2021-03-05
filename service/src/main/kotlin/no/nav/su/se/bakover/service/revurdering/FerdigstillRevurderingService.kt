package no.nav.su.se.bakover.service.revurdering

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.right
import no.nav.su.se.bakover.database.revurdering.RevurderingRepo
import no.nav.su.se.bakover.database.vedtak.VedtakRepo
import no.nav.su.se.bakover.domain.brev.LagBrevRequest
import no.nav.su.se.bakover.domain.eksterneiverksettingssteg.JournalføringOgBrevdistribusjon
import no.nav.su.se.bakover.domain.eksterneiverksettingssteg.JournalføringOgBrevdistribusjonFeil.KunneIkkeDistribuereBrev
import no.nav.su.se.bakover.domain.eksterneiverksettingssteg.JournalføringOgBrevdistribusjonFeil.KunneIkkeDistribuereBrev.AlleredeDistribuertBrev
import no.nav.su.se.bakover.domain.eksterneiverksettingssteg.JournalføringOgBrevdistribusjonFeil.KunneIkkeDistribuereBrev.FeilVedDistribueringAvBrev
import no.nav.su.se.bakover.domain.eksterneiverksettingssteg.JournalføringOgBrevdistribusjonFeil.KunneIkkeDistribuereBrev.MåJournalføresFørst
import no.nav.su.se.bakover.domain.eksterneiverksettingssteg.JournalføringOgBrevdistribusjonFeil.KunneIkkeJournalføre
import no.nav.su.se.bakover.domain.eksterneiverksettingssteg.JournalføringOgBrevdistribusjonFeil.KunneIkkeJournalføre.AlleredeJournalført
import no.nav.su.se.bakover.domain.eksterneiverksettingssteg.JournalføringOgBrevdistribusjonFeil.KunneIkkeJournalføre.FeilVedJournalføring
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.revurdering.IverksattRevurdering
import no.nav.su.se.bakover.domain.revurdering.Revurdering
import no.nav.su.se.bakover.service.brev.BrevService
import no.nav.su.se.bakover.service.søknadsbehandling.FerdigstillIverksettingService
import org.slf4j.LoggerFactory

internal class FerdigstillRevurderingService(
    private val brevService: BrevService,
    private val revurderingRepo: RevurderingRepo,
    private val ferdigstillIverksettingService: FerdigstillIverksettingService,
    private val vedtakRepo: VedtakRepo
) {
    private val log = LoggerFactory.getLogger(this::class.java)

    fun ferdigstill(revurdering: IverksattRevurdering) {
        ferdigstillIverksettingService.lagBrevRequest(revurdering)
            .mapLeft { throw KunneIkkeLageBrevRequestException(revurdering, it) }
            .flatMap { brevRequest ->
                journalfør(revurdering, brevRequest)
            }
            .flatMap { journalførtRevurdering ->
                when (val steg = journalførtRevurdering.eksterneIverksettingsteg) {
                    is JournalføringOgBrevdistribusjon.Journalført -> vedtakRepo.oppdaterJournalpostForRevurdering(
                        revurderingId = journalførtRevurdering.id,
                        journalpostId = steg.journalpostId
                    )
                    else -> {
                    }
                }
                revurderingRepo.lagre(journalførtRevurdering)
                distribuerBrev(journalførtRevurdering)
            }
            .map { journalførtOgDistribuert ->
                when (val steg = journalførtOgDistribuert.eksterneIverksettingsteg) {
                    is JournalføringOgBrevdistribusjon.JournalførtOgDistribuertBrev -> vedtakRepo.oppdaterBrevbestillingIdForRevurdering(
                        revurderingId = journalførtOgDistribuert.id,
                        brevbestillingId = steg.brevbestillingId
                    )
                    else -> {
                    }
                }
                revurderingRepo.lagre(journalførtOgDistribuert)
                lukkOppgave(journalførtOgDistribuert.oppgaveId)
            }
    }

    private fun journalfør(
        revurdering: IverksattRevurdering,
        brevRequest: LagBrevRequest
    ): Either<KunneIkkeJournalføre, IverksattRevurdering> {
        return revurdering.journalfør {
            brevService.journalførBrev(brevRequest, revurdering.saksnummer)
                .mapLeft { FeilVedJournalføring }
        }.mapLeft {
            return when (it) {
                is AlleredeJournalført -> revurdering.right()
                FeilVedJournalføring -> throw KunneIkkeJournalføreBrevException(revurdering, it)
            }
        }
    }

    private fun distribuerBrev(revurdering: IverksattRevurdering): Either<FerdigstillIverksettingService.KunneIkkeFerdigstilleInnvilgelse.KunneIkkeDistribuereBrev, IverksattRevurdering> {
        return revurdering.distribuerBrev { journalpostId ->
            brevService.distribuerBrev(journalpostId)
                .mapLeft { FeilVedDistribueringAvBrev(journalpostId) }
        }.mapLeft {
            return when (it) {
                is AlleredeDistribuertBrev -> revurdering.right()
                is FeilVedDistribueringAvBrev -> throw KunneIkkeDistribuereBrevException(revurdering, it)
                MåJournalføresFørst -> throw KunneIkkeDistribuereBrevException(revurdering, it)
            }
        }
    }

    private fun lukkOppgave(oppgaveId: OppgaveId) = ferdigstillIverksettingService.lukkOppgave(oppgaveId).mapLeft {
        log.error("Kunne ikke lukke oppgave $oppgaveId ved innvilgelse av revurdering. Dette må gjøres manuelt.")
    }.map {
        log.info("Lukket oppgave $oppgaveId ved innvilgelse av revurdering.")
    }

    internal data class KunneIkkeLageBrevRequestException(
        private val revurdering: Revurdering,
        private val error: FerdigstillIverksettingService.KunneIkkeFerdigstilleInnvilgelse,
        val msg: String = "Kunne ikke opprette brevrequest for revurdering: ${revurdering.id}. Original feil: ${error::class.qualifiedName}"
    ) : RuntimeException(msg)

    internal data class KunneIkkeJournalføreBrevException(
        private val revurdering: Revurdering,
        private val error: KunneIkkeJournalføre,
        val msg: String = "Kunne ikke journalføre brev for revurdering: ${revurdering.id}. Original feil: ${error::class.qualifiedName}"
    ) : RuntimeException(msg)

    internal data class KunneIkkeDistribuereBrevException(
        private val revurdering: Revurdering,
        private val error: KunneIkkeDistribuereBrev,
        val msg: String = "Kunne ikke distribuere brev for revurdering: ${revurdering.id}. Original feil: ${error::class.qualifiedName}"
    ) : RuntimeException(msg)
}
