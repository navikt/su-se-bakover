package no.nav.su.se.bakover.domain.revurdering.retur

sealed interface KunneIkkeReturnereRevurdering {
    data object FantIkkeRevurdering : KunneIkkeReturnereRevurdering
    data object FeilSaksbehandler : KunneIkkeReturnereRevurdering
}
