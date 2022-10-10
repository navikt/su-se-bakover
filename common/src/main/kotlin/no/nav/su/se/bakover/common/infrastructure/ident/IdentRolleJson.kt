package no.nav.su.se.bakover.common.infrastructure.ident

import no.nav.su.se.bakover.common.NavIdentBruker

/**
 * Brukes på tvers av funksjonalitet for å serialisere/deserialisere json.
 * Hvis den gjør ikke-bakoverkompatible endringer må man migrere database og oppdatere su-se-framover og andre klienter.
 */
enum class IdentRolleJson {
    VEILEDER, SAKSBEHANDLER, ATTESTANT;

    fun toDomain(ident: String): NavIdentBruker {
        return when (this) {
            VEILEDER -> NavIdentBruker.Veileder(ident)
            SAKSBEHANDLER -> NavIdentBruker.Saksbehandler(ident)
            ATTESTANT -> NavIdentBruker.Attestant(ident)
        }
    }

    companion object {
        fun NavIdentBruker.toIdentRolleJson(): IdentRolleJson {
            return when (this) {
                is NavIdentBruker.Attestant -> ATTESTANT
                is NavIdentBruker.Saksbehandler -> SAKSBEHANDLER
                is NavIdentBruker.Veileder -> VEILEDER
            }
        }
    }
}
