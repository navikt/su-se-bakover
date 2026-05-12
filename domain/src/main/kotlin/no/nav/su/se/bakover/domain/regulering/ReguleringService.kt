package no.nav.su.se.bakover.domain.regulering

import arrow.core.Either
import no.nav.su.se.bakover.common.domain.sak.SakInfo
import no.nav.su.se.bakover.common.persistence.SessionContext
import no.nav.su.se.bakover.common.persistence.TransactionContext
import no.nav.su.se.bakover.domain.revurdering.iverksett.KunneIkkeFerdigstilleIverksettelsestransaksjon
import no.nav.su.se.bakover.domain.vedtak.VedtakInnvilgetRegulering
import økonomi.domain.utbetaling.Utbetaling
import økonomi.domain.utbetaling.Utbetalinger
import java.time.Clock
import java.util.UUID

sealed interface KunneIkkeBehandleRegulering {
    data object KunneIkkeBeregne : KunneIkkeBehandleRegulering
    data object KunneIkkeSimulere : KunneIkkeBehandleRegulering
    data class KunneIkkeUtbetale(val feil: KunneIkkeFerdigstilleIverksettelsestransaksjon) : KunneIkkeBehandleRegulering
}

interface ReguleringService {
    fun behandleReguleringAutomatisk(
        regulering: ReguleringUnderBehandling,
        sakInfo: SakInfo,
        utbetalinger: Utbetalinger,
        isLiveRun: Boolean = true,
    ): Either<KunneIkkeBehandleRegulering, IverksattRegulering>

    fun beregnOgSimulerRegulering(
        regulering: ReguleringUnderBehandling,
        sakInfo: SakInfo,
        utbetalinger: Utbetalinger,
        clock: Clock,
    ): Either<KunneIkkeBehandleRegulering, Pair<ReguleringUnderBehandling.BeregnetRegulering, Utbetaling.SimulertUtbetaling>>

    fun ferdigstillRegulering(
        regulering: IverksattRegulering,
        simulertUtbetaling: Utbetaling.SimulertUtbetaling,
        sessionContext: TransactionContext? = null,
    ): Either<KunneIkkeBehandleRegulering.KunneIkkeUtbetale, VedtakInnvilgetRegulering>

    fun hentReguleringerForSak(sakId: UUID): Reguleringer

    fun hentRelatertId(sakId: UUID, tx: SessionContext): UUID?
}
