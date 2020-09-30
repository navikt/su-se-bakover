package no.nav.su.se.bakover.service.behandling

import arrow.core.Either
import no.nav.su.se.bakover.database.behandling.BehandlingRepo
import no.nav.su.se.bakover.database.hendelseslogg.HendelsesloggRepo
import no.nav.su.se.bakover.domain.Attestant
import no.nav.su.se.bakover.domain.Behandling

internal class BehandlingServiceImpl(
    private val behandlingRepo: BehandlingRepo,
    private val hendelsesloggRepo: HendelsesloggRepo
) : BehandlingService {

    override fun underkjenn(
        begrunnelse: String,
        attestant: Attestant,
        behandling: Behandling
    ): Either<Behandling.KunneIkkeUnderkjenne, Behandling> {
        return behandling.underkjenn(begrunnelse, attestant)
            .mapLeft { it }
            .map {
                hendelsesloggRepo.oppdaterHendelseslogg(it.hendelseslogg)
                behandlingRepo.hentBehandling(it.id)!!
            }
    }
}
