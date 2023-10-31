package tilbakekreving.domain.vurdert

import tilbakekreving.domain.IkkeTilgangTilSak

sealed interface KunneIkkeForhåndsviseVedtaksbrev {
    data class IkkeTilgang(val underliggende: IkkeTilgangTilSak) : KunneIkkeForhåndsviseVedtaksbrev
    data object SkalIkkeSendeBrevForÅViseVedtaksbrev : KunneIkkeForhåndsviseVedtaksbrev
    data object BrevetMåVæreVedtaksbrevMedFritekst : KunneIkkeForhåndsviseVedtaksbrev
    data object FantIkkeBehandling : KunneIkkeForhåndsviseVedtaksbrev
    data object IkkeTattStillingTilBrevvalg : KunneIkkeForhåndsviseVedtaksbrev
    data object FeilVedGenereringAvDokument : KunneIkkeForhåndsviseVedtaksbrev
}
