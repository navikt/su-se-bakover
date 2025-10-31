package no.nav.su.se.bakover.domain.eksterneiverksettingssteg

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import dokument.domain.JournalføringOgBrevdistribusjon
import dokument.domain.JournalføringOgBrevdistribusjon.IkkeJournalførtEllerDistribuert
import dokument.domain.JournalføringOgBrevdistribusjon.Journalført
import dokument.domain.JournalføringOgBrevdistribusjon.JournalførtOgDistribuertBrev
import dokument.domain.KunneIkkeJournalføreOgDistribuereBrev.KunneIkkeJournalføre.AlleredeJournalført
import dokument.domain.KunneIkkeJournalføreOgDistribuereBrev.KunneIkkeJournalføre.FeilVedJournalføring
import dokument.domain.brev.BrevbestillingId
import dokument.domain.distribuering.KunneIkkeBestilleDistribusjon
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.domain.backoff.Failures
import no.nav.su.se.bakover.common.journal.JournalpostId
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.test.fixedClock
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

internal class JournalføringOgBrevdistribusjonTest {
    @Nested
    inner class Journalføring {

        @Test
        fun `journalfører venter på kvittering og oppdaterer status`() {
            IkkeJournalførtEllerDistribuert.journalfør { okJournalføring() } shouldBe Journalført(
                journalpostId,
                Failures.EMPTY,
            ).right()
        }

        @Test
        fun `svarer med feil dersom journalføring feiler`() {
            IkkeJournalførtEllerDistribuert.journalfør { errorJournalføring() } shouldBe FeilVedJournalføring.left()
        }

        @Test
        fun `svarer med feil dersom man forsøker å journalføre en allerede journalført`() {
            Journalført(
                journalpostId,
                Failures.EMPTY,
            ).journalfør { throw IllegalStateException("Skal ikke komme hit") } shouldBe AlleredeJournalført(
                journalpostId,
            ).left()
        }

        @Test
        fun `svarer med feil dersom man forsøker å journalføre en allerede distribuert og journalført`() {
            JournalførtOgDistribuertBrev(
                journalpostId,
                brevbestillingId,
                Failures.EMPTY,
            ).journalfør { throw IllegalStateException("Skal ikke komme hit") } shouldBe AlleredeJournalført(journalpostId).left()
        }
    }

    @Nested
    inner class BrevDistribusjon {

        @Test
        fun `skal ikke kunne distribuere brev når vi ikke har journalført`() {
            IkkeJournalførtEllerDistribuert.distribuerBrev(fixedClock) {
                throw IllegalStateException("Skal ikke komme hit")
            } shouldBe JournalføringOgBrevdistribusjon.KunneIkkeDistribuereBrev.MåJournalføresFørst.left()
        }

        @Test
        fun `distribuerer brev og oppdaterer status for journalført`() {
            Journalført(
                journalpostId,
                Failures.EMPTY,
            ).distribuerBrev(fixedClock) { id -> okBrevDistribusjon(id) } shouldBe JournalførtOgDistribuertBrev(
                journalpostId,
                brevbestillingId,
                Failures.EMPTY,
            ).right()
        }

        @Test
        fun `skal ikke kunne distribuere brev når vi allerede har distribuert`() {
            JournalførtOgDistribuertBrev(
                journalpostId,
                brevbestillingId,
                Failures.EMPTY,
            ).distribuerBrev(fixedClock) { throw IllegalStateException("Skal ikke komme hit") } shouldBe JournalføringOgBrevdistribusjon.KunneIkkeDistribuereBrev.AlleredeDistribuertBrev(
                journalpostId,
                brevbestillingId,
            ).left()
        }

        @Test
        fun `svarer med feil hvis distribuering feiler`() {
            val journalført = Journalført(
                journalpostId,
                Failures.EMPTY,
            )
            journalført.distribuerBrev(fixedClock) {
                KunneIkkeBestilleDistribusjon.left()
            } shouldBe JournalføringOgBrevdistribusjon.KunneIkkeDistribuereBrev.OppdatertFailures(
                journalført.copy(
                    distribusjonFailures = Failures(
                        count = 1,
                        last = Tidspunkt.now(fixedClock),
                    ),
                ),
            ).left()
        }
    }

    private val journalpostId = JournalpostId("jp")
    private val brevbestillingId = BrevbestillingId("bid")

    private fun okJournalføring() = journalpostId.right()
    private fun okBrevDistribusjon(@Suppress("UNUSED_PARAMETER") journalpostId: JournalpostId): Either<Nothing, BrevbestillingId> {
        return brevbestillingId.right()
    }

    private fun errorJournalføring() = FeilVedJournalføring.left()
}
