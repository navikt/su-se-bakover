package tilbakekreving.domain.forhåndsvarsel

import dokument.domain.KunneIkkeLageDokument
import tilgangstyring.domain.IkkeTilgangTilSak

sealed interface KunneIkkeForhåndsvarsle {
    data class IkkeTilgang(val underliggende: IkkeTilgangTilSak) : KunneIkkeForhåndsvarsle
    data class FeilVedDokumentGenerering(val kunneIkkeLageDokument: KunneIkkeLageDokument) : KunneIkkeForhåndsvarsle
    data object UlikVersjon : KunneIkkeForhåndsvarsle
}
