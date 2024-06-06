package dokument.domain

import arrow.core.Either
import dokument.domain.brev.BrevbestillingId
import dokument.domain.brev.KunneIkkeDistribuereBrev
import dokument.domain.distribuering.KunneIkkeBestilleDistribusjon
import no.nav.su.se.bakover.common.domain.backoff.Failures
import no.nav.su.se.bakover.common.journal.JournalpostId
import no.nav.su.se.bakover.common.tid.Tidspunkt
import java.time.Clock
import java.util.UUID

/**
 * Representerer tilstanden i prosessen for journalføringen og distribusjonen av et [Dokument].
 */
data class Dokumentdistribusjon(
    val id: UUID = UUID.randomUUID(),
    val opprettet: Tidspunkt,
    val endret: Tidspunkt = opprettet,
    val dokument: Dokument.MedMetadata,
    val journalføringOgBrevdistribusjon: JournalføringOgBrevdistribusjon,
) {

    val distribusjonFailures: Failures
        get() = journalføringOgBrevdistribusjon.distribusjonFailures

    fun journalfør(journalfør: () -> Either<KunneIkkeJournalføreOgDistribuereBrev.KunneIkkeJournalføre.FeilVedJournalføring, JournalpostId>): Either<KunneIkkeJournalføreOgDistribuereBrev.KunneIkkeJournalføre, Dokumentdistribusjon> {
        return journalføringOgBrevdistribusjon.journalfør(journalfør)
            .map { copy(journalføringOgBrevdistribusjon = it) }
    }

    /**
     * @param ignoreBackoff I noen tilfeller ønsker vi ikke å ta hensyn til backoff-strategien. F.eks. dersom saksbehandler ønsker overstyre distribusjonsadresse.
     */
    fun distribuerBrev(
        clock: Clock,
        ignoreBackoff: Boolean = false,
        distribuerBrev: (journalpostId: JournalpostId) -> Either<KunneIkkeBestilleDistribusjon, BrevbestillingId>,
    ): Either<KunneIkkeJournalføreOgDistribuereBrev.KunneIkkeDistribuereBrev, Dokumentdistribusjon> {
        return journalføringOgBrevdistribusjon.distribuerBrev(clock, ignoreBackoff, distribuerBrev)
            .mapLeft {
                when (it) {
                    is JournalføringOgBrevdistribusjon.KunneIkkeDistribuereBrev.ForTidligÅPrøvePåNytt -> KunneIkkeJournalføreOgDistribuereBrev.KunneIkkeDistribuereBrev.ForTidligÅPrøvePåNytt
                    is JournalføringOgBrevdistribusjon.KunneIkkeDistribuereBrev.OppdatertFailures -> KunneIkkeJournalføreOgDistribuereBrev.KunneIkkeDistribuereBrev.OppdatertFailures(
                        journalpostId = it.journalføringOgBrevdistribusjon.journalpostId,
                        dokumentdistribusjon = this.copy(
                            journalføringOgBrevdistribusjon = it.journalføringOgBrevdistribusjon,
                        ),
                    )

                    is JournalføringOgBrevdistribusjon.KunneIkkeDistribuereBrev.MåJournalføresFørst -> KunneIkkeJournalføreOgDistribuereBrev.KunneIkkeDistribuereBrev.MåJournalføresFørst
                    is JournalføringOgBrevdistribusjon.KunneIkkeDistribuereBrev.AlleredeDistribuertBrev -> KunneIkkeJournalføreOgDistribuereBrev.KunneIkkeDistribuereBrev.AlleredeDistribuertBrev(
                        it.journalpostId,
                        it.brevbestillingId,
                    )
                }
            }
            .map { copy(journalføringOgBrevdistribusjon = it) }
    }
}
