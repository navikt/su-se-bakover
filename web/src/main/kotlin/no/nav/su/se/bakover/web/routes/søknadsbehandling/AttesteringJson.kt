package no.nav.su.se.bakover.web.routes.søknadsbehandling

import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.behandling.AttesteringHistorik

internal data class AttesteringJson(
    val attestant: String,
    val underkjennelse: UnderkjennelseJson?
) {
    companion object {
        internal fun AttesteringHistorik.toJson() = this.hentAttesteringer().map { it.toJson() }

        internal fun Attestering.toJson() =
            when (this) {
                is Attestering.Iverksatt -> AttesteringJson(
                    attestant = this.attestant.navIdent,
                    underkjennelse = null,
                )
                is Attestering.Underkjent -> AttesteringJson(
                    attestant = this.attestant.navIdent,
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
