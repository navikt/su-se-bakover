package no.nav.su.se.bakover.domain.revurdering.brev

sealed class KunneIkkeLageBrevutkastForRevurdering {
    object FantIkkeRevurdering : KunneIkkeLageBrevutkastForRevurdering()
    object KunneIkkeLageBrevutkast : KunneIkkeLageBrevutkastForRevurdering()
    object FantIkkePerson : KunneIkkeLageBrevutkastForRevurdering()
    object KunneIkkeHenteNavnForSaksbehandlerEllerAttestant : KunneIkkeLageBrevutkastForRevurdering()
    object KunneIkkeFinneGjeldendeUtbetaling : KunneIkkeLageBrevutkastForRevurdering()
    object DetSkalIkkeSendesBrev : KunneIkkeLageBrevutkastForRevurdering()
    object UgyldigTilstand : KunneIkkeLageBrevutkastForRevurdering()
}
