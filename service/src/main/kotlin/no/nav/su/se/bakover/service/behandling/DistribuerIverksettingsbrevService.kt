package no.nav.su.se.bakover.service.behandling

import arrow.core.Either
import arrow.core.right
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

    internal fun distribuerBrev(behandling: Behandling, incrementMetrics: () -> Unit): Either<KunneIkkeDistribuereBrev, Behandling> {
        if (behandling.iverksattBrevbestillingId() != null) {
            log.info("Behandling ${behandling.id} har allerede distribuert en brevbestilling ${behandling.iverksattBrevbestillingId()}")
            return behandling.right()
        }
        return brevService.distribuerBrev(behandling.iverksattJournalpostId()!!)
            .mapLeft {
                log.error("Kunne ikke bestille brev ved iverksetting for behandling ${behandling.id}.")
                KunneIkkeDistribuereBrev
            }
            .map { brevbestillingId ->
                behandling.oppdaterIverksattBrevbestillingId(brevbestillingId).also {
                    behandlingRepo.oppdaterIverksattBrevbestillingId(it.id, brevbestillingId)
                    incrementMetrics()
                    log.info("Bestilt iverksettingsbrev for behandling ${it.id} med bestillingsid $brevbestillingId")
                }
            }
    }
}
