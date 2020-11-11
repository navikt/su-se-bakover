package no.nav.su.se.bakover.database.behandling

import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.behandling.Behandling
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.behandling.NySøknadsbehandling
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import java.util.UUID

interface BehandlingRepo {
    fun hentBehandling(behandlingId: UUID): Behandling?
    fun oppdaterBehandlingsinformasjon(behandlingId: UUID, behandlingsinformasjon: Behandlingsinformasjon)
    fun oppdaterBehandlingStatus(behandlingId: UUID, status: Behandling.BehandlingsStatus)
    fun leggTilUtbetaling(behandlingId: UUID, utbetalingId: UUID30)
    fun leggTilSimulering(behandlingId: UUID, simulering: Simulering)
    fun leggTilBeregning(behandlingId: UUID, beregning: Beregning)
    fun slettBeregning(behandlingId: UUID)
    fun settSaksbehandler(behandlingId: UUID, saksbehandler: NavIdentBruker.Saksbehandler)
    fun attester(behandlingId: UUID, attestant: NavIdentBruker.Attestant)
    fun opprettSøknadsbehandling(nySøknadsbehandling: NySøknadsbehandling)
}
