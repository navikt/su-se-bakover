package tilbakekreving.domain.vedtaksbrev

import tilgangstyring.domain.IkkeTilgangTilSak

sealed interface KunneIkkeForhåndsviseVedtaksbrev {
    data class IkkeTilgang(val underliggende: IkkeTilgangTilSak) : KunneIkkeForhåndsviseVedtaksbrev
    data object FantIkkeBehandling : KunneIkkeForhåndsviseVedtaksbrev
    data object FeilVedGenereringAvDokument : KunneIkkeForhåndsviseVedtaksbrev
    data object VurderingerFinnesIkkePåBehandlingen : KunneIkkeForhåndsviseVedtaksbrev
}
