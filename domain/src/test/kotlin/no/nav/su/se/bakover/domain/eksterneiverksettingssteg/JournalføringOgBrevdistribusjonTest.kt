package no.nav.su.se.bakover.domain.eksterneiverksettingssteg

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.application.journal.JournalpostId
import no.nav.su.se.bakover.domain.brev.BrevbestillingId
import no.nav.su.se.bakover.domain.eksterneiverksettingssteg.JournalføringOgBrevdistribusjon.IkkeJournalførtEllerDistribuert
import no.nav.su.se.bakover.domain.eksterneiverksettingssteg.JournalføringOgBrevdistribusjon.Journalført
import no.nav.su.se.bakover.domain.eksterneiverksettingssteg.JournalføringOgBrevdistribusjon.JournalførtOgDistribuertBrev
import no.nav.su.se.bakover.domain.eksterneiverksettingssteg.KunneIkkeJournalføreOgDistribuereBrev.KunneIkkeDistribuereBrev
import no.nav.su.se.bakover.domain.eksterneiverksettingssteg.KunneIkkeJournalføreOgDistribuereBrev.KunneIkkeJournalføre.AlleredeJournalført
import no.nav.su.se.bakover.domain.eksterneiverksettingssteg.KunneIkkeJournalføreOgDistribuereBrev.KunneIkkeJournalføre.FeilVedJournalføring
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.verifyNoMoreInteractions

internal class JournalføringOgBrevdistribusjonTest {
    @Nested
    inner class Journalføring {
        private val skalIkkeBliKaltVedJournalføring = mock<Either<FeilVedJournalføring, JournalpostId>>()

        @Test
        fun `journalfører venter på kvittering og oppdaterer status`() {
            IkkeJournalførtEllerDistribuert.journalfør { okJournalføring() } shouldBe Journalført(journalpostId).right()
        }

        @Test
        fun `svarer med feil dersom journalføring feiler`() {
            IkkeJournalførtEllerDistribuert.journalfør { errorJournalføring() } shouldBe FeilVedJournalføring.left()
        }

        @Test
        fun `svarer med feil dersom man forsøker å journalføre en allerede journalført`() {
            Journalført(journalpostId).journalfør { skalIkkeBliKaltVedJournalføring } shouldBe AlleredeJournalført(
                journalpostId,
            ).left()
            verifyNoMoreInteractions(skalIkkeBliKaltVedJournalføring)
        }

        @Test
        fun `svarer med feil dersom man forsøker å journalføre en allerede distribuert og journalført`() {
            JournalførtOgDistribuertBrev(
                journalpostId,
                brevbestillingId,
            ).journalfør { skalIkkeBliKaltVedJournalføring } shouldBe AlleredeJournalført(journalpostId).left()
            verifyNoMoreInteractions(skalIkkeBliKaltVedJournalføring)
        }
    }

    @Nested
    inner class BrevDistribusjon {
        private val skalIkkeBliKaltVedBrevDistribusjon = mock<Either<KunneIkkeDistribuereBrev.FeilVedDistribueringAvBrev, BrevbestillingId>>()
        private fun brevDistribusjonsfeil(id: JournalpostId) = KunneIkkeDistribuereBrev.FeilVedDistribueringAvBrev(id).left()

        @Test
        fun `skal ikke kunne distribuera brev når vi ikke har journalført`() {
            IkkeJournalførtEllerDistribuert.distribuerBrev { skalIkkeBliKaltVedBrevDistribusjon } shouldBe KunneIkkeDistribuereBrev.MåJournalføresFørst.left()
            verifyNoInteractions(skalIkkeBliKaltVedBrevDistribusjon)
        }

        @Test
        fun `distribuerer brev og oppdaterer status for journalført`() {
            Journalført(journalpostId).distribuerBrev { id -> okBrevDistribusjon(id) } shouldBe JournalførtOgDistribuertBrev(journalpostId, brevbestillingId).right()
        }

        @Test
        fun `skal ikke kunne distribuera brev når vi allerede har distribuert`() {
            JournalførtOgDistribuertBrev(journalpostId, brevbestillingId).distribuerBrev { skalIkkeBliKaltVedBrevDistribusjon } shouldBe KunneIkkeDistribuereBrev.AlleredeDistribuertBrev(journalpostId).left()
        }

        @Test
        fun `svarer med feil hvis distribuering feiler`() {
            Journalført(journalpostId).distribuerBrev { id -> brevDistribusjonsfeil(id) } shouldBe KunneIkkeDistribuereBrev.FeilVedDistribueringAvBrev(journalpostId).left()
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
