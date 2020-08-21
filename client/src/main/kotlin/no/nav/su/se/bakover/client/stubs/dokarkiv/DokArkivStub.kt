package no.nav.su.se.bakover.client.stubs.dokarkiv

import arrow.core.Either
import arrow.core.right
import no.nav.su.se.bakover.client.ClientError
import no.nav.su.se.bakover.client.dokarkiv.DokArkiv
import no.nav.su.se.bakover.domain.Person
import no.nav.su.se.bakover.domain.SøknadInnhold

object DokArkivStub : DokArkiv {
    override fun opprettJournalpost(
        søknadInnhold: SøknadInnhold,
        person: Person,
        pdf: ByteArray,
        sakId: String
    ): Either<ClientError, String> =
        """
                        {
                          "journalpostId": "1",
                          "journalpostferdigstilt": true,
                          "dokumenter": [
                            {
                              "dokumentInfoId": "485227498",
                              "tittel": "Søknad om supplerende stønad for uføre flyktninger"
                            }
                          ]
                        }
        """.trimIndent().right()
}
