package dokument.domain

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.left
import dokument.domain.brev.BrevbestillingId
import dokument.domain.distribuering.KunneIkkeBestilleDistribusjon
import no.nav.su.se.bakover.common.domain.backoff.Failures
import no.nav.su.se.bakover.common.domain.backoff.shouldRetry
import no.nav.su.se.bakover.common.journal.JournalpostId
import java.time.Clock

sealed interface JournalføringOgBrevdistribusjon {
    fun journalpostId(): JournalpostId?
    fun brevbestillingsId(): BrevbestillingId?
    val distribusjonFailures: Failures
    fun incDistribusjonFailures(clock: Clock): JournalføringOgBrevdistribusjon
    fun journalfør(journalfør: () -> Either<KunneIkkeJournalføreOgDistribuereBrev.KunneIkkeJournalføre.FeilVedJournalføring, JournalpostId>): Either<KunneIkkeJournalføreOgDistribuereBrev.KunneIkkeJournalføre, Journalført> {
        return when (this) {
            is IkkeJournalførtEllerDistribuert -> {
                journalfør().map { medJournalpost(it) }
            }

            is Journalført -> {
                KunneIkkeJournalføreOgDistribuereBrev.KunneIkkeJournalføre.AlleredeJournalført(journalpostId).left()
            }

            is JournalførtOgDistribuertBrev -> {
                KunneIkkeJournalføreOgDistribuereBrev.KunneIkkeJournalføre.AlleredeJournalført(journalpostId).left()
            }
        }
    }

    fun distribuerBrev(
        clock: Clock,
        ignoreBackoff: Boolean = false,
        distribuerBrev: (journalpostId: JournalpostId) -> Either<KunneIkkeBestilleDistribusjon, BrevbestillingId>,
    ): Either<KunneIkkeDistribuereBrev, JournalførtOgDistribuertBrev> {
        return when (this) {
            is IkkeJournalførtEllerDistribuert -> {
                KunneIkkeDistribuereBrev.MåJournalføresFørst.left()
            }

            is Journalført -> {
                if (ignoreBackoff) {
                    distribuerBrev(journalpostId).map { this.medDistribuertBrev(it) }
                        .mapLeft { KunneIkkeDistribuereBrev.OppdatertFailures(this.incDistribusjonFailures(clock)) }
                } else {
                    shouldRetryDistribusjon(clock).mapLeft {
                        KunneIkkeDistribuereBrev.ForTidligÅPrøvePåNytt
                    }.flatMap {
                        distribuerBrev(journalpostId)
                            .mapLeft { KunneIkkeDistribuereBrev.OppdatertFailures(this.incDistribusjonFailures(clock)) }
                            .map { this.medDistribuertBrev(it) }
                    }
                }
            }

            is JournalførtOgDistribuertBrev -> {
                KunneIkkeDistribuereBrev.AlleredeDistribuertBrev(journalpostId, brevbestillingId).left()
            }
        }
    }

    data object IkkeJournalførtEllerDistribuert : JournalføringOgBrevdistribusjon {
        override val distribusjonFailures: Failures = Failures.EMPTY
        fun medJournalpost(journalpostId: JournalpostId): Journalført = Journalført(journalpostId, distribusjonFailures)

        override fun journalpostId(): JournalpostId? = null
        override fun brevbestillingsId(): BrevbestillingId? = null
        override fun incDistribusjonFailures(clock: Clock) =
            throw java.lang.IllegalStateException("Kan ikke inkrementere failures for JournalførtOgDistribuertBrev")
    }

    data class Journalført(
        val journalpostId: JournalpostId,
        override val distribusjonFailures: Failures,
    ) : JournalføringOgBrevdistribusjon {

        fun medDistribuertBrev(brevbestillingId: BrevbestillingId): JournalførtOgDistribuertBrev =
            JournalførtOgDistribuertBrev(journalpostId, brevbestillingId, distribusjonFailures)

        override fun journalpostId() = journalpostId
        override fun brevbestillingsId(): BrevbestillingId? = null

        fun shouldRetryDistribusjon(clock: Clock): Either<Unit, JournalføringOgBrevdistribusjon> {
            return distribusjonFailures.shouldRetry(clock).map {
                this.copy(distribusjonFailures = it)
            }
        }

        override fun incDistribusjonFailures(clock: Clock) =
            this.copy(distribusjonFailures = distribusjonFailures.inc(clock))
    }

