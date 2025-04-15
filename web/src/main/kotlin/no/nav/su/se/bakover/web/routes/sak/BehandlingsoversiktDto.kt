package no.nav.su.se.bakover.web.routes.sak

import no.nav.su.se.bakover.common.domain.sak.Behandlingssammendrag
import no.nav.su.se.bakover.common.infrastructure.PeriodeJson
import no.nav.su.se.bakover.common.infrastructure.PeriodeJson.Companion.toJson

internal data class BehandlingsoversiktDto(
    val sakType: String,
    val saksnummer: String,
    val typeBehandling: String,
    val periode: PeriodeJson?,
    val status: String?,
    val behandlingStartet: String?,
) {
    companion object {
        fun List<Behandlingssammendrag>.toDto() = this.map {
            BehandlingsoversiktDto(
                sakType = it.sakType.name,
                saksnummer = it.saksnummer.toString(),
                typeBehandling = it.behandlingstype.name,
                status = it.status?.toString(),
                behandlingStartet = it.behandlingStartet?.toString(),
                periode = it.periode?.toJson(),
            )
        }
    }
}
