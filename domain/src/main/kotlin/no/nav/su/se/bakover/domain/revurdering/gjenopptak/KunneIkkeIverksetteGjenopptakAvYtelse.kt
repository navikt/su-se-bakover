package no.nav.su.se.bakover.domain.revurdering.gjenopptak

sealed interface KunneIkkeIverksetteGjenopptakAvYtelse {
    data object SimuleringIndikererFeilutbetaling : KunneIkkeIverksetteGjenopptakAvYtelse
}
