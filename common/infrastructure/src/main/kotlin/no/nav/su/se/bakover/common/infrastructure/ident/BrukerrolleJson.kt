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
    ;

    fun toNavIdentBruker(ident: String): NavIdentBruker {
        return when (this) {
            VEILEDER -> NavIdentBruker.Veileder(ident)
            SAKSBEHANDLER -> NavIdentBruker.Saksbehandler(ident)
            ATTESTANT -> NavIdentBruker.Attestant(ident)
            DRIFT -> NavIdentBruker.Drift(ident)
        }
    }

    fun toBrukerrolle(): Brukerrolle {
        return when (this) {
            VEILEDER -> Brukerrolle.Veileder
            SAKSBEHANDLER -> Brukerrolle.Saksbehandler
            ATTESTANT -> Brukerrolle.Attestant
            DRIFT -> Brukerrolle.Drift
        }
    }

    companion object {
        fun NavIdentBruker.toBrukerrolleJson(): BrukerrolleJson {
            return when (this) {
                is NavIdentBruker.Attestant -> ATTESTANT
                is NavIdentBruker.Saksbehandler -> SAKSBEHANDLER
                is NavIdentBruker.Veileder -> VEILEDER
                is NavIdentBruker.Drift -> DRIFT
            }
        }

        fun Brukerrolle.toBrukerrolleJson(): BrukerrolleJson {
            return when (this) {
                Brukerrolle.Attestant -> ATTESTANT
                Brukerrolle.Saksbehandler -> SAKSBEHANDLER
                Brukerrolle.Veileder -> VEILEDER
                Brukerrolle.Drift -> DRIFT
            }
        }
    }
}

fun List<Brukerrolle>.toBrukerrollerJson(): List<BrukerrolleJson> = this.map { it.toBrukerrolleJson() }
fun List<BrukerrolleJson>.toBrukerroller(): List<Brukerrolle> = this.map { it.toBrukerrolle() }
