package no.nav.su.se.bakover.service.brev

import arrow.core.Either
import no.nav.su.se.bakover.client.ClientError
import no.nav.su.se.bakover.domain.Behandling
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.Søknad
import java.util.UUID

interface BrevService {
    fun lagUtkastTilBrev(behandling: Behandling): Either<ClientError, ByteArray>
    fun journalførVedtakOgSendBrev(
        sak: Sak,
        behandling: Behandling
    ): Either<KunneIkkeOppretteJournalpostOgSendeBrev, String>

    fun journalførLukketSøknadOgSendBrev(
        sakId: UUID,
        søknadId: UUID
    ): Either<KunneIkkeOppretteJournalpostOgSendeBrev, String>

    fun lagLukketSøknadBrevUtkast(
        sakId: UUID,
        typeLukking: Søknad.TypeLukking
    ): Either<ClientError, ByteArray>
}

object KunneIkkeOppretteJournalpostOgSendeBrev
