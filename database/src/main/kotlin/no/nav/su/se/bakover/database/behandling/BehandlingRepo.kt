package no.nav.su.se.bakover.database.behandling

import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.domain.Behandling
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.behandling.NySøknadsbehandling
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import java.util.UUID

interface BehandlingRepo {
    fun hentBehandling(behandlingId: UUID): Behandling?
    fun oppdaterBehandlingsinformasjon(behandlingId: UUID, behandlingsinformasjon: Behandlingsinformasjon): Behandling
    fun oppdaterBehandlingStatus(behandlingId: UUID, status: Behandling.BehandlingsStatus): Behandling
    fun leggTilUtbetaling(behandlingId: UUID, utbetalingId: UUID30): Behandling
    fun leggTilSimulering(behandlingId: UUID, simulering: Simulering)
    fun settSaksbehandler(behandlingId: UUID, saksbehandler: NavIdentBruker.Saksbehandler): Behandling
    fun attester(behandlingId: UUID, attestant: NavIdentBruker.Attestant): Behandling
    fun opprettSøknadsbehandling(nySøknadsbehandling: NySøknadsbehandling)
}
