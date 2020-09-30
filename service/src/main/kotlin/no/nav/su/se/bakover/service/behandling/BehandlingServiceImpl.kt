package no.nav.su.se.bakover.service.behandling

import arrow.core.Either
import no.nav.su.se.bakover.database.ObjectRepo
import no.nav.su.se.bakover.database.behandling.BehandlingRepo
import no.nav.su.se.bakover.database.beregning.BeregningRepo
import no.nav.su.se.bakover.database.hendelseslogg.HendelsesloggRepo
import no.nav.su.se.bakover.domain.Attestant
import no.nav.su.se.bakover.domain.Behandling
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import java.util.UUID

internal class BehandlingServiceImpl(
    private val behandlingRepo: BehandlingRepo,
    private val hendelsesloggRepo: HendelsesloggRepo,
    private val beregningRepo: BeregningRepo,
    private val objectRepo: ObjectRepo // TODO dont use
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

    override fun oppdaterBehandlingsinformasjon(
        behandlingId: UUID,
        behandlingsinformasjon: Behandlingsinformasjon
    ): Behandling {
        val beforeUpdate = behandlingRepo.hentBehandling(behandlingId)!!
        beregningRepo.slettBeregningForBehandling(behandlingId)
        val updated = behandlingRepo.oppdaterBehandlingsinformasjon(
            behandlingId,
            beforeUpdate.behandlingsinformasjon().patch(behandlingsinformasjon)
        )
        // TODO fix weirdness for internal state
        val newstatus = updated.oppdaterBehandlingsinformasjon(behandlingsinformasjon).status()
        behandlingRepo.oppdaterBehandlingStatus(behandlingId, newstatus)
        return objectRepo.hentBehandling(behandlingId)!! // TODO just to add observers for tests and stuff until they are all gone
    }
}
