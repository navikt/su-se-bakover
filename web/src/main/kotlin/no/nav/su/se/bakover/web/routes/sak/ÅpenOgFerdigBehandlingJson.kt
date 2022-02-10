package no.nav.su.se.bakover.web.routes.sak

import no.nav.su.se.bakover.domain.sak.SakBehandlinger

internal data class ÅpenOgFerdigBehandlingJson(
    val saksnummer: String,
    val behandlingId: String,
    val typeBehandling: String,
    val status: String,
    val behandlingStartet: String?,
) {
    companion object {
        fun List<SakBehandlinger>.toJson() = this.map {
            ÅpenOgFerdigBehandlingJson(
                saksnummer = it.saksnummer.toString(),
                behandlingId = it.behandlingsId.toString(),
                typeBehandling = it.restansType.toString(),
                status = when (it) {
                    is SakBehandlinger.ÅpenBehandling -> it.status.toString()
                    is SakBehandlinger.FerdigBehandling -> it.result.toString()
                },
                behandlingStartet = it.behandlingStartet?.toString(),
            )
        }
    }
}
