package no.nav.su.se.bakover.client.dokdistfordeling

import arrow.core.Either
import no.nav.su.se.bakover.client.ClientError
import no.nav.su.se.bakover.domain.journal.JournalpostId

interface DokDistFordeling {
    fun bestillDistribusjon(
        journalPostId: JournalpostId
    ): Either<ClientError, String>
}
