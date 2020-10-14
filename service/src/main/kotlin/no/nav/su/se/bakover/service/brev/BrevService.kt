package no.nav.su.se.bakover.service.brev

import arrow.core.Either
import no.nav.su.se.bakover.client.ClientError
import no.nav.su.se.bakover.domain.Behandling
import no.nav.su.se.bakover.domain.Sak
import java.util.UUID

interface BrevService {
    fun journalførVedtakOgSendBrev(
        sak: Sak,
        behandling: Behandling
    ): Either<KunneIkkeOppretteJournalpostOgSendeBrev, String>
    fun lagUtkastTilBrev(behandling: Behandling): Either<ClientError, ByteArray>
    fun journalførLukketSøknadOgSendBrev(
        sakId: UUID,
        søknadId: UUID
    ): Either<KunneIkkeOppretteJournalpostOgSendeBrev, String>
}

object KunneIkkeOppretteJournalpostOgSendeBrev
