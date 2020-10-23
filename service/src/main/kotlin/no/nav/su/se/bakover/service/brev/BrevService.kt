package no.nav.su.se.bakover.service.brev

import arrow.core.Either
import no.nav.su.se.bakover.domain.Behandling
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.brev.LagBrevRequest

interface BrevService {
    fun journalførVedtakOgSendBrev(
        sak: Sak,
        behandling: Behandling
    ): Either<KunneIkkeOppretteJournalpostOgSendeBrev, String>

    fun lagBrev(request: LagBrevRequest): Either<KunneIkkeLageBrev, ByteArray>
}

sealed class KunneIkkeLageBrev {
    object KunneIkkeGenererePDF : KunneIkkeLageBrev()
}
object KunneIkkeOppretteJournalpostOgSendeBrev
