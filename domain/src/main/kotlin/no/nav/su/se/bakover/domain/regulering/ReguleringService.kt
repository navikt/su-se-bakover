package no.nav.su.se.bakover.domain.regulering

import arrow.core.Either
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.revurdering.iverksett.KunneIkkeFerdigstilleIverksettelsestransaksjon
import satser.domain.SatsFactory

sealed interface KunneIkkeFerdigstilleOgIverksette {
    data object KunneIkkeBeregne : KunneIkkeFerdigstilleOgIverksette
    data object KunneIkkeSimulere : KunneIkkeFerdigstilleOgIverksette
    data class KunneIkkeUtbetale(val feil: KunneIkkeFerdigstilleIverksettelsestransaksjon) : KunneIkkeFerdigstilleOgIverksette
}

interface ReguleringService {

    /**
     * Lagrer reguleringen
     */
    // TODO del opp i beregning, simuler og ferdigstill??
    fun ferdigstillOgIverksettRegulering(
        regulering: OpprettetRegulering,
        sak: Sak,
        isLiveRun: Boolean,
        satsFactory: SatsFactory,
    ): Either<KunneIkkeFerdigstilleOgIverksette, IverksattRegulering>
}
