package no.nav.su.se.bakover.domain.revurdering.brev

sealed class KunneIkkeLageBrevutkastForAvsluttingAvRevurdering {
    object FantIkkeRevurdering : KunneIkkeLageBrevutkastForAvsluttingAvRevurdering()
    object KunneIkkeLageBrevutkast : KunneIkkeLageBrevutkastForAvsluttingAvRevurdering()
    object RevurderingenErIkkeForhåndsvarslet : KunneIkkeLageBrevutkastForAvsluttingAvRevurdering()
    object FantIkkePerson : KunneIkkeLageBrevutkastForAvsluttingAvRevurdering()
    object KunneIkkeHenteNavnForSaksbehandlerEllerAttestant : KunneIkkeLageBrevutkastForAvsluttingAvRevurdering()
    object KunneIkkeGenererePDF : KunneIkkeLageBrevutkastForAvsluttingAvRevurdering()
    object KunneIkkeFinneGjeldendeUtbetaling : KunneIkkeLageBrevutkastForAvsluttingAvRevurdering()
    object DetSkalIkkeSendesBrev : KunneIkkeLageBrevutkastForAvsluttingAvRevurdering()
}
