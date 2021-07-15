package no.nav.su.se.bakover.domain.dokument

import arrow.core.Either
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.domain.brev.BrevbestillingId
import no.nav.su.se.bakover.domain.eksterneiverksettingssteg.JournalføringOgBrevdistribusjon
import no.nav.su.se.bakover.domain.eksterneiverksettingssteg.KunneIkkeJournalføreOgDistribuereBrev
import no.nav.su.se.bakover.domain.journal.JournalpostId
import java.util.UUID

sealed class Dokument {
    abstract val id: UUID
    abstract val opprettet: Tidspunkt
    abstract val generertDokument: ByteArray

    /**
     * Json-representasjon av data som ble benyttet for opprettelsen av [generertDokument]
     */
    abstract val generertDokumentJson: String
    abstract val metadata: Metadata

    data class Vedtak(
        override val id: UUID = UUID.randomUUID(),
        override val opprettet: Tidspunkt = Tidspunkt.now(),
        override val generertDokument: ByteArray,
        override val generertDokumentJson: String,
        override val metadata: Metadata,
    ) : Dokument()

    data class Informasjon(
        override val id: UUID = UUID.randomUUID(),
        override val opprettet: Tidspunkt = Tidspunkt.now(),
        override val generertDokument: ByteArray,
        override val generertDokumentJson: String,
        override val metadata: Metadata,
    ) : Dokument()

    data class Metadata(
        val sakId: UUID,
        val søknadId: UUID? = null,
        val vedtakId: UUID? = null,
        val tittel: String,
        val bestillBrev: Boolean,
    )
}

data class Dokumentdistribusjon(
    val id: UUID,
    val opprettet: Tidspunkt,
    val endret: Tidspunkt,
    val dokument: Dokument,
    val journalføringOgBrevdistribusjon: JournalføringOgBrevdistribusjon,
) {
    fun journalfør(journalfør: () -> Either<KunneIkkeJournalføreOgDistribuereBrev.KunneIkkeJournalføre.FeilVedJournalføring, JournalpostId>): Either<KunneIkkeJournalføreOgDistribuereBrev.KunneIkkeJournalføre, Dokumentdistribusjon> {
        return journalføringOgBrevdistribusjon.journalfør(journalfør)
            .map { copy(journalføringOgBrevdistribusjon = it) }
    }

    fun distribuerBrev(distribuerBrev: (journalpostId: JournalpostId) -> Either<KunneIkkeJournalføreOgDistribuereBrev.KunneIkkeDistribuereBrev.FeilVedDistribueringAvBrev, BrevbestillingId>): Either<KunneIkkeJournalføreOgDistribuereBrev.KunneIkkeDistribuereBrev, Dokumentdistribusjon> {
        return journalføringOgBrevdistribusjon.distribuerBrev(distribuerBrev)
            .map { copy(journalføringOgBrevdistribusjon = it) }
    }
}
