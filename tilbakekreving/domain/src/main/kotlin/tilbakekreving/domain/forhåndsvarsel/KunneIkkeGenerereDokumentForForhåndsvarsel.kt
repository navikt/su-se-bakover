package tilbakekreving.domain.forhåndsvarsel

import dokument.domain.KunneIkkeLageDokument

sealed interface KunneIkkeGenerereDokumentForForhåndsvarsel {
    data class FeilVedDokumentGenerering(val it: KunneIkkeLageDokument) : KunneIkkeGenerereDokumentForForhåndsvarsel
}
