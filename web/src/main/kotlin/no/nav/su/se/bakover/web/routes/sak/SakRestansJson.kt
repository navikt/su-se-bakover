package no.nav.su.se.bakover.web.routes.sak

import no.nav.su.se.bakover.domain.sak.SakRestans

internal data class SakRestansJson(
    val saksnummer: String,
    val behandlingId: String,
    val typeBehandling: String,
    val status: String,
    val opprettet: String,
) {
    companion object {
        fun List<SakRestans>.toJson() = this.map {
            SakRestansJson(
                saksnummer = it.saksnummer.toString(),
                behandlingId = it.behandlingsId.toString(),
                typeBehandling = it.restansType.toString(),
                status = it.status.toString(),
                opprettet = it.opprettet.toString(),
            )
        }
    }
}
