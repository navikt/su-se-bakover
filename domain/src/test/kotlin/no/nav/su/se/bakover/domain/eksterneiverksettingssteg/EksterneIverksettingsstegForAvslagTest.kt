package no.nav.su.se.bakover.domain.eksterneiverksettingssteg

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.nhaarman.mockitokotlin2.mock
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.domain.brev.BrevbestillingId
import no.nav.su.se.bakover.domain.journal.JournalpostId
import org.junit.jupiter.api.Test

class EksterneIverksettingsstegForAvslagTest {
    private val journalpostId = JournalpostId("jp")
    private val brevbestillingId = BrevbestillingId("bid")
    private val skalIkkeBliKaltVedBrevDistribusjon =
        mock<Either<EksterneIverksettingsstegFeil.EksterneIverksettingsstegForAvslagFeil.KunneIkkeDistribuereBrev.FeilVedDistribueringAvBrev, BrevbestillingId>>()
    private fun okBrevDistribusjon(@Suppress("UNUSED_PARAMETER") journalpostId: JournalpostId): Either<Nothing, BrevbestillingId> {
        return brevbestillingId.right()
    }

    private fun brevDistribusjonsfeil(id: JournalpostId) =
        EksterneIverksettingsstegFeil.EksterneIverksettingsstegForAvslagFeil.KunneIkkeDistribuereBrev.FeilVedDistribueringAvBrev(
            id
        ).left()

    @Test
    fun `distribuerer brev og oppdaterer status for journalført`() {
        EksterneIverksettingsstegForAvslag.Journalført(journalpostId).distribuerBrev { id -> okBrevDistribusjon(id) } shouldBe EksterneIverksettingsstegForAvslag.JournalførtOgDistribuertBrev(
            journalpostId,
            brevbestillingId
        ).right()
    }

    @Test
    fun `skal ikke kunne distribuera brev når vi allerede har distribuert`() {
        EksterneIverksettingsstegForAvslag.JournalførtOgDistribuertBrev(journalpostId, brevbestillingId)
            .distribuerBrev { skalIkkeBliKaltVedBrevDistribusjon } shouldBe EksterneIverksettingsstegFeil.EksterneIverksettingsstegForAvslagFeil.KunneIkkeDistribuereBrev.AlleredeDistribuertBrev(
            journalpostId
        ).left()
    }

    @Test
    fun `svarer med feil hvis distribuering feiler`() {
        EksterneIverksettingsstegForAvslag.Journalført(journalpostId)
            .distribuerBrev { id -> brevDistribusjonsfeil(id) } shouldBe EksterneIverksettingsstegFeil.EksterneIverksettingsstegForAvslagFeil.KunneIkkeDistribuereBrev.FeilVedDistribueringAvBrev(
            journalpostId
        ).left()
    }
}
