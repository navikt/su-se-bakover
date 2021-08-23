package no.nav.su.se.bakover.service.brev

import arrow.core.Either
import no.nav.su.se.bakover.common.persistence.TransactionContext
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.brev.BrevbestillingId
import no.nav.su.se.bakover.domain.brev.LagBrevRequest
import no.nav.su.se.bakover.domain.dokument.Dokument
import no.nav.su.se.bakover.domain.dokument.Dokumentdistribusjon
import no.nav.su.se.bakover.domain.journal.JournalpostId
import java.util.UUID

interface BrevService {
    fun lagBrev(request: LagBrevRequest): Either<KunneIkkeLageBrev, ByteArray>
    fun journalførBrev(request: LagBrevRequest, saksnummer: Saksnummer): Either<KunneIkkeJournalføreBrev, JournalpostId>
    fun distribuerBrev(journalpostId: JournalpostId): Either<KunneIkkeDistribuereBrev, BrevbestillingId>
    fun lagreDokument(dokument: Dokument.MedMetadata)
    fun lagreDokument(dokument: Dokument.MedMetadata, transactionContext: TransactionContext)
    fun journalførDokument(dokumentdistribusjon: Dokumentdistribusjon): Either<KunneIkkeJournalføreDokument, Dokumentdistribusjon>
    fun distribuerDokument(dokumentdistribusjon: Dokumentdistribusjon): Either<KunneIkkeBestilleBrevForDokument, Dokumentdistribusjon>
    fun hentDokumenterForDistribusjon(): List<Dokumentdistribusjon>
    fun hentDokumenterFor(hentDokumenterForIdType: HentDokumenterForIdType): List<Dokument>
}

/**
 * Informasjon om id og hvilken type instans denne id'en stammer fra.
 * Avgjør hvilke [Dokument.Metadata] som benyttes for oppslag.
 */
sealed class HentDokumenterForIdType {
    abstract val id: UUID

    data class Sak(override val id: UUID) : HentDokumenterForIdType()
    data class Søknad(override val id: UUID) : HentDokumenterForIdType()
    data class Revurdering(override val id: UUID) : HentDokumenterForIdType()
    data class Vedtak(override val id: UUID) : HentDokumenterForIdType()
}

sealed class KunneIkkeLageBrev {
    object KunneIkkeGenererePDF : KunneIkkeLageBrev()
    object FantIkkePerson : KunneIkkeLageBrev()
}

sealed class KunneIkkeJournalføreBrev {
    object KunneIkkeGenereBrev : KunneIkkeJournalføreBrev()
    object KunneIkkeOppretteJournalpost : KunneIkkeJournalføreBrev()
}

object KunneIkkeDistribuereBrev

sealed class KunneIkkeJournalføreDokument {
    object KunneIkkeFinneSak : KunneIkkeJournalføreDokument()
    object KunneIkkeFinnePerson : KunneIkkeJournalføreDokument()
    object FeilVedOpprettelseAvJournalpost : KunneIkkeJournalføreDokument()
}

sealed class KunneIkkeBestilleBrevForDokument {
    object FeilVedBestillingAvBrev : KunneIkkeBestilleBrevForDokument()
    object MåJournalføresFørst : KunneIkkeBestilleBrevForDokument()
}
