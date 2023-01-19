package no.nav.su.se.bakover.domain.revurdering.varsel

sealed interface Varselmelding {
    object BeløpsendringUnder10Prosent : Varselmelding
    object FradragOgFormueForEPSErFjernet : Varselmelding
}
