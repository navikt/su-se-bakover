package no.nav.su.se.bakover.client.stubs.dokdistfordeling

import arrow.core.Either
import arrow.core.right
import no.nav.su.se.bakover.client.dokdistfordeling.DokDistFordeling
import no.nav.su.se.bakover.client.dokdistfordeling.KunneIkkeBestilleDistribusjon
import no.nav.su.se.bakover.domain.brev.BrevbestillingId
import no.nav.su.se.bakover.domain.journal.JournalpostId

object DokDistFordelingStub : DokDistFordeling {
    override fun bestillDistribusjon(
        journalPostId: JournalpostId
    ): Either<KunneIkkeBestilleDistribusjon, BrevbestillingId> = BrevbestillingId("51be490f-5a21-44dd-8d38-d762491d6b22").right()
}
