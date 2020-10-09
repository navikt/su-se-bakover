package no.nav.su.se.bakover.service.brev

import arrow.core.Either
import no.nav.su.se.bakover.client.ClientError
import no.nav.su.se.bakover.domain.Behandling
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.TrukketSøknadBody

interface BrevService {
    fun journalførVedtakOgSendBrev(
        sak: Sak,
        behandling: Behandling
    ): Either<BrevServiceImpl.KunneIkkeOppretteJournalpostOgSendeBrev, String>
    fun lagUtkastTilBrev(behandling: Behandling): Either<ClientError, ByteArray>
    fun journalførTrukketSøknadOgSendBrev(
        trukketSøknadBody: TrukketSøknadBody
    ): Either<BrevServiceImpl.KunneIkkeOppretteJournalpostOgSendeBrev, String>
}
