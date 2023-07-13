package no.nav.su.se.bakover.domain.revurdering.brev

sealed class KunneIkkeLageBrevutkastForAvsluttingAvRevurdering {
    data object FantIkkeRevurdering : KunneIkkeLageBrevutkastForAvsluttingAvRevurdering()
    data object KunneIkkeLageBrevutkast : KunneIkkeLageBrevutkastForAvsluttingAvRevurdering()
    data object RevurderingenErIkkeForhåndsvarslet : KunneIkkeLageBrevutkastForAvsluttingAvRevurdering()
    data object FantIkkePerson : KunneIkkeLageBrevutkastForAvsluttingAvRevurdering()
    data object KunneIkkeHenteNavnForSaksbehandlerEllerAttestant : KunneIkkeLageBrevutkastForAvsluttingAvRevurdering()
    data object KunneIkkeGenererePDF : KunneIkkeLageBrevutkastForAvsluttingAvRevurdering()
    data object KunneIkkeFinneGjeldendeUtbetaling : KunneIkkeLageBrevutkastForAvsluttingAvRevurdering()
    data object DetSkalIkkeSendesBrev : KunneIkkeLageBrevutkastForAvsluttingAvRevurdering()
}
