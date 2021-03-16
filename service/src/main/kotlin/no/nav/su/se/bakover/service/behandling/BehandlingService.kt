package no.nav.su.se.bakover.service.behandling

import arrow.core.Either
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.domain.behandling.Behandling
import no.nav.su.se.bakover.service.revurdering.RevurderingService
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingService
import java.util.UUID

/**
 * Overbygg over alle behandlingstypene. I.e. Søknadsbehandling og Revurdering
 */
class BehandlingService(
    private val søknadsbehandlingService: SøknadsbehandlingService,
    private val revurderingService: RevurderingService,
) {
    fun hentBehandling(behandlingId: UUID): Either<FantIkkeBehandling, Behandling> {
        return søknadsbehandlingService.hent(SøknadsbehandlingService.HentRequest(behandlingId)).getOrHandle {
            revurderingService.hentRevurdering(behandlingId).getOrHandle {
                return FantIkkeBehandling.left()
            }
        }.right()
    }
    object FantIkkeBehandling
}
