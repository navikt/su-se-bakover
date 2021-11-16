package no.nav.su.se.bakover.web.routes.klage

import no.nav.su.se.bakover.domain.klage.Klage
import no.nav.su.se.bakover.domain.klage.OpprettetKlage
import no.nav.su.se.bakover.web.routes.klage.Typer.Companion.frontendStatus

data class KlageJson(
    val id: String,
    val sakid: String,
    val opprettet: String,
    val status: String,
)

internal fun Klage.toJson() = KlageJson(
    id = this.id.toString(),
    sakid = this.sakId.toString(),
    opprettet = this.opprettet.toString(),
    status = this.frontendStatus()
)

private enum class Typer(val verdi: String) {
    OPPRETTET("OPPRETTET");

    companion object {
        fun Klage.frontendStatus(): String {
            return when (this) {
                is OpprettetKlage -> OPPRETTET
            }.toString()
        }
    }
    override fun toString() = verdi
}
