package no.nav.su.se.bakover.domain.eksterneiverksettingssteg

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.domain.brev.BrevbestillingId
import no.nav.su.se.bakover.domain.eksterneiverksettingssteg.EksterneIverksettingsstegEtterUtbetaling.Journalført
import no.nav.su.se.bakover.domain.eksterneiverksettingssteg.EksterneIverksettingsstegEtterUtbetaling.JournalførtOgDistribuertBrev
import no.nav.su.se.bakover.domain.eksterneiverksettingssteg.EksterneIverksettingsstegEtterUtbetaling.VenterPåKvittering
import no.nav.su.se.bakover.domain.eksterneiverksettingssteg.EksterneIverksettingsstegFeil.EksterneIverksettingsstegEtterUtbetalingFeil.KunneIkkeJournalføre.AlleredeJournalført
import no.nav.su.se.bakover.domain.eksterneiverksettingssteg.EksterneIverksettingsstegFeil.EksterneIverksettingsstegEtterUtbetalingFeil.KunneIkkeJournalføre.FeilVedJournalføring
import no.nav.su.se.bakover.domain.journal.JournalpostId
import org.junit.jupiter.api.Test

internal class EksterneIverksettingsstegEtterUtbetalingTest {
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

    private val journalpostId = JournalpostId("jp")
    private val brevbestillingId = BrevbestillingId("bid")
    private val skalIkkeBliKaltVedJournalføring = mock<Either<FeilVedJournalføring, JournalpostId>>()

    private fun okJournalføring() = journalpostId.right()
    private fun errorJournalføring() = FeilVedJournalføring.left()
}
