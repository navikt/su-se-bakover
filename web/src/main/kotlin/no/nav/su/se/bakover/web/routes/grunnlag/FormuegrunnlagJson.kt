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
    )
}

fun Formuegrunnlag.toJson() = FormuegrunnlagJson(
    epsFormue = this.epsFormue?.let {
        FormuegrunnlagJson.VerdierJson(
            verdiIkkePrimærbolig = it.verdiIkkePrimærbolig,
            verdiEiendommer = it.verdiEiendommer,
            verdiKjøretøy = it.verdiKjøretøy,
            innskudd = it.innskudd,
            verdipapir = it.verdipapir,
            pengerSkyldt = it.pengerSkyldt,
            kontanter = it.kontanter,
            depositumskonto = it.depositumskonto,
        )
    },
    søkersFormue = this.søkersFormue.let {
        FormuegrunnlagJson.VerdierJson(
            verdiIkkePrimærbolig = it.verdiIkkePrimærbolig,
            verdiEiendommer = it.verdiEiendommer,
            verdiKjøretøy = it.verdiKjøretøy,
            innskudd = it.innskudd,
            verdipapir = it.verdipapir,
            pengerSkyldt = it.pengerSkyldt,
            kontanter = it.kontanter,
            depositumskonto = it.depositumskonto,
        )
    },
)
