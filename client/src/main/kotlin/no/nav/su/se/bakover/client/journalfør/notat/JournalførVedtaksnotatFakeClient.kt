package no.nav.su.se.bakover.client.journalfør.notat

import arrow.core.Either
import arrow.core.right
import no.nav.su.se.bakover.common.domain.client.ClientError
import no.nav.su.se.bakover.common.journal.JournalpostId
import no.nav.su.se.bakover.dokument.infrastructure.client.journalføring.JournalpostIdGeneratorForFakes
import no.nav.su.se.bakover.domain.notat.JournalførVedtaksnotatClient
import no.nav.su.se.bakover.domain.notat.JournalførVedtaksnotatCommand

class JournalførVedtaksnotatFakeClient(
    private val journalpostIdGenerator: JournalpostIdGeneratorForFakes,
) : JournalførVedtaksnotatClient {
    override fun journalførVedtaksnotat(command: JournalførVedtaksnotatCommand): Either<ClientError, JournalpostId> {
        return journalpostIdGenerator.next().right()
    }
}
