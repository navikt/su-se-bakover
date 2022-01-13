package no.nav.su.se.bakover.database.klage

import no.nav.su.se.bakover.common.Tidspunkt

internal data class VedtattUtfallJson(
    val opprettet: Tidspunkt,
    val utfallJson: UtfallJson
) {
    enum class UtfallJson() {
        TRUKKET,
        RETUR,
        OPPHEVET,
        MEDHOLD,
        DELVIS_MEDHOLD,
        STADFESTELSE,
        UGUNST,
        AVVIST
    }
}
