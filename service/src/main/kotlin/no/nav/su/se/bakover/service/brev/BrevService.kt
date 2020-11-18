package no.nav.su.se.bakover.service.brev

import arrow.core.Either
import no.nav.su.se.bakover.domain.brev.BrevbestillingId
import no.nav.su.se.bakover.domain.brev.LagBrevRequest
import no.nav.su.se.bakover.domain.journal.JournalpostId
import java.util.UUID

interface BrevService {
    fun lagBrev(request: LagBrevRequest): Either<KunneIkkeLageBrev, ByteArray>
    fun journalførBrev(request: LagBrevRequest, sakId: UUID): Either<KunneIkkeJournalføreBrev, JournalpostId>
    fun distribuerBrev(journalpostId: JournalpostId): Either<KunneIkkeDistribuereBrev, BrevbestillingId>
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

object KunneIkkeDistribuereBrev
