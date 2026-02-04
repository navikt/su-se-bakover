package no.nav.su.se.bakover.domain.regulering

import arrow.core.Either
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.revurdering.iverksett.KunneIkkeFerdigstilleIverksettelsestransaksjon

sealed interface KunneIkkeFerdigstilleOgIverksette {
    data object KunneIkkeBeregne : KunneIkkeFerdigstilleOgIverksette
    data object KunneIkkeSimulere : KunneIkkeFerdigstilleOgIverksette
    data class KunneIkkeUtbetale(val feil: KunneIkkeFerdigstilleIverksettelsestransaksjon) : KunneIkkeFerdigstilleOgIverksette
}

interface ReguleringService {
    fun behandleRegulering(
        regulering: OpprettetRegulering,
        sak: Sak,
        isLiveRun: Boolean = true,
    ): Either<KunneIkkeFerdigstilleOgIverksette, IverksattRegulering>
}
