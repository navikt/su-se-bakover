package no.nav.su.se.bakover.domain.brev

import arrow.core.Either
import no.nav.su.se.bakover.common.persistence.TransactionContext
import no.nav.su.se.bakover.domain.dokument.Dokument
import no.nav.su.se.bakover.domain.dokument.KunneIkkeLageDokument
import no.nav.su.se.bakover.domain.visitor.LagBrevRequestVisitor
import no.nav.su.se.bakover.domain.visitor.Visitable
import java.util.UUID

interface BrevService {
    fun lagBrev(request: LagBrevRequest): Either<KunneIkkeLageBrev, ByteArray>
    fun lagDokument(request: LagBrevRequest): Either<KunneIkkeLageDokument, Dokument.UtenMetadata>
    fun lagreDokument(dokument: Dokument.MedMetadata)
    fun lagreDokument(dokument: Dokument.MedMetadata, transactionContext: TransactionContext)
    fun hentDokumenterFor(hentDokumenterForIdType: HentDokumenterForIdType): List<Dokument>
    fun lagDokument(visitable: Visitable<LagBrevRequestVisitor>): Either<KunneIkkeLageDokument, Dokument.UtenMetadata>
    fun lagBrevRequest(visitable: Visitable<LagBrevRequestVisitor>): Either<KunneIkkeLageBrevRequest, LagBrevRequest>
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

sealed class KunneIkkeLageBrev {

    data object KunneIkkeGenererePDF : KunneIkkeLageBrev()
    data object FantIkkePerson : KunneIkkeLageBrev()
}

sealed class KunneIkkeJournalføreBrev {

    data object KunneIkkeOppretteJournalpost : KunneIkkeJournalføreBrev()
}

data object KunneIkkeDistribuereBrev

sealed class KunneIkkeJournalføreDokument {

    data object KunneIkkeFinneSak : KunneIkkeJournalføreDokument()
    data object KunneIkkeFinnePerson : KunneIkkeJournalføreDokument()
    data object FeilVedOpprettelseAvJournalpost : KunneIkkeJournalføreDokument()
}

sealed class KunneIkkeBestilleBrevForDokument {

    data object FeilVedBestillingAvBrev : KunneIkkeBestilleBrevForDokument()
    data object MåJournalføresFørst : KunneIkkeBestilleBrevForDokument()
}
