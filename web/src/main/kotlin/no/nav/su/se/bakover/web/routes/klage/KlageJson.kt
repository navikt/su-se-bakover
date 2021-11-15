package no.nav.su.se.bakover.web.routes.klage

import no.nav.su.se.bakover.domain.klage.Klage

data class KlageJson(
    val id: String,
    val sakid: String,
    val opprettet: String,
)

internal fun Klage.toJson() = KlageJson(
    id = this.id.toString(),
    sakid = this.sakId.toString(),
    opprettet = this.opprettet.toString(),
)
