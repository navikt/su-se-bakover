package no.nav.su.se.bakover.service.brev

import arrow.core.Either
import no.nav.su.se.bakover.domain.Behandling
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.brev.Brevinnhold
import java.util.UUID

interface BrevService {

    fun lagBrev(brevinnhold: Brevinnhold): Either<KunneIkkeLageBrev, ByteArray>

    fun journalførVedtakOgSendBrev(
        sak: Sak,
        behandling: Behandling
    ): Either<KunneIkkeOppretteJournalpostOgSendeBrev, String>

    fun journalførLukketSøknadOgSendBrev(
        sakId: UUID,
        søknad: Søknad,
        lukketSøknad: Søknad.Lukket
    ): Either<KunneIkkeOppretteJournalpostOgSendeBrev, String>
}

sealed class KunneIkkeLageBrev {
    object FantIkkePerson : KunneIkkeLageBrev()
    object FantIkkeSak : KunneIkkeLageBrev()
    object KunneIkkeGenererePdf : KunneIkkeLageBrev()
}

object KunneIkkeOppretteJournalpostOgSendeBrev
