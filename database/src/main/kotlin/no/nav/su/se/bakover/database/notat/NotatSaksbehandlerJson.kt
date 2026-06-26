package no.nav.su.se.bakover.database.notat

import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.domain.notat.NotatHandling
import no.nav.su.se.bakover.domain.notat.NotatHendelse

internal data class NotatSaksbehandlerJson(
    val tidspunkt: Tidspunkt,
    val navIdent: String,
    val handling: String,
) {
    fun toDomain(): NotatHendelse = NotatHendelse(
        navIdent = NavIdentBruker.Saksbehandler(navIdent),
        tidspunkt = tidspunkt,
        handling = NotatHandling.valueOf(handling),
    )

    companion object {
        fun List<NotatHendelse>.toJson(): List<NotatSaksbehandlerJson> = map {
            NotatSaksbehandlerJson(
                tidspunkt = it.tidspunkt,
                navIdent = it.navIdent.navIdent,
                handling = it.handling.name,
            )
        }
    }
}
