package no.nav.su.se.bakover.dokument.infrastructure.journalføring.søknad

import arrow.core.Either
import dokument.domain.journalføring.søknad.JournalførSøknadClient
import dokument.domain.journalføring.søknad.JournalførSøknadCommand
import no.nav.su.se.bakover.common.domain.client.ClientError
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.journal.JournalpostId
import no.nav.su.se.bakover.dokument.infrastructure.journalføring.AvsenderMottaker
import no.nav.su.se.bakover.dokument.infrastructure.journalføring.Fagsak
import no.nav.su.se.bakover.dokument.infrastructure.journalføring.JournalPostType
import no.nav.su.se.bakover.dokument.infrastructure.journalføring.JournalførHttpClient
import no.nav.su.se.bakover.dokument.infrastructure.journalføring.JournalførJsonRequest
import no.nav.su.se.bakover.dokument.infrastructure.journalføring.JournalførendeEnhet
import no.nav.su.se.bakover.dokument.infrastructure.journalføring.JournalpostDokument
import no.nav.su.se.bakover.dokument.infrastructure.journalføring.Kanal
import no.nav.su.se.bakover.dokument.infrastructure.journalføring.tilBehandlingstema
import no.nav.su.se.bakover.dokument.infrastructure.journalføring.tilBruker

/**
 * TODO jah: Denne bør egentlig bo i :søknad-modulen som ikke eksisterer på dette tidspunktet.
 */
internal class JournalførSøknadHttpClient(private val client: JournalførHttpClient) : JournalførSøknadClient {
    override fun journalførSøknad(command: JournalførSøknadCommand): Either<ClientError, JournalpostId> {
        return client.opprettJournalpost(
            JournalførJsonRequest(
                tittel = when (command.sakstype) {
                    Sakstype.ALDER -> "Søknad om supplerende stønad for alder"
                    Sakstype.UFØRE -> "Søknad om supplerende stønad for uføre flyktninger"
                },
                journalpostType = JournalPostType.INNGAAENDE,
                kanal = Kanal.INNSENDT_NAV_ANSATT.value,
                behandlingstema = command.sakstype.tilBehandlingstema(),
                journalfoerendeEnhet = JournalførendeEnhet.AUTOMATISK.enhet,
                avsenderMottaker = AvsenderMottaker(id = command.fnr.toString()),
                bruker = command.fnr.tilBruker(),
                sak = Fagsak(command.saksnummer.nummer.toString()),
                dokumenter = JournalpostDokument.lagDokumenterForJournalpostForSak(
                    tittel = when (command.sakstype) {
                        Sakstype.ALDER -> "Søknad om supplerende stønad for alder"
                        Sakstype.UFØRE -> "Søknad om supplerende stønad for uføre flyktninger"
                    },
                    pdf = command.pdf,
                    originalJson = command.søknadInnholdJson,
                ),
                datoDokument = command.datoDokument,
                eksternReferanseId = command.internDokumentId.toString(),
            ),
        )
    }
}

fun createJournalførSøknadHttpClient(client: JournalførHttpClient): JournalførSøknadClient {
    return JournalførSøknadHttpClient(client)
}
