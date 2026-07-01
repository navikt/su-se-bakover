package no.nav.su.se.bakover.kontrollsamtale.infrastructure.persistence

import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.kontrollsamtale.domain.KontrollsamtaleHandling
import no.nav.su.se.bakover.kontrollsamtale.domain.KontrollsamtaleHendelse

internal data class KontrollsamtaleHendelserJson(
    val tidspunkt: Tidspunkt,
    val navIdent: String,
    val handling: String,
    val rolle: String? = null,
) {
    fun toDomain(): KontrollsamtaleHendelse {
        return KontrollsamtaleHendelse(
            navIdent = when (rolle) {
                KontrollsamtaleHendelseRolleJson.ATTESTANT.name -> NavIdentBruker.Attestant(navIdent)
                KontrollsamtaleHendelseRolleJson.SAKSBEHANDLER.name, null -> NavIdentBruker.Saksbehandler(navIdent)
                KontrollsamtaleHendelseRolleJson.VEILEDER.name -> NavIdentBruker.Veileder(navIdent)
                KontrollsamtaleHendelseRolleJson.DRIFT.name -> NavIdentBruker.Drift(navIdent)
                else -> error("Ukjent rolle for kontrollsamtalehendelse: $rolle")
            },
            tidspunkt = tidspunkt,
            handling = KontrollsamtaleHandling.valueOf(handling),
        )
    }

    companion object {
        fun List<KontrollsamtaleHendelse>.toJson(): List<KontrollsamtaleHendelserJson> = map {
            KontrollsamtaleHendelserJson(
                tidspunkt = it.tidspunkt,
                navIdent = it.navIdent.navIdent,
                handling = it.handling.name,
                rolle = when (it.navIdent) {
                    is NavIdentBruker.Attestant -> KontrollsamtaleHendelseRolleJson.ATTESTANT.name
                    is NavIdentBruker.Saksbehandler -> KontrollsamtaleHendelseRolleJson.SAKSBEHANDLER.name
                    is NavIdentBruker.Veileder -> KontrollsamtaleHendelseRolleJson.VEILEDER.name
                    is NavIdentBruker.Drift -> KontrollsamtaleHendelseRolleJson.DRIFT.name
                },
            )
        }
    }
}

private enum class KontrollsamtaleHendelseRolleJson {
    ATTESTANT,
    SAKSBEHANDLER,
    VEILEDER,
    DRIFT,
}
