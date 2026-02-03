package no.nav.su.se.bakover.dokument.infrastructure.client.journalføring.brev

import arrow.core.Either
import dokument.domain.Dokument
import dokument.domain.journalføring.brev.JournalførBrevClient
import dokument.domain.journalføring.brev.JournalførBrevCommand
import no.nav.su.se.bakover.common.domain.client.ClientError
import no.nav.su.se.bakover.common.journal.JournalpostId
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.dokument.infrastructure.client.journalføring.AvsenderMottakerFnr
import no.nav.su.se.bakover.dokument.infrastructure.client.journalføring.AvsenderMottakerOrgnr
import no.nav.su.se.bakover.dokument.infrastructure.client.journalføring.Fagsak
import no.nav.su.se.bakover.dokument.infrastructure.client.journalføring.JournalPostType
import no.nav.su.se.bakover.dokument.infrastructure.client.journalføring.JournalførHttpClient
import no.nav.su.se.bakover.dokument.infrastructure.client.journalføring.JournalførJsonRequest
import no.nav.su.se.bakover.dokument.infrastructure.client.journalføring.JournalførendeEnhet
import no.nav.su.se.bakover.dokument.infrastructure.client.journalføring.JournalpostDokument
import no.nav.su.se.bakover.dokument.infrastructure.client.journalføring.tilBehandlingstema
import no.nav.su.se.bakover.dokument.infrastructure.client.journalføring.tilBruker

internal class JournalførBrevHttpClient(private val client: JournalførHttpClient) : JournalførBrevClient {
    override fun journalførBrev(command: JournalførBrevCommand): Either<ClientError, JournalpostId> {
        val dokument = command.dokument
        // TODO: kan hende vi må ha noe tilsvarende for dokdist.
        val mottakerIdentifikator = when (dokument) {
            is Dokument.MedMetadata.Informasjon.Annet -> command.fnr.toString()
            is Dokument.MedMetadata.Informasjon.Viktig -> command.fnr.toString()
            is Dokument.MedMetadata.Vedtak -> dokument.ekstraMottaker ?: command.fnr.toString()
        }

        val avsender = if (Fnr.tryCreate(mottakerIdentifikator) == null) {
            AvsenderMottakerFnr(id = mottakerIdentifikator)
        } else {
            AvsenderMottakerOrgnr(id = mottakerIdentifikator)
        }
        return client.opprettJournalpost(
            JournalførJsonRequest(
                tittel = dokument.tittel,
                journalpostType = JournalPostType.UTGAAENDE,
                kanal = null,
                behandlingstema = command.sakstype.tilBehandlingstema(),
                journalfoerendeEnhet = JournalførendeEnhet.ÅLESUND.enhet,
                // denne støtter også navn men dokdist gjør vel noe magi der basert på fnr
                avsenderMottaker = avsender, // denne skal være verge eller søker men vi har ingen støtte for dette
                bruker = command.fnr.tilBruker(), // Denne må være søker fnr - men også for orgnr
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
