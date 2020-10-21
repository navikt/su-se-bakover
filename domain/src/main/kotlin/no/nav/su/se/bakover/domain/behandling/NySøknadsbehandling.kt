package no.nav.su.se.bakover.domain.behandling

import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.domain.Behandling.BehandlingsStatus
import no.nav.su.se.bakover.domain.Behandling.BehandlingsStatus.OPPRETTET
import java.util.UUID

data class NySøknadsbehandling(
    val id: UUID = UUID.randomUUID(),
    val opprettet: Tidspunkt = Tidspunkt.now(),
    val sakId: UUID,
    val søknadId: UUID
) {
    val status: BehandlingsStatus = OPPRETTET
    val behandlingsinformasjon: Behandlingsinformasjon = Behandlingsinformasjon.lagTomBehandlingsinformasjon()
}
