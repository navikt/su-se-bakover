package common.presentation.attestering

import no.nav.su.se.bakover.common.domain.Attestering
import no.nav.su.se.bakover.common.domain.Attesteringshistorikk
import no.nav.su.se.bakover.common.tid.Tidspunkt

data class AttesteringJson(
    val attestant: String,
    val underkjennelse: UnderkjennelseJson?,
    val opprettet: Tidspunkt,
) {
    companion object {
        fun Attesteringshistorikk.toJson() = this.map { it.toJson() }

        fun Attestering.toJson() =
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

data class UnderkjennelseJson(
    val grunn: String,
    val kommentar: String,
)
