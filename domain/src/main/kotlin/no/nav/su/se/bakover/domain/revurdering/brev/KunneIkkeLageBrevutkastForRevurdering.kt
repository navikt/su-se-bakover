package no.nav.su.se.bakover.domain.revurdering.brev

sealed class KunneIkkeLageBrevutkastForRevurdering {
    data object FantIkkeRevurdering : KunneIkkeLageBrevutkastForRevurdering()
    data object KunneIkkeLageBrevutkast : KunneIkkeLageBrevutkastForRevurdering()
    data object FantIkkePerson : KunneIkkeLageBrevutkastForRevurdering()
    data object KunneIkkeHenteNavnForSaksbehandlerEllerAttestant : KunneIkkeLageBrevutkastForRevurdering()
    data object KunneIkkeFinneGjeldendeUtbetaling : KunneIkkeLageBrevutkastForRevurdering()
    data object DetSkalIkkeSendesBrev : KunneIkkeLageBrevutkastForRevurdering()
    data object UgyldigTilstand : KunneIkkeLageBrevutkastForRevurdering()
}
