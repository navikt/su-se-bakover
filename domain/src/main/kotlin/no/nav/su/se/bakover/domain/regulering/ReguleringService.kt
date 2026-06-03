package no.nav.su.se.bakover.domain.regulering

import arrow.core.Either
import behandling.regulering.domain.simulering.KunneIkkeSimulereRegulering
import no.nav.su.se.bakover.common.domain.sak.SakInfo
import no.nav.su.se.bakover.common.persistence.SessionContext
import no.nav.su.se.bakover.common.persistence.TransactionContext
import no.nav.su.se.bakover.domain.revurdering.iverksett.KunneIkkeFerdigstilleIverksettelsestransaksjon
import no.nav.su.se.bakover.domain.vedtak.VedtakInnvilgetRegulering
import satser.domain.SatsFactory
import økonomi.domain.utbetaling.Utbetaling
import økonomi.domain.utbetaling.Utbetalinger
import java.time.Clock
import java.util.UUID

sealed interface KunneIkkeBehandleRegulering {
    data object KunneIkkeBeregne : KunneIkkeBehandleRegulering

    /**
     * Bærer med seg [underliggende] slik at årsaken til at simulering feilet
     * (f.eks. utenfor åpningstid, manglende uføregrunnlag eller forskjeller mellom
     * forventet og simulert utbetaling) blir synlig i loggen og i
     * reguleringsresultatets `beskrivelse`-felt.
     */
    data class KunneIkkeSimulere(val underliggende: KunneIkkeSimulereRegulering) : KunneIkkeBehandleRegulering
    data class KunneIkkeUtbetale(val feil: KunneIkkeFerdigstilleIverksettelsestransaksjon) : KunneIkkeBehandleRegulering
}

interface ReguleringService {
    fun behandleReguleringAutomatisk(
        regulering: ReguleringUnderBehandling,
        sakInfo: SakInfo,
        utbetalinger: Utbetalinger,
        satsFactory: SatsFactory,
        isLiveRun: Boolean = true,
    ): Either<KunneIkkeBehandleRegulering, IverksattRegulering>

    fun beregnOgSimulerRegulering(
        regulering: ReguleringUnderBehandling,
        sakInfo: SakInfo,
        utbetalinger: Utbetalinger,
        satsFactory: SatsFactory,
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
