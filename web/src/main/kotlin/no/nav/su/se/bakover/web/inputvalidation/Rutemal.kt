package no.nav.su.se.bakover.web.inputvalidation

import io.ktor.server.routing.RoutingCall

private val ruteSelektor = Regex("/\\([^)]*\\)")

/**
 * Rutemalen for kallet uten Ktors interne selektorer, f.eks.
 * `/saker/{sakId}/klager/{klageId}/avslutt` i stedet for
 * `/(authenticate jwt)/(with user)/saker/{sakId}/klager/{klageId}/avslutt/(method:POST)`.
 * Inneholder ingen faktiske verdier (UUID-er, fnr), så den er trygg å logge.
 *
 * Bygger på formatet til `RoutingNode.toString()` i Ktor, som ikke er en garantert kontrakt.
 */
internal val RoutingCall.rutemal: String
    get() = route.toString().replace(ruteSelektor, "")
