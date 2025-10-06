package no.nav.su.se.bakover.domain.revurdering.stans

import no.nav.su.se.bakover.domain.revurdering.StansAvYtelseRevurdering
import no.nav.su.se.bakover.domain.vedtak.VedtakStansAvYtelse
import økonomi.domain.utbetaling.Utbetalingsrequest

data class IverksettStansAvYtelseITransaksjonResponse(
    val revurdering: StansAvYtelseRevurdering.IverksattStansAvYtelse,
    val vedtak: VedtakStansAvYtelse,
    val utbetalingsrequest: Utbetalingsrequest,
)
