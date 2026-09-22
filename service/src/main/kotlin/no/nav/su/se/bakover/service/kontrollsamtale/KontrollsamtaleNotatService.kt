package no.nav.su.se.bakover.service.kontrollsamtale

import arrow.core.Either
import no.nav.su.se.bakover.common.domain.PdfA
import no.nav.su.se.bakover.common.domain.sak.SakInfo
import no.nav.su.se.bakover.common.journal.JournalpostId
import no.nav.su.se.bakover.domain.kontrollnotat.KontrollsamtaleNotat
import person.domain.Person
import java.util.UUID

interface KontrollsamtaleNotatService {
    fun lagre(
        sakId: UUID,
        kontrollsamtaleNotat: KontrollsamtaleNotat,
    ): Either<KunneIkkeOppretteJournalpost, KontrollsamtaleNotat>

    fun hentKontrollsamtaleNotatPdf(
        sakId: UUID,
    ): Either<KunneIkkeLageKontrollnotatPdf, PdfA>

    fun hentKontrollsamtaleNotat(
        sakId: UUID,
    ): Either<FantIkkeKontrollnotat, KontrollsamtaleNotat>

    fun opprettJournalpost(
        sakInfo: SakInfo,
        kontrollsamtaleNotat: KontrollsamtaleNotat,
        person: Person,
    ): Either<KunneIkkeOppretteJournalpost, JournalpostId>

    fun forsøkJournalpostPåNytt()

    data object FantIkkeKontrollnotat

    sealed interface KunneIkkeLageKontrollnotatPdf {
        data object FantIkkeSak : KunneIkkeLageKontrollnotatPdf
        data object FantIkkePerson : KunneIkkeLageKontrollnotatPdf
        data object FantIkkeKontrollnotat : KunneIkkeLageKontrollnotatPdf
        data object KunneIkkeLagePdf : KunneIkkeLageKontrollnotatPdf
        data object KunneIkkeGenerereForside : KunneIkkeLageKontrollnotatPdf
    }

    data class KunneIkkeOppretteJournalpost(val sakId: UUID, val kontrollsamtaleNotatId: UUID, val grunn: String)
}
