package no.nav.su.se.bakover.client.journalfør.skatt.utenforsak

import arrow.core.Either
import no.nav.su.se.bakover.common.domain.client.ClientError
import no.nav.su.se.bakover.common.journal.JournalpostId
import no.nav.su.se.bakover.dokument.infrastructure.database.journalføring.Fagsak
import no.nav.su.se.bakover.dokument.infrastructure.database.journalføring.JournalPostType
import no.nav.su.se.bakover.dokument.infrastructure.database.journalføring.JournalførHttpClient
import no.nav.su.se.bakover.dokument.infrastructure.database.journalføring.JournalførJsonRequest
import no.nav.su.se.bakover.dokument.infrastructure.database.journalføring.JournalførendeEnhet
import no.nav.su.se.bakover.dokument.infrastructure.database.journalføring.JournalpostDokument
import no.nav.su.se.bakover.dokument.infrastructure.database.journalføring.tilBehandlingstema
import no.nav.su.se.bakover.dokument.infrastructure.database.journalføring.tilBruker
import vilkår.skatt.domain.journalpost.JournalførSkattedokumentUtenforSakClient
import vilkår.skatt.domain.journalpost.JournalførSkattedokumentUtenforSakCommand

/**
 * TODO jah: Flytt til skatt modul når vi har en skatt modul.
 */
internal class JournalførSkattedokumentUtenforSakHttpClient(
    private val client: JournalførHttpClient,
) : JournalførSkattedokumentUtenforSakClient {
    override fun journalførSkattedokument(command: JournalførSkattedokumentUtenforSakCommand): Either<ClientError, JournalpostId> {
        val dokument = command.dokument
        return client.opprettJournalpost(
            JournalførJsonRequest(
                tittel = dokument.tittel,
                journalpostType = JournalPostType.NOTAT,
                kanal = null,
                behandlingstema = command.sakstype.tilBehandlingstema(),
                journalfoerendeEnhet = JournalførendeEnhet.AUTOMATISK.enhet,
                avsenderMottaker = null,
                bruker = command.fnr.tilBruker(),
                sak = Fagsak(command.fagsystemId),
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
