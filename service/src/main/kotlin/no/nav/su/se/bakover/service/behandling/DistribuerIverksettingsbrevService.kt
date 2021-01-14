package no.nav.su.se.bakover.service.behandling

import arrow.core.Either
import no.nav.su.se.bakover.database.behandling.BehandlingRepo
import no.nav.su.se.bakover.domain.behandling.Behandling
import no.nav.su.se.bakover.service.brev.BrevService
import org.slf4j.LoggerFactory

class DistribuerIverksettingsbrevService(
    private val brevService: BrevService,
    private val behandlingRepo: BehandlingRepo,
) {
    private val log = LoggerFactory.getLogger(this::class.java)

    object KunneIkkeDistribuereBrev

    internal fun distribuerBrev(behandling: Behandling): Either<KunneIkkeDistribuereBrev, Behandling> {
        return brevService.distribuerBrev(behandling.iverksattJournalpostId()!!)
            .mapLeft {
                log.error("Kunne ikke bestille brev ved iverksetting for behandling ${behandling.id}.")
                KunneIkkeDistribuereBrev
            }
            .map {
                behandling.oppdaterIverksattBrevbestillingId(it)
                behandlingRepo.oppdaterIverksattBrevbestillingId(behandling.id, it)
                log.info("Bestilt iverksettingsbrev for behandling ${behandling.id} med bestillingsid $it")
                behandling
            }
    }
}
