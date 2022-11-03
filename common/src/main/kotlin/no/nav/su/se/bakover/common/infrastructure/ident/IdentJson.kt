package no.nav.su.se.bakover.common.infrastructure.ident

import no.nav.su.se.bakover.common.NavIdentBruker
import no.nav.su.se.bakover.common.infrastructure.ident.BrukerrolleJson.Companion.toBrukerrolleJson

/**
 * Brukes på tvers av funksjonalitet for å serialisere/deserialisere json.
 * Hvis den gjør ikke-bakoverkompatible endringer må man migrere database og oppdatere su-se-framover og andre klienter.
 */
data class IdentJson(
    val ident: String,
    val rolle: BrukerrolleJson,
) {
    fun toDomain(): NavIdentBruker {
        return rolle.toNavIdentBruker(this.ident)
    }

    companion object {
        fun NavIdentBruker.toIdentJson(): IdentJson {
            return IdentJson(
                ident = this.navIdent,
                rolle = this.toBrukerrolleJson(),
            )
        }
    }
}
