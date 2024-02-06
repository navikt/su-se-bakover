package dokument.domain.brev

import java.util.UUID

/**
 * Informasjon om id og hvilken type instans denne id'en stammer fra.
 * Avgjør hvilke [dokument.domain.Dokument.Metadata] som benyttes for oppslag.
 */
sealed interface HentDokumenterForIdType {
    val id: UUID

    data class HentDokumenterForSak(override val id: UUID) : HentDokumenterForIdType
    data class HentDokumenterForSøknad(override val id: UUID) : HentDokumenterForIdType
    data class HentDokumenterForRevurdering(override val id: UUID) : HentDokumenterForIdType
    data class HentDokumenterForVedtak(override val id: UUID) : HentDokumenterForIdType
    data class HentDokumenterForKlage(override val id: UUID) : HentDokumenterForIdType
}
