package no.nav.su.se.bakover.domain.revurdering.varsel

sealed interface Varselmelding {
    data object BeløpsendringUnder10Prosent : Varselmelding
    data object FradragOgFormueForEPSErFjernet : Varselmelding
}
