package tilbakekreving.domain.tilAttestering

import tilbakekreving.domain.IkkeTilgangTilSak

sealed interface KunneIkkeSendeTilAttestering {
    data class IkkeTilgang(val underliggende: IkkeTilgangTilSak) : KunneIkkeSendeTilAttestering

    /**
     * Dersom det har kommet nye endringer på kravgrunnlaget siden behandlingen ble vurdert.
     * Saksbehandler må da oppdatere kravgrunnlaget.
     */
    data object KravgrunnlagetHarEndretSeg : KunneIkkeSendeTilAttestering
}
