package no.nav.su.se.bakover.domain.søknadsbehandling.grunnlag

sealed interface KunneIkkeLeggeTilSkattegrunnlag {
    data object KanIkkeLeggeTilSkattForTilstandUtenAtDenHarBlittHentetFør : KunneIkkeLeggeTilSkattegrunnlag

    data object UgyldigTilstand : KunneIkkeLeggeTilSkattegrunnlag
}
