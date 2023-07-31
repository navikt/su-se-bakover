package no.nav.su.se.bakover.domain.revurdering.brev

import no.nav.su.se.bakover.domain.dokument.KunneIkkeLageDokument

sealed interface KunneIkkeLageBrevutkastForRevurdering {
    data object FantIkkeRevurdering : KunneIkkeLageBrevutkastForRevurdering
    data object UgyldigTilstand : KunneIkkeLageBrevutkastForRevurdering
    data class KunneIkkeGenererePdf(val underliggende: KunneIkkeLageDokument) : KunneIkkeLageBrevutkastForRevurdering
}
