package tilbakekreving.domain.vurdert

import tilbakekreving.domain.IkkeTilgangTilSak

sealed interface KunneIkkeLagreBrevtekst {
    data class IkkeTilgang(val underliggende: IkkeTilgangTilSak) : KunneIkkeLagreBrevtekst
    data object UlikVersjon : KunneIkkeLagreBrevtekst
}
