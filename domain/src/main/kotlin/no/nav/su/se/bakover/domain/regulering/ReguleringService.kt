package no.nav.su.se.bakover.domain.regulering

import arrow.core.Either
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.revurdering.iverksett.KunneIkkeFerdigstilleIverksettelsestransaksjon
import økonomi.domain.utbetaling.Utbetaling
import java.time.Clock

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

    fun beregnRegulering(
        regulering: OpprettetRegulering,
        clock: Clock,
    ): Either<KunneIkkeFerdigstilleOgIverksette.KunneIkkeBeregne, OpprettetRegulering>

    fun simulerReguleringOgUtbetaling(
        regulering: OpprettetRegulering,
        sak: Sak,
    ): Either<KunneIkkeFerdigstilleOgIverksette.KunneIkkeSimulere, Pair<OpprettetRegulering, Utbetaling.SimulertUtbetaling>>
}
