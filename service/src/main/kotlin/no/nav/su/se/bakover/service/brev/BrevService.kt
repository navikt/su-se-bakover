package no.nav.su.se.bakover.service.brev

import arrow.core.Either
import no.nav.su.se.bakover.domain.Behandling
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.Søknad
import java.util.UUID

interface BrevService {
    fun lagUtkastTilBrev(behandling: Behandling): Either<KunneIkkeLageBrev, ByteArray>
    fun journalførVedtakOgSendBrev(
        sak: Sak,
        behandling: Behandling
    ): Either<KunneIkkeOppretteJournalpostOgSendeBrev, String>

    fun journalførLukketSøknadOgSendBrev(
        sakId: UUID,
        søknad: Søknad,
        lukketSøknadBody: Søknad.LukketSøknadBody
    ): Either<KunneIkkeOppretteJournalpostOgSendeBrev, String>

    fun lagLukketSøknadBrevutkast(
        sakId: UUID,
        søknad: Søknad,
        lukketSøknadBody: Søknad.LukketSøknadBody
    ): Either<KunneIkkeLageBrev, ByteArray>
}

sealed class KunneIkkeLageBrev {
    object FantIkkePerson : KunneIkkeLageBrev()
    object FantIkkeSak : KunneIkkeLageBrev()
    object KunneIkkeGenererePdf : KunneIkkeLageBrev()
}

object KunneIkkeOppretteJournalpostOgSendeBrev
