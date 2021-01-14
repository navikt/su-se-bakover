package no.nav.su.se.bakover.service.behandling

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.database.behandling.BehandlingRepo
import no.nav.su.se.bakover.domain.behandling.Behandling
import no.nav.su.se.bakover.domain.brev.LagBrevRequest
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.service.brev.BrevService

class JournalførIverksettingService(
    private val behandlingRepo: BehandlingRepo,
    private val brevService: BrevService
) {
    fun opprettJournalpost(
        behandling: Behandling,
        lagBrevRequest: LagBrevRequest,
    ): Either<KunneIkkeIverksetteBehandling.KunneIkkeJournalføreBrev, JournalpostId> {
        val journalpostId = brevService.journalførBrev(
            lagBrevRequest,
            behandling.saksnummer,
        ).map {
            behandling.oppdaterIverksattJournalpostId(it)
            behandlingRepo.oppdaterIverksattJournalpostId(behandling.id, it)
            it
        }.getOrElse {
            return KunneIkkeIverksetteBehandling.KunneIkkeJournalføreBrev.left()
        }
        return journalpostId.right()
    }
}
