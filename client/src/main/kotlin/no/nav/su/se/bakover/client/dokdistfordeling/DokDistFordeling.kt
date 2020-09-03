package no.nav.su.se.bakover.client.dokdistfordeling

import arrow.core.Either
import no.nav.su.se.bakover.client.ClientError

interface DokDistFordeling {
    fun bestillDistribusjon(
        journalPostId: String
    ): Either<ClientError, String>
}
