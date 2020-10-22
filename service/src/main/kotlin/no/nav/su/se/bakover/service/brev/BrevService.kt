package no.nav.su.se.bakover.service.brev

import arrow.core.Either
import no.nav.su.se.bakover.domain.Behandling
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.brev.Brevinnhold
import java.util.UUID

interface BrevService {

    fun lagBrev(brevinnhold: Brevinnhold): Either<KunneIkkeLageBrev, ByteArray>
    fun journalførBrev(brevinnhold: Brevinnhold, sakId: UUID): Either<KunneIkkeJournalføreBrev, String>
    fun distribuerBrev(journalPostId: String): Either<KunneIkkeDistribuereBrev, String>

    fun journalførVedtakOgSendBrev(
        sak: Sak,
        behandling: Behandling
    ): Either<KunneIkkeOppretteJournalpostOgSendeBrev, String>
}

sealed class KunneIkkeLageBrev {
    object FantIkkePerson : KunneIkkeLageBrev()
    object FantIkkeSak : KunneIkkeLageBrev()
    object KunneIkkeGenererePdf : KunneIkkeLageBrev()
}

object KunneIkkeDistribuereBrev
object KunneIkkeOppretteJournalpostOgSendeBrev

sealed class KunneIkkeJournalføreBrev {
    object FantIkkePerson : KunneIkkeJournalføreBrev()
    object FantIkkeSak : KunneIkkeJournalføreBrev()
    object KunneIkkeGenererePdf : KunneIkkeJournalføreBrev()
    object KunneIkkeOppretteJournalpost : KunneIkkeJournalføreBrev()
}
