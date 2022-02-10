package no.nav.su.se.bakover.web.routes.sak

import no.nav.su.se.bakover.domain.sak.Behandlingsoversikt

internal data class BehandlingsoversiktJson(
    val saksnummer: String,
    val behandlingId: String,
    val typeBehandling: String,
    val status: String,
    val behandlingStartet: String?,
) {
    companion object {
        fun List<Behandlingsoversikt>.toJson() = this.map {
            BehandlingsoversiktJson(
                saksnummer = it.saksnummer.toString(),
                behandlingId = it.behandlingsId.toString(),
                typeBehandling = it.behandlingstype.toString(),
                status = it.status.toString(),
                behandlingStartet = it.behandlingStartet?.toString(),
            )
        }
    }
}
