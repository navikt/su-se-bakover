package no.nav.su.se.bakover.domain.revurdering.brev

import dokument.domain.KunneIkkeLageDokument

sealed interface KunneIkkeLageBrevutkastForRevurdering {
    data object FantIkkeRevurdering : KunneIkkeLageBrevutkastForRevurdering
    data object UgyldigTilstand : KunneIkkeLageBrevutkastForRevurdering
    data class KunneIkkeGenererePdf(val underliggende: KunneIkkeLageDokument) : KunneIkkeLageBrevutkastForRevurdering
}
