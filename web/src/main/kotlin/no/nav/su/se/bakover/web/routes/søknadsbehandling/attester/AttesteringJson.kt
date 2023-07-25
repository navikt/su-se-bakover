package no.nav.su.se.bakover.web.routes.søknadsbehandling.attester

import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.behandling.Attesteringshistorikk

internal data class AttesteringJson(
    val attestant: String,
    val underkjennelse: UnderkjennelseJson?,
    val opprettet: Tidspunkt,
) {
    companion object {
        internal fun Attesteringshistorikk.toJson() = this.map { it.toJson() }

        internal fun Attestering.toJson() =
            when (this) {
                is Attestering.Iverksatt -> AttesteringJson(
                    attestant = this.attestant.navIdent,
                    opprettet = this.opprettet,
                    underkjennelse = null,
                )
                is Attestering.Underkjent -> AttesteringJson(
                    attestant = this.attestant.navIdent,
                    opprettet = this.opprettet,
                    underkjennelse = UnderkjennelseJson(
                        grunn = this.grunn.toString(),
                        kommentar = this.kommentar,
                    ),
                )
            }
    }
}

internal data class UnderkjennelseJson(
    val grunn: String,
    val kommentar: String,
)
