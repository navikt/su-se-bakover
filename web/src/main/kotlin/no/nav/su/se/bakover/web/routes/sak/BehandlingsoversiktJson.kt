package no.nav.su.se.bakover.web.routes.sak

import no.nav.su.se.bakover.common.domain.sak.Behandlingssammendrag
import no.nav.su.se.bakover.common.infrastructure.PeriodeJson
import no.nav.su.se.bakover.common.infrastructure.PeriodeJson.Companion.toJson

internal data class BehandlingsoversiktJson(
    val saksnummer: String,
    val behandlingId: String,
    val typeBehandling: String,
    val periode: PeriodeJson?,
    val status: String?,
    val behandlingStartet: String?,
) {
    companion object {
        fun List<Behandlingssammendrag>.toJson() = this.map {
            BehandlingsoversiktJson(
                saksnummer = it.saksnummer.toString(),
                behandlingId = it.behandlingsId.toString(),
                typeBehandling = it.behandlingstype.toString(),
                status = it.status?.toString(),
                behandlingStartet = it.behandlingStartet?.toString(),
                periode = it.periode?.toJson(),
            )
        }
    }
}
