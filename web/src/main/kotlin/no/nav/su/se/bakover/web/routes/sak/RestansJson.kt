package no.nav.su.se.bakover.web.routes.sak

import no.nav.su.se.bakover.domain.behandling.Restans

internal data class RestansJson(
    val saksnummer: String,
    val behandlingId: String,
    val typeBehandling: String,
    val status: String,
    val opprettet: String,
) {
    companion object {
        fun List<Restans>.toJson() = this.map {
            RestansJson(
                saksnummer = it.saksnummer.toString(),
                behandlingId = it.behandlingsId.toString(),
                typeBehandling = it.restansType.toString(),
                status = it.status.toString(),
                opprettet = it.opprettet.toString(),
            )
        }
    }
}
