package no.nav.su.se.bakover.dokument.infrastructure.journalføring.brev

import arrow.core.Either
import dokument.domain.journalføring.brev.JournalførBrevClient
import dokument.domain.journalføring.brev.JournalførBrevCommand
import no.nav.su.se.bakover.common.domain.client.ClientError
import no.nav.su.se.bakover.common.journal.JournalpostId
import no.nav.su.se.bakover.dokument.infrastructure.journalføring.AvsenderMottaker
import no.nav.su.se.bakover.dokument.infrastructure.journalføring.Fagsak
import no.nav.su.se.bakover.dokument.infrastructure.journalføring.JournalPostType
import no.nav.su.se.bakover.dokument.infrastructure.journalføring.JournalførHttpClient
import no.nav.su.se.bakover.dokument.infrastructure.journalføring.JournalførJsonRequest
import no.nav.su.se.bakover.dokument.infrastructure.journalføring.JournalførendeEnhet
import no.nav.su.se.bakover.dokument.infrastructure.journalføring.JournalpostDokument
import no.nav.su.se.bakover.dokument.infrastructure.journalføring.tilBehandlingstema
import no.nav.su.se.bakover.dokument.infrastructure.journalføring.tilBruker

internal class JournalførBrevHttpClient(private val client: JournalførHttpClient) : JournalførBrevClient {
    override fun journalførBrev(command: JournalførBrevCommand): Either<ClientError, JournalpostId> {
        val dokument = command.dokument
        return client.opprettJournalpost(
            JournalførJsonRequest(
                tittel = dokument.tittel,
                journalpostType = JournalPostType.UTGAAENDE,
                kanal = null,
                behandlingstema = command.sakstype.tilBehandlingstema(),
                journalfoerendeEnhet = JournalførendeEnhet.ÅLESUND.enhet,
                avsenderMottaker = AvsenderMottaker(id = command.fnr.toString()),
                bruker = command.fnr.tilBruker(),
                sak = Fagsak(command.saksnummer.nummer.toString()),
                dokumenter = JournalpostDokument.lagDokumenterForJournalpostForSak(
                    tittel = dokument.tittel,
                    pdf = dokument.generertDokument,
                    originalJson = dokument.generertDokumentJson,
                ),
                datoDokument = dokument.opprettet,
                eksternReferanseId = command.internDokumentId.toString(),
            ),
        )
    }
}

fun createJournalførBrevHttpClient(client: JournalførHttpClient): JournalførBrevClient {
    return JournalførBrevHttpClient(client)
}
