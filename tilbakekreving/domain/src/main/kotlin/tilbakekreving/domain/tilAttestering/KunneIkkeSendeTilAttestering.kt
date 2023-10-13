package tilbakekreving.domain.tilAttestering

import tilbakekreving.domain.IkkeTilgangTilSak

sealed interface KunneIkkeSendeTilAttestering {
    data class IkkeTilgang(val underliggende: IkkeTilgangTilSak) : KunneIkkeSendeTilAttestering
}
