package no.nav.su.se.bakover.hendelse.infrastructure.persistence

import no.nav.su.se.bakover.common.NavIdentBruker

internal enum class IdentRolle {
    VEILEDER, SAKSBEHANDLER, ATTESTANT;

    fun toDomain(ident: String): NavIdentBruker {
        return when (this) {
            VEILEDER -> NavIdentBruker.Veileder(ident)
            SAKSBEHANDLER -> NavIdentBruker.Saksbehandler(ident)
            ATTESTANT -> NavIdentBruker.Attestant(ident)
        }
    }

    companion object {
        fun NavIdentBruker.toIdentRolle(): IdentRolle {
            return when (this) {
                is NavIdentBruker.Attestant -> ATTESTANT
                is NavIdentBruker.Saksbehandler -> SAKSBEHANDLER
                is NavIdentBruker.Veileder -> VEILEDER
            }
        }
    }
}
