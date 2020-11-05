package no.nav.su.se.bakover.domain.behandling

import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.domain.behandling.Behandling.BehandlingsStatus
import no.nav.su.se.bakover.domain.behandling.Behandling.BehandlingsStatus.OPPRETTET
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import java.util.UUID

data class NySøknadsbehandling(
    val id: UUID = UUID.randomUUID(),
    val opprettet: Tidspunkt = Tidspunkt.now(),
    val sakId: UUID,
    val søknadId: UUID,
    val oppgaveId: OppgaveId
) {
    val status: BehandlingsStatus = OPPRETTET
    val behandlingsinformasjon: Behandlingsinformasjon = Behandlingsinformasjon.lagTomBehandlingsinformasjon()
}
