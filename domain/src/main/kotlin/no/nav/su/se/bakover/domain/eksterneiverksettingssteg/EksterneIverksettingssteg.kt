package no.nav.su.se.bakover.domain.eksterneiverksettingssteg

import arrow.core.Either
import arrow.core.left
import no.nav.su.se.bakover.domain.brev.BrevbestillingId
import no.nav.su.se.bakover.domain.eksterneiverksettingssteg.JournalføringOgBrevdistribusjon.IkkeJournalførtEllerDistribuert.medJournalpost
import no.nav.su.se.bakover.domain.journal.JournalpostId

sealed class JournalføringOgBrevdistribusjon {
    abstract fun journalpostId(): JournalpostId?
    fun journalfør(journalfør: () -> Either<JournalføringOgBrevdistribusjonFeil.KunneIkkeJournalføre.FeilVedJournalføring, JournalpostId>): Either<JournalføringOgBrevdistribusjonFeil.KunneIkkeJournalføre, Journalført> {
        return when (this) {
            is IkkeJournalførtEllerDistribuert -> {
                journalfør().map { medJournalpost(it) }
            }
            is Journalført -> {
                JournalføringOgBrevdistribusjonFeil.KunneIkkeJournalføre.AlleredeJournalført(journalpostId).left()
            }
            is JournalførtOgDistribuertBrev -> {
                JournalføringOgBrevdistribusjonFeil.KunneIkkeJournalføre.AlleredeJournalført(journalpostId).left()
            }
        }
    }

    fun distribuerBrev(distribuerBrev: (journalpostId: JournalpostId) -> Either<JournalføringOgBrevdistribusjonFeil.KunneIkkeDistribuereBrev.FeilVedDistribueringAvBrev, BrevbestillingId>): Either<JournalføringOgBrevdistribusjonFeil.KunneIkkeDistribuereBrev, JournalførtOgDistribuertBrev> {
        return when (this) {
            is IkkeJournalførtEllerDistribuert -> {
                JournalføringOgBrevdistribusjonFeil.KunneIkkeDistribuereBrev.MåJournalføresFørst.left()
            }
            is Journalført -> {
                distribuerBrev(journalpostId).map { this.medDistribuertBrev(it) }
            }
            is JournalførtOgDistribuertBrev -> {
                JournalføringOgBrevdistribusjonFeil.KunneIkkeDistribuereBrev.AlleredeDistribuertBrev(journalpostId).left()
            }
        }
    }

    object IkkeJournalførtEllerDistribuert : JournalføringOgBrevdistribusjon() {
        fun medJournalpost(journalpostId: JournalpostId): Journalført = Journalført(journalpostId)

        override fun journalpostId(): JournalpostId? = null
    }

    data class Journalført(val journalpostId: JournalpostId) : JournalføringOgBrevdistribusjon() {
        fun medDistribuertBrev(brevbestillingId: BrevbestillingId): JournalførtOgDistribuertBrev =
            JournalførtOgDistribuertBrev(journalpostId, brevbestillingId)

        override fun journalpostId() = journalpostId
    }

    data class JournalførtOgDistribuertBrev(
        val journalpostId: JournalpostId,
        val brevbestillingId: BrevbestillingId
    ) : JournalføringOgBrevdistribusjon() {
        override fun journalpostId() = journalpostId
    }
}

sealed class JournalføringOgBrevdistribusjonFeil {
    sealed class KunneIkkeDistribuereBrev : JournalføringOgBrevdistribusjonFeil() {
        object MåJournalføresFørst : KunneIkkeDistribuereBrev()
        data class AlleredeDistribuertBrev(val journalpostId: JournalpostId) : KunneIkkeDistribuereBrev()
        data class FeilVedDistribueringAvBrev(val journalpostId: JournalpostId) : KunneIkkeDistribuereBrev()
    }

    sealed class KunneIkkeJournalføre : JournalføringOgBrevdistribusjonFeil() {
        data class AlleredeJournalført(val journalpostId: JournalpostId) : KunneIkkeJournalføre()
        object FeilVedJournalføring : KunneIkkeJournalføre()
    }
}
