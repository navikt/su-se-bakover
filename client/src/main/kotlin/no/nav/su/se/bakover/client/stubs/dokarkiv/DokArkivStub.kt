package no.nav.su.se.bakover.client.stubs.dokarkiv

import arrow.core.Either
import arrow.core.right
import no.nav.su.meldinger.kafka.soknad.NySøknad
import no.nav.su.se.bakover.client.ClientError
import no.nav.su.se.bakover.client.dokarkiv.DokArkiv

object DokArkivStub : DokArkiv {
    override fun opprettJournalpost(nySøknad: NySøknad, pdf: ByteArray): Either<ClientError, String> = """
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
