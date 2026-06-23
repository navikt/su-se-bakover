package no.nav.su.se.bakover.database.notat

import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.domain.notat.NotatSaksbehandler

internal data class NotatSaksbehandlerJson(
    val tidspunkt: Tidspunkt,
    val navIdent: String,
    val handling: String,
) {
    fun toDomain(): NotatSaksbehandler = NotatSaksbehandler(
        navIdent = NavIdentBruker.Saksbehandler(navIdent),
        tidspunkt = tidspunkt,
        handling = handling,
    )

    companion object {
        fun NotatSaksbehandler.toJson(): NotatSaksbehandlerJson = NotatSaksbehandlerJson(
            tidspunkt = tidspunkt,
            navIdent = navIdent.navIdent,
            handling = handling,
        )
    }
}
