package no.nav.su.se.bakover.database.notat

import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.domain.notat.NotatHandling
import no.nav.su.se.bakover.domain.notat.NotatHendelse

internal data class NotatHendelserJson(
    val tidspunkt: Tidspunkt,
    val navIdent: String,
    val handling: String,
    val rolle: String? = null,
    val hvasomerEndret: String? = null,
) {
    fun toDomain(): NotatHendelse = NotatHendelse(
        navIdent = when (rolle) {
            NotatHendelseRolle.ATTESTANT.name -> NavIdentBruker.Attestant(navIdent)
            NotatHendelseRolle.SAKSBEHANDLER.name, null -> NavIdentBruker.Saksbehandler(navIdent)
            else -> error("Ukjent rolle for notathendelse: $rolle")
        },
        tidspunkt = tidspunkt,
        handling = NotatHandling.valueOf(handling),
        hvasomerEndret = hvasomerEndret,
    )

    companion object {
        fun List<NotatHendelse>.toJson(): List<NotatHendelserJson> = map {
            NotatHendelserJson(
                tidspunkt = it.tidspunkt,
                navIdent = it.navIdent.navIdent,
                handling = it.handling.name,
                rolle = when (it.navIdent) {
                    is NavIdentBruker.Attestant -> NotatHendelseRolle.ATTESTANT.name
                    is NavIdentBruker.Saksbehandler -> NotatHendelseRolle.SAKSBEHANDLER.name
                    else -> error("Støtter ikke rollen ${it.navIdent::class.simpleName} for notathendelser")
                },
                hvasomerEndret = it.hvasomerEndret,
            )
        }
    }
}

private enum class NotatHendelseRolle {
    ATTESTANT,
    SAKSBEHANDLER,
}
