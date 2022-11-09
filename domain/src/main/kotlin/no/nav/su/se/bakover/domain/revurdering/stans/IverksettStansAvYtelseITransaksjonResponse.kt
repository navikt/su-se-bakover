package no.nav.su.se.bakover.domain.revurdering.stans

import arrow.core.Either
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingFeilet
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingsrequest
import no.nav.su.se.bakover.domain.revurdering.StansAvYtelseRevurdering
import no.nav.su.se.bakover.domain.vedtak.VedtakSomKanRevurderes

data class IverksettStansAvYtelseITransaksjonResponse(
    val revurdering: StansAvYtelseRevurdering.IverksattStansAvYtelse,
    val vedtak: VedtakSomKanRevurderes.EndringIYtelse.StansAvYtelse,
    val sendUtbetalingCallback: () -> Either<UtbetalingFeilet.Protokollfeil, Utbetalingsrequest>,
    val sendStatistikkCallback: () -> Unit,
)
