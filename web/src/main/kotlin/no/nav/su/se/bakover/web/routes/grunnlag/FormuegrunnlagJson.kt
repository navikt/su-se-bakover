package no.nav.su.se.bakover.web.routes.grunnlag

import no.nav.su.se.bakover.domain.grunnlag.Formuegrunnlag

// Bruk FormuevilkårJson e.l. for å legge på utledede verdier som 0.5G, hva 0.5G tilsvarer i kroner og om vilkår er oppfylt.
data class FormuegrunnlagJson(
    val epsFormue: VerdierJson?,
    val søkersFormue: VerdierJson,
) {
    data class VerdierJson(
        val verdiIkkePrimærbolig: Int,
        val verdiEiendommer: Int,
        val verdiKjøretøy: Int,
        val innskudd: Int,
        val verdipapir: Int,
        val pengerSkyldt: Int,
        val kontanter: Int,
        val depositumskonto: Int,
    ) {
        fun toDomain(): Formuegrunnlag.Verdier {
            return Formuegrunnlag.Verdier(
                verdiIkkePrimærbolig = verdiIkkePrimærbolig,
                verdiEiendommer = verdiEiendommer,
                verdiKjøretøy = verdiKjøretøy,
                innskudd = innskudd,
                verdipapir = verdipapir,
                pengerSkyldt = pengerSkyldt,
                kontanter = kontanter,
                depositumskonto = depositumskonto,
            )
        }
    }
}
