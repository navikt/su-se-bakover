package no.nav.su.se.bakover.service.brev

import arrow.core.Either
import no.nav.su.se.bakover.common.persistence.TransactionContext
import no.nav.su.se.bakover.domain.brev.LagBrevRequest
import no.nav.su.se.bakover.domain.dokument.Dokument
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
    fun lagBrevRequest(visitable: Visitable<LagBrevRequestVisitor>): Either<LagBrevRequestVisitor.KunneIkkeLageBrevRequest, LagBrevRequest>

    /** Journalfører og distribuerer utgående dokumenter som ikke har blitt journalført / distribuert.
     * Denne er i utgangspunktet ment å kalles av en kron-jobb. */
    fun journalførOgDistribuerUtgåendeDokumenter()
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
    data class Klage(override val id: UUID) : HentDokumenterForIdType()
}

sealed class KunneIkkeLageDokument {
    override fun toString() = this::class.simpleName!!
    object KunneIkkeFinneGjeldendeUtbetaling : KunneIkkeLageDokument()
    object KunneIkkeHenteNavnForSaksbehandlerEllerAttestant : KunneIkkeLageDokument()
    object KunneIkkeHentePerson : KunneIkkeLageDokument()
    object KunneIkkeGenererePDF : KunneIkkeLageDokument()
    object DetSkalIkkeSendesBrev : KunneIkkeLageDokument()
}

sealed class KunneIkkeLageBrev {
    override fun toString() = this::class.simpleName!!

    object KunneIkkeGenererePDF : KunneIkkeLageBrev()
    object FantIkkePerson : KunneIkkeLageBrev()
}

sealed class KunneIkkeJournalføreBrev {
    override fun toString() = this::class.simpleName!!

    object KunneIkkeOppretteJournalpost : KunneIkkeJournalføreBrev()
}

object KunneIkkeDistribuereBrev

sealed class KunneIkkeJournalføreDokument {
    override fun toString() = this::class.simpleName!!

    object KunneIkkeFinneSak : KunneIkkeJournalføreDokument()
    object KunneIkkeFinnePerson : KunneIkkeJournalføreDokument()
    object FeilVedOpprettelseAvJournalpost : KunneIkkeJournalføreDokument()
}

sealed class KunneIkkeBestilleBrevForDokument {
    override fun toString() = this::class.simpleName!!

    object FeilVedBestillingAvBrev : KunneIkkeBestilleBrevForDokument()
    object MåJournalføresFørst : KunneIkkeBestilleBrevForDokument()
}
