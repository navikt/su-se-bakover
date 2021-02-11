package no.nav.su.se.bakover.domain.eksterneiverksettingssteg

import arrow.core.Either
import arrow.core.left
import no.nav.su.se.bakover.domain.brev.BrevbestillingId
import no.nav.su.se.bakover.domain.eksterneiverksettingssteg.EksterneIverksettingsstegFeil.EksterneIverksettingsstegEtterUtbetalingFeil
import no.nav.su.se.bakover.domain.eksterneiverksettingssteg.EksterneIverksettingsstegFeil.EksterneIverksettingsstegForAvslagFeil
import no.nav.su.se.bakover.domain.journal.JournalpostId

sealed class EksterneIverksettingsstegEtterUtbetaling {
    abstract fun journalpostId(): JournalpostId?
    fun journalfør(journalfør: () -> Either<EksterneIverksettingsstegEtterUtbetalingFeil.KunneIkkeJournalføre.FeilVedJournalføring, JournalpostId>): Either<EksterneIverksettingsstegEtterUtbetalingFeil.KunneIkkeJournalføre, Journalført> {
        return when (this) {
            is VenterPåKvittering -> {
                journalfør().map { medJournalpost(it) }
            }
            is Journalført -> {
                EksterneIverksettingsstegEtterUtbetalingFeil.KunneIkkeJournalføre.AlleredeJournalført(journalpostId)
                    .left()
            }
            is JournalførtOgDistribuertBrev -> {
                EksterneIverksettingsstegEtterUtbetalingFeil.KunneIkkeJournalføre.AlleredeJournalført(journalpostId)
                    .left()
            }
        }
    }

    fun distribuerBrev(distribuerBrev: (journalpostId: JournalpostId) -> Either<EksterneIverksettingsstegEtterUtbetalingFeil.KunneIkkeDistribuereBrev, BrevbestillingId>): Either<EksterneIverksettingsstegEtterUtbetalingFeil.KunneIkkeDistribuereBrev, JournalførtOgDistribuertBrev> {
        return when (this) {
            is VenterPåKvittering -> EksterneIverksettingsstegEtterUtbetalingFeil.KunneIkkeDistribuereBrev.MåJournalføresFørst.left()
            is Journalført -> distribuerBrev(journalpostId).map { this.medDistribuertBrev(it) }
            is JournalførtOgDistribuertBrev -> EksterneIverksettingsstegEtterUtbetalingFeil.KunneIkkeDistribuereBrev.AlleredeDistribuertBrev(
                journalpostId
            ).left()
        }
    }

    object VenterPåKvittering : EksterneIverksettingsstegEtterUtbetaling() {
        fun medJournalpost(journalpostId: JournalpostId): Journalført = Journalført(journalpostId)

        override fun journalpostId(): JournalpostId? = null
    }

    data class Journalført(val journalpostId: JournalpostId) : EksterneIverksettingsstegEtterUtbetaling() {
        fun medDistribuertBrev(brevbestillingId: BrevbestillingId): JournalførtOgDistribuertBrev =
            JournalførtOgDistribuertBrev(journalpostId, brevbestillingId)

        override fun journalpostId() = journalpostId
    }

    data class JournalførtOgDistribuertBrev(
        val journalpostId: JournalpostId,
        val brevbestillingId: BrevbestillingId
    ) : EksterneIverksettingsstegEtterUtbetaling() {
        override fun journalpostId() = journalpostId
    }
}

sealed class EksterneIverksettingsstegForAvslag {
    abstract val journalpostId: JournalpostId

    fun distribuerBrev(distribuerBrev: (journalpostId: JournalpostId) -> Either<EksterneIverksettingsstegForAvslagFeil.KunneIkkeDistribuereBrev, BrevbestillingId>): Either<EksterneIverksettingsstegForAvslagFeil.KunneIkkeDistribuereBrev, JournalførtOgDistribuertBrev> =
        when (this) {
            is Journalført -> distribuerBrev(journalpostId).map { this.medDistribuertBrev(it) }
            is JournalførtOgDistribuertBrev -> EksterneIverksettingsstegForAvslagFeil.KunneIkkeDistribuereBrev.AlleredeDistribuertBrev(
                journalpostId
            ).left()
        }

    data class Journalført(override val journalpostId: JournalpostId) : EksterneIverksettingsstegForAvslag() {
        fun medDistribuertBrev(brevbestillingId: BrevbestillingId): JournalførtOgDistribuertBrev =
            JournalførtOgDistribuertBrev(journalpostId, brevbestillingId)
    }

    data class JournalførtOgDistribuertBrev(
        override val journalpostId: JournalpostId,
        val brevbestillingId: BrevbestillingId
    ) : EksterneIverksettingsstegForAvslag()
}

sealed class EksterneIverksettingsstegFeil {
    sealed class EksterneIverksettingsstegEtterUtbetalingFeil : EksterneIverksettingsstegFeil() {
        sealed class KunneIkkeDistribuereBrev : EksterneIverksettingsstegEtterUtbetalingFeil() {
            object MåJournalføresFørst : KunneIkkeDistribuereBrev()
            data class AlleredeDistribuertBrev(val journalpostId: JournalpostId) : KunneIkkeDistribuereBrev()
            data class FeilVedDistribueringAvBrev(val journalpostId: JournalpostId) : KunneIkkeDistribuereBrev()
        }

        sealed class KunneIkkeJournalføre : EksterneIverksettingsstegEtterUtbetalingFeil() {
            data class AlleredeJournalført(val journalpostId: JournalpostId) : KunneIkkeJournalføre()
            object FeilVedJournalføring : KunneIkkeJournalføre()
        }
    }

    sealed class EksterneIverksettingsstegForAvslagFeil : EksterneIverksettingsstegFeil() {
        sealed class KunneIkkeDistribuereBrev : EksterneIverksettingsstegForAvslagFeil() {
            data class AlleredeDistribuertBrev(val journalpostId: JournalpostId) : KunneIkkeDistribuereBrev()
            data class FeilVedDistribueringAvBrev(val journalpostId: JournalpostId) : KunneIkkeDistribuereBrev()
        }

        sealed class KunneIkkeJournalføre : EksterneIverksettingsstegForAvslagFeil() {
            data class AlleredeJournalført(val journalpostId: JournalpostId) : KunneIkkeJournalføre()
            object FeilVedJournalføring : KunneIkkeJournalføre()
        }
    }
}
