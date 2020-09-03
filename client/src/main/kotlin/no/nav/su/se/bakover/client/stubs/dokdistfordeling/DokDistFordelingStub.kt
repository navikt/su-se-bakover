package no.nav.su.se.bakover.client.stubs.dokdistfordeling

import arrow.core.Either
import arrow.core.right
import no.nav.su.se.bakover.client.ClientError
import no.nav.su.se.bakover.client.dokdistfordeling.DokDistFordeling

object DokDistFordelingStub: DokDistFordeling {
    override fun bestillDistribusjon(
        journalPostId: String
    ): Either<ClientError, String> =
        """
                        {
                            "bestillingsId": "51be490f-5a21-44dd-8d38-d762491d6b22" 
                        }
        """.trimIndent().right()
}
