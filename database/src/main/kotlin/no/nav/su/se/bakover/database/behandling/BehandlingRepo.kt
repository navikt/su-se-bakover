package no.nav.su.se.bakover.database.behandling

import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.domain.Attestant
import no.nav.su.se.bakover.domain.Behandling
import no.nav.su.se.bakover.domain.Saksbehandler
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import java.util.UUID

interface BehandlingRepo {
    fun hentBehandling(behandlingId: UUID): Behandling?
    fun oppdaterBehandlingsinformasjon(behandlingId: UUID, behandlingsinformasjon: Behandlingsinformasjon): Behandling
    fun oppdaterBehandlingStatus(behandlingId: UUID, status: Behandling.BehandlingsStatus): Behandling
    fun leggTilUtbetaling(behandlingId: UUID, utbetalingId: UUID30): Behandling
    fun settSaksbehandler(behandlingId: UUID, saksbehandler: Saksbehandler): Behandling
    fun attester(behandlingId: UUID, attestant: Attestant): Behandling
    fun opprettSøknadsbehandling(sakId: UUID, behandling: Behandling): Behandling
    fun harSøknadBehandling(søknadId: UUID): Boolean
}
