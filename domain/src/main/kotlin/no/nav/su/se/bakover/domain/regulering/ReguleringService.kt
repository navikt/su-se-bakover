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
        regulering: ReguleringUnderBehandling,
        sak: Sak,
        isLiveRun: Boolean = true,
    ): Either<KunneIkkeFerdigstilleOgIverksette, IverksattRegulering>

    fun beregnOgSimulerRegulering(
        regulering: ReguleringUnderBehandling,
        sak: Sak,
        clock: Clock,
    ): Either<KunneIkkeFerdigstilleOgIverksette, Pair<ReguleringUnderBehandling.BeregnetRegulering, Utbetaling.SimulertUtbetaling>>
}
