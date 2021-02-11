package no.nav.su.se.bakover.domain.eksterneiverksettingssteg

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.domain.brev.BrevbestillingId
import no.nav.su.se.bakover.domain.eksterneiverksettingssteg.EksterneIverksettingsstegEtterUtbetaling.Journalført
import no.nav.su.se.bakover.domain.eksterneiverksettingssteg.EksterneIverksettingsstegEtterUtbetaling.JournalførtOgDistribuertBrev
import no.nav.su.se.bakover.domain.eksterneiverksettingssteg.EksterneIverksettingsstegEtterUtbetaling.VenterPåKvittering
import no.nav.su.se.bakover.domain.eksterneiverksettingssteg.EksterneIverksettingsstegFeil.EksterneIverksettingsstegEtterUtbetalingFeil.KunneIkkeDistribuereBrev
import no.nav.su.se.bakover.domain.eksterneiverksettingssteg.EksterneIverksettingsstegFeil.EksterneIverksettingsstegEtterUtbetalingFeil.KunneIkkeJournalføre.AlleredeJournalført
import no.nav.su.se.bakover.domain.eksterneiverksettingssteg.EksterneIverksettingsstegFeil.EksterneIverksettingsstegEtterUtbetalingFeil.KunneIkkeJournalføre.FeilVedJournalføring
import no.nav.su.se.bakover.domain.journal.JournalpostId
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

internal class EksterneIverksettingsstegEtterUtbetalingTest {
    @Nested
    inner class Journalføring {
        private val skalIkkeBliKaltVedJournalføring = mock<Either<FeilVedJournalføring, JournalpostId>>()
        @Test
        fun `journalfører venter på kvittering og oppdaterer status`() {
            VenterPåKvittering.journalfør { okJournalføring() } shouldBe Journalført(journalpostId).right()
        }

        @Test
        fun `svarer med feil dersom journalføring feiler`() {
            VenterPåKvittering.journalfør { errorJournalføring() } shouldBe FeilVedJournalføring.left()
        }

        @Test
        fun `svarer med feil dersom man forsøker å journalføre en allerede journalført`() {
            Journalført(journalpostId).journalfør { skalIkkeBliKaltVedJournalføring } shouldBe AlleredeJournalført(
                journalpostId
            ).left()
            verifyNoMoreInteractions(skalIkkeBliKaltVedJournalføring)
        }

        @Test
        fun `svarer med feil dersom man forsøker å journalføre en allerede distribuert og journalført`() {
            JournalførtOgDistribuertBrev(
                journalpostId,
                brevbestillingId
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
            VenterPåKvittering.distribuerBrev { skalIkkeBliKaltVedBrevDistribusjon } shouldBe KunneIkkeDistribuereBrev.MåJournalføresFørst.left()
            verifyZeroInteractions(skalIkkeBliKaltVedBrevDistribusjon)
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
