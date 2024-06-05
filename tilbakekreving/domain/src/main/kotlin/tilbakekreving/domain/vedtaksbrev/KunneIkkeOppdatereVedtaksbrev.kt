package tilbakekreving.domain.vedtaksbrev

import tilgangstyring.domain.IkkeTilgangTilSak

sealed interface KunneIkkeOppdatereVedtaksbrev {
    data class IkkeTilgang(val underliggende: IkkeTilgangTilSak) : KunneIkkeOppdatereVedtaksbrev
    data object UlikVersjon : KunneIkkeOppdatereVedtaksbrev
}
