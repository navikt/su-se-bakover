package no.nav.su.se.bakover.service.behandling

import arrow.core.Either
import no.nav.su.se.bakover.domain.AktørId
import no.nav.su.se.bakover.domain.Attestant
import no.nav.su.se.bakover.domain.Behandling
import no.nav.su.se.bakover.domain.Saksbehandler
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.beregning.Fradrag
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringFeilet
import no.nav.su.se.bakover.domain.oppgave.KunneIkkeOppretteOppgave
import java.time.LocalDate
import java.util.UUID

interface BehandlingService {
    fun underkjenn(begrunnelse: String, attestant: Attestant, behandling: Behandling): Either<Behandling.KunneIkkeUnderkjenne, Behandling>
    fun oppdaterBehandlingsinformasjon(behandlingId: UUID, behandlingsinformasjon: Behandlingsinformasjon): Behandling
    fun opprettBeregning(behandlingId: UUID, fom: LocalDate, tom: LocalDate, fradrag: List<Fradrag>): Behandling
    fun simuler(behandlingId: UUID): Either<SimuleringFeilet, Behandling>
    fun sendTilAttestering(behandlingId: UUID, aktørId: AktørId, saksbehandler: Saksbehandler): Either<KunneIkkeOppretteOppgave, Behandling>
    fun iverksett(behandlingId: UUID, attestant: Attestant): Either<Behandling.IverksettFeil, Behandling>
}
