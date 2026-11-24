package no.nav.su.se.bakover.common.infrastructure.ident

import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.infrastructure.ident.BrukerrolleJson.Companion.toBrukerrolleJson

/**
 * Brukes på tvers av funksjonalitet for å serialisere/deserialisere json.
 * Hvis man gjør ikke-bakoverkompatible endringer må man migrere database og oppdatere su-se-framover og andre klienter.
 */
enum class BrukerrolleJson {
    VEILEDER,
    SAKSBEHANDLER,
    ATTESTANT,
    DRIFT,
    STATISTIKK,
    ;

    fun toNavIdentBruker(ident: String): NavIdentBruker {
        return when (this) {
            VEILEDER -> NavIdentBruker.Veileder(ident)
            SAKSBEHANDLER -> NavIdentBruker.Saksbehandler(ident)
            ATTESTANT -> NavIdentBruker.Attestant(ident)
            DRIFT -> NavIdentBruker.Drift(ident)
            STATISTIKK -> NavIdentBruker.Statistikk(ident)
        }
    }
    companion object {
        fun NavIdentBruker.toBrukerrolleJson(): BrukerrolleJson {
            return when (this) {
                is NavIdentBruker.Attestant -> ATTESTANT
                is NavIdentBruker.Saksbehandler -> SAKSBEHANDLER
                is NavIdentBruker.Veileder -> VEILEDER
                is NavIdentBruker.Drift -> DRIFT
                is NavIdentBruker.Statistikk -> STATISTIKK
            }
        }

        fun Brukerrolle.toBrukerrolleJson(): BrukerrolleJson {
            return when (this) {
                Brukerrolle.Attestant -> ATTESTANT
                Brukerrolle.Saksbehandler -> SAKSBEHANDLER
                Brukerrolle.Veileder -> VEILEDER
                Brukerrolle.Drift -> DRIFT
                Brukerrolle.Statistikk -> STATISTIKK
            }
        }
    }
}

fun List<Brukerrolle>.toBrukerrollerJson(): List<BrukerrolleJson> = this.map { it.toBrukerrolleJson() }
