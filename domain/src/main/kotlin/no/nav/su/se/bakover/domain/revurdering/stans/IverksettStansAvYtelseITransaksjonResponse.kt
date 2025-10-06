package no.nav.su.se.bakover.domain.revurdering.stans

import arrow.core.Either
import no.nav.su.se.bakover.common.persistence.SessionContext
import no.nav.su.se.bakover.domain.revurdering.StansAvYtelseRevurdering
import no.nav.su.se.bakover.domain.vedtak.VedtakStansAvYtelse
import økonomi.domain.utbetaling.UtbetalingFeilet
import økonomi.domain.utbetaling.Utbetalingsrequest

data class IverksettStansAvYtelseITransaksjonResponse(
    val revurdering: StansAvYtelseRevurdering.IverksattStansAvYtelse,
    val vedtak: VedtakStansAvYtelse,
    val sendUtbetalingCallback: () -> Either<UtbetalingFeilet.Protokollfeil, Utbetalingsrequest>,
    val sendStatistikkCallback: (tx: SessionContext) -> Unit,
)
