package no.nav.su.se.bakover.domain.regulering

import arrow.core.Either
import no.nav.su.se.bakover.common.persistence.TransactionContext
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.revurdering.iverksett.KunneIkkeFerdigstilleIverksettelsestransaksjon
import no.nav.su.se.bakover.domain.vedtak.VedtakInnvilgetRegulering
import økonomi.domain.utbetaling.Utbetaling
import java.time.Clock

sealed interface KunneIkkeBehandleRegulering {
    data object KunneIkkeBeregne : KunneIkkeBehandleRegulering
    data object KunneIkkeSimulere : KunneIkkeBehandleRegulering
    data class KunneIkkeUtbetale(val feil: KunneIkkeFerdigstilleIverksettelsestransaksjon) : KunneIkkeBehandleRegulering
}

interface ReguleringService {
    fun behandleReguleringAutomatisk(
        regulering: ReguleringUnderBehandling,
        sak: Sak,
        isLiveRun: Boolean = true,
    ): Either<KunneIkkeBehandleRegulering, IverksattRegulering>

    fun beregnOgSimulerRegulering(
        regulering: ReguleringUnderBehandling,
        sak: Sak,
        clock: Clock,
    ): Either<KunneIkkeBehandleRegulering, Pair<ReguleringUnderBehandling.BeregnetRegulering, Utbetaling.SimulertUtbetaling>>

    fun ferdigstillRegulering(
        regulering: IverksattRegulering,
        simulertUtbetaling: Utbetaling.SimulertUtbetaling,
        sessionContext: TransactionContext? = null,
    ): Either<KunneIkkeBehandleRegulering.KunneIkkeUtbetale, VedtakInnvilgetRegulering>
}
