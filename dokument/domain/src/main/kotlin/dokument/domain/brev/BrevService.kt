package dokument.domain.brev

import arrow.core.Either
import dokument.domain.Dokument
import dokument.domain.GenererDokumentCommand
import dokument.domain.KunneIkkeLageDokument
import no.nav.su.se.bakover.common.persistence.TransactionContext
import java.util.UUID

interface BrevService {

    fun lagDokument(
        command: GenererDokumentCommand,
        id: UUID = UUID.randomUUID(),
    ): Either<KunneIkkeLageDokument, Dokument.UtenMetadata>

    fun lagreDokument(dokument: Dokument.MedMetadata)
    fun lagreDokument(dokument: Dokument.MedMetadata, transactionContext: TransactionContext)
    fun hentDokumenterFor(hentDokumenterForIdType: HentDokumenterForIdType): List<Dokument>
}

/**
 * Informasjon om id og hvilken type instans denne id'en stammer fra.
 * Avgjør hvilke [Dokument.Metadata] som benyttes for oppslag.
 */
sealed interface HentDokumenterForIdType {
    val id: UUID

    data class HentDokumenterForSak(override val id: UUID) : HentDokumenterForIdType
    data class HentDokumenterForSøknad(override val id: UUID) : HentDokumenterForIdType
    data class HentDokumenterForRevurdering(override val id: UUID) : HentDokumenterForIdType
    data class HentDokumenterForVedtak(override val id: UUID) : HentDokumenterForIdType
    data class HentDokumenterForKlage(override val id: UUID) : HentDokumenterForIdType
}

sealed interface KunneIkkeJournalføreBrev {

    data object KunneIkkeOppretteJournalpost : KunneIkkeJournalføreBrev
}

data object KunneIkkeDistribuereBrev

sealed interface KunneIkkeJournalføreDokument {
    data object KunneIkkeFinnePerson : KunneIkkeJournalføreDokument
    data object FeilVedOpprettelseAvJournalpost : KunneIkkeJournalføreDokument
}

sealed interface KunneIkkeBestilleBrevForDokument {

    data object FeilVedBestillingAvBrev : KunneIkkeBestilleBrevForDokument
    data object MåJournalføresFørst : KunneIkkeBestilleBrevForDokument
}
