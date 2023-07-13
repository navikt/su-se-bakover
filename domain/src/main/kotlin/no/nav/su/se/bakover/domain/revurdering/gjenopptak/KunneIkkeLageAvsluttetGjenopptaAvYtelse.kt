package no.nav.su.se.bakover.domain.revurdering.gjenopptak

sealed class KunneIkkeLageAvsluttetGjenopptaAvYtelse {
    data object RevurderingErAlleredeAvsluttet : KunneIkkeLageAvsluttetGjenopptaAvYtelse()
    data object RevurderingenErIverksatt : KunneIkkeLageAvsluttetGjenopptaAvYtelse()
}
