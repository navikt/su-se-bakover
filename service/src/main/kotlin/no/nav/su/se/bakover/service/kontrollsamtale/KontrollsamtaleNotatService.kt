package no.nav.su.se.bakover.service.kontrollsamtale

import arrow.core.Either
import no.nav.su.se.bakover.common.domain.PdfA
import no.nav.su.se.bakover.common.persistence.SessionContext
import no.nav.su.se.bakover.domain.kontrollnotat.KontrollsamtaleNotat
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

    data object FantIkkeKontrollnotat

    sealed interface KunneIkkeLageKontrollnotatPdf {
        data object FantIkkeSak : KunneIkkeLageKontrollnotatPdf
        data object FantIkkePerson : KunneIkkeLageKontrollnotatPdf
        data object FantIkkeKontrollnotat : KunneIkkeLageKontrollnotatPdf
        data object KunneIkkeLagePdf : KunneIkkeLageKontrollnotatPdf
    }
}
