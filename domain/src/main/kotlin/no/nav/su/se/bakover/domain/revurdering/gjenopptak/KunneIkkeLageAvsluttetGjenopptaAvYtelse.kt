package no.nav.su.se.bakover.domain.revurdering.gjenopptak

sealed class KunneIkkeLageAvsluttetGjenopptaAvYtelse {
    object RevurderingErAlleredeAvsluttet : KunneIkkeLageAvsluttetGjenopptaAvYtelse()
    object RevurderingenErIverksatt : KunneIkkeLageAvsluttetGjenopptaAvYtelse()
}
