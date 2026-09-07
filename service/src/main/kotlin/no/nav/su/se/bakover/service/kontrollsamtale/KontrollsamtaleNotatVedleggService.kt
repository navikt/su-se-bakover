package no.nav.su.se.bakover.service.kontrollsamtale

import arrow.core.Either
import no.nav.su.se.bakover.domain.kontrollnotat.KontrollsamtaleNotatVedlegg
import java.util.UUID

interface KontrollsamtaleNotatVedleggService {
    fun leggTilVedlegg(
        kontrollsamtaleId: UUID,
        filnavn: String,
        mimeType: String,
        innhold: ByteArray,
    ): Either<KontrollsamtaleNotatVedleggFeil, KontrollsamtaleNotatVedlegg>

    fun hentVedlegg(
        kontrollsamtaleId: UUID,
    ): List<KontrollsamtaleNotatVedlegg>

    fun slettVedlegg(
        vedleggId: UUID,
    )

    sealed interface KontrollsamtaleNotatVedleggFeil {
        data object UgyldigMimeType : KontrollsamtaleNotatVedleggFeil
        data object MimeTypeMatcherIkkeFilnavn : KontrollsamtaleNotatVedleggFeil
        data object FilForStor : KontrollsamtaleNotatVedleggFeil
        data object FantIkkeKontrollsamtale : KontrollsamtaleNotatVedleggFeil
    }
}
