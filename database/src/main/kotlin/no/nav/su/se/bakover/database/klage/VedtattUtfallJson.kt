package no.nav.su.se.bakover.database.klage

import no.nav.su.se.bakover.common.Tidspunkt
import java.util.UUID

internal data class VedtattUtfallJson(
    val id: UUID,
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