    data class JournalførtOgDistribuertBrev(
        val journalpostId: JournalpostId,
        val brevbestillingId: BrevbestillingId,
        override val distribusjonFailures: Failures,
    ) : JournalføringOgBrevdistribusjon {
        override fun journalpostId() = journalpostId
        override fun brevbestillingsId(): BrevbestillingId = brevbestillingId
        override fun incDistribusjonFailures(clock: Clock) =
            throw java.lang.IllegalStateException("Kan ikke inkrementere failures for JournalførtOgDistribuertBrev")
    }

    companion object {
        fun fromId(
            iverksattJournalpostId: JournalpostId?,
            iverksattBrevbestillingId: BrevbestillingId?,
            distribusjonFailures: Failures,
        ): JournalføringOgBrevdistribusjon = when {
            iverksattJournalpostId == null && iverksattBrevbestillingId == null -> {
                IkkeJournalførtEllerDistribuert
            }

            iverksattJournalpostId != null && iverksattBrevbestillingId != null -> {
                JournalførtOgDistribuertBrev(
                    journalpostId = iverksattJournalpostId,
                    brevbestillingId = iverksattBrevbestillingId,
                    distribusjonFailures = distribusjonFailures,
                )
            }

            iverksattJournalpostId != null -> {
                Journalført(iverksattJournalpostId, distribusjonFailures)
            }

            else -> {
                throw IllegalStateException("Kunne ikke bestemme eksterne iverksettingssteg for innvilgelse, iverksattJournalpostId:$iverksattJournalpostId, iverksattBrevbestillingId:$iverksattBrevbestillingId")
            }
        }

        fun iverksattJournalpostId(e: JournalføringOgBrevdistribusjon): JournalpostId? =
            when (e) {
                is IkkeJournalførtEllerDistribuert -> null
                is Journalført -> e.journalpostId
                is JournalførtOgDistribuertBrev -> e.journalpostId
            }

        fun iverksattBrevbestillingId(e: JournalføringOgBrevdistribusjon): BrevbestillingId? =
            when (e) {
                is IkkeJournalførtEllerDistribuert,
                is Journalført,
                -> null

                is JournalførtOgDistribuertBrev -> e.brevbestillingId
            }
    }

    sealed interface KunneIkkeDistribuereBrev {
        data object MåJournalføresFørst : KunneIkkeDistribuereBrev
        data class AlleredeDistribuertBrev(
            val journalpostId: JournalpostId,
            val brevbestillingId: BrevbestillingId,
        ) : KunneIkkeDistribuereBrev

        data class OppdatertFailures(
            val journalføringOgBrevdistribusjon: Journalført,
        ) : KunneIkkeDistribuereBrev

        data object ForTidligÅPrøvePåNytt : KunneIkkeDistribuereBrev
    }
}

sealed interface KunneIkkeJournalføreOgDistribuereBrev {
    sealed interface KunneIkkeDistribuereBrev : KunneIkkeJournalføreOgDistribuereBrev {
        data object MåJournalføresFørst : KunneIkkeDistribuereBrev
        data class AlleredeDistribuertBrev(
            val journalpostId: JournalpostId,
            val brevbestillingId: BrevbestillingId,
        ) : KunneIkkeDistribuereBrev
        data class OppdatertFailures(
            val journalpostId: JournalpostId,
            val dokumentdistribusjon: Dokumentdistribusjon,
        ) : KunneIkkeDistribuereBrev

        data object ForTidligÅPrøvePåNytt : KunneIkkeDistribuereBrev
    }

    sealed interface KunneIkkeJournalføre : KunneIkkeJournalføreOgDistribuereBrev {
        data class AlleredeJournalført(val journalpostId: JournalpostId) : KunneIkkeJournalføre
        data object FeilVedJournalføring : KunneIkkeJournalføre
    }
}
