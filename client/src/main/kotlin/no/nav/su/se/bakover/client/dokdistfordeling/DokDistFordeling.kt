package no.nav.su.se.bakover.client.dokdistfordeling

import arrow.core.Either
import no.nav.su.se.bakover.domain.brev.BrevbestillingId
import no.nav.su.se.bakover.domain.journal.JournalpostId

interface DokDistFordeling {
    fun bestillDistribusjon(
        journalPostId: JournalpostId
    ): Either<KunneIkkeBestilleDistribusjon, BrevbestillingId>
}

object KunneIkkeBestilleDistribusjon
