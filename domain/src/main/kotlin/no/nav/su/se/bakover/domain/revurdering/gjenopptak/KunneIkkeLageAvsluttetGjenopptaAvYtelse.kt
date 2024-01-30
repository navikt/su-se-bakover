package no.nav.su.se.bakover.domain.revurdering.gjenopptak

sealed interface KunneIkkeLageAvsluttetGjenopptaAvYtelse {
    data object RevurderingErAlleredeAvsluttet : KunneIkkeLageAvsluttetGjenopptaAvYtelse
    data object RevurderingenErIverksatt : KunneIkkeLageAvsluttetGjenopptaAvYtelse
}
