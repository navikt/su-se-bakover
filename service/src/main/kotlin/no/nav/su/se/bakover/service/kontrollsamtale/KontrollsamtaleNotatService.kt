package no.nav.su.se.bakover.service.kontrollsamtale

import arrow.core.Either
import no.nav.su.se.bakover.common.domain.PdfA
import no.nav.su.se.bakover.common.persistence.SessionContext
import no.nav.su.se.bakover.domain.kontrollnotat.KontrollsamtaleNotat
import no.nav.su.se.bakover.domain.kontrollnotat.KontrollsamtaleNotatVedlegg
import java.util.UUID

interface KontrollsamtaleNotatService {
    fun lagre(
        sakId: UUID,
        kontrollsamtaleNotat: KontrollsamtaleNotat,
        sessionContext: SessionContext? = null,
    )
    fun hentKontrollsamtaleNotatPdf(
        sakId: UUID,
    ): Either<KunneIkkeLageKontrollnotatPdf, PdfA>

    fun hentKontrollsamtaleNotat(
        sakId: UUID,
    ): Either<FantIkkeKontrollnotat, KontrollsamtaleNotat>

    fun leggTilVedlegg(
        sakId: UUID,
        filnavn: String,
        mimeType: String,
        innhold: ByteArray,
    ): Either<KontrollsamtaleNotatVedleggFeil, KontrollsamtaleNotatVedlegg>

    fun hentVedlegg(
        sakId: UUID,
    ): Either<KontrollsamtaleNotatVedleggFeil, List<KontrollsamtaleNotatVedlegg>>

    fun slettVedlegg(
        sakId: UUID,
        vedleggId: UUID,
    ): Either<KontrollsamtaleNotatVedleggFeil, Unit>

    data object FantIkkeKontrollnotat

    sealed interface KunneIkkeLageKontrollnotatPdf {
        data object FantIkkeSak : KunneIkkeLageKontrollnotatPdf
        data object FantIkkePerson : KunneIkkeLageKontrollnotatPdf
        data object FantIkkeKontrollnotat : KunneIkkeLageKontrollnotatPdf
        data object KunneIkkeLagePdf : KunneIkkeLageKontrollnotatPdf
    }

    sealed interface KontrollsamtaleNotatVedleggFeil {
        data object FantIkkeKontrollnotat : KontrollsamtaleNotatVedleggFeil
        data object FantIkkeVedlegg : KontrollsamtaleNotatVedleggFeil
        data object UgyldigMimeType : KontrollsamtaleNotatVedleggFeil
        data object MimeTypeMatcherIkkeFilnavn : KontrollsamtaleNotatVedleggFeil
        data object VedleggForStort : KontrollsamtaleNotatVedleggFeil
    }
}
