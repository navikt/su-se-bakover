package tilbakekreving.domain.iverksettelse

import tilgangstyring.domain.IkkeTilgangTilSak

sealed interface KunneIkkeIverksette {
    data class IkkeTilgang(val underliggende: IkkeTilgangTilSak) : KunneIkkeIverksette

    /**
     * Dersom det har kommet nye endringer på kravgrunnlaget siden behandlingen ble vurdert.
     * Saksbehandler må da oppdatere kravgrunnlaget.
     */
    data object KravgrunnlagetHarEndretSeg : KunneIkkeIverksette
    data object UlikVersjon : KunneIkkeIverksette
    data object SaksbehandlerOgAttestantKanIkkeVæreSammePerson : KunneIkkeIverksette
    data object KunneIkkeSendeTilbakekrevingsvedtak : KunneIkkeIverksette
}
