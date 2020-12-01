package no.nav.su.se.bakover.domain.behandling

import no.nav.su.se.bakover.domain.NavIdentBruker

data class Attestering(
    val attestant: NavIdentBruker.Attestant,
    val underkjennelsesGrunn: UnderkjennelsesGrunn? = null,
) {
    data class UnderkjennelsesGrunn(
        val grunn: UnderkjentGrunn,
        val kommentar: String?
    ) {
        enum class UnderkjentGrunn {
            INNGANGSVILKÅRENE_ER_FEILVURDERT,
            BEREGNINGEN_ER_FEIL,
            DOKUMENTASJON_MANGLER_VEDTAKSBREVET_ER_FEIL,
            ANDRE_FORHOLD,
        }
    }
}
