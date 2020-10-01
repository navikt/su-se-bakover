package no.nav.su.se.bakover.service.behandling

import arrow.core.Either
import no.nav.su.se.bakover.domain.Attestant
import no.nav.su.se.bakover.domain.Behandling
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.beregning.Fradrag
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringFeilet
import java.time.LocalDate
import java.util.UUID

interface BehandlingService {
    fun underkjenn(begrunnelse: String, attestant: Attestant, behandling: Behandling): Either<Behandling.KunneIkkeUnderkjenne, Behandling>
    fun oppdaterBehandlingsinformasjon(behandlingId: UUID, behandlingsinformasjon: Behandlingsinformasjon): Behandling
    fun opprettBeregning(behandlingId: UUID, fom: LocalDate, tom: LocalDate, fradrag: List<Fradrag>): Behandling
    fun simuler(behandlingId: UUID): Either<SimuleringFeilet, Behandling>
}
