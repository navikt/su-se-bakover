package no.nav.su.se.bakover.service.brev

import arrow.core.Either
import no.nav.su.se.bakover.domain.Behandling
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.brev.LagBrevRequest
import no.nav.su.se.bakover.domain.journal.JournalpostId
import java.util.UUID

interface BrevService {
    fun journalførVedtakOgSendBrev(
        sak: Sak,
        behandling: Behandling
    ): Either<KunneIkkeOppretteJournalpostOgSendeBrev, String>

    fun lagBrev(request: LagBrevRequest): Either<KunneIkkeLageBrev, ByteArray>
    fun journalførBrev(request: LagBrevRequest, sakId: UUID): Either<KunneIkkeJournalføreBrev, JournalpostId>
    fun distribuerBrev(journalpostId: JournalpostId): Either<KunneIkkeSendeBrev, String>
}

sealed class KunneIkkeLageBrev {
    object KunneIkkeGenererePDF : KunneIkkeLageBrev()
    object FantIkkePerson : KunneIkkeLageBrev()
}

sealed class KunneIkkeJournalføreBrev {
    object FantIkkePerson : KunneIkkeJournalføreBrev()
    object KunneIkkeGenereBrev : KunneIkkeJournalføreBrev()
    object KunneIkkeOppretteJournalpost : KunneIkkeJournalføreBrev()
}

object KunneIkkeSendeBrev

object KunneIkkeOppretteJournalpostOgSendeBrev
