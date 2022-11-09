package no.nav.su.se.bakover.domain.revurdering.gjenopptak

sealed class KunneIkkeIverksetteGjenopptakAvYtelse {
    object SimuleringIndikererFeilutbetaling : KunneIkkeIverksetteGjenopptakAvYtelse()
}
