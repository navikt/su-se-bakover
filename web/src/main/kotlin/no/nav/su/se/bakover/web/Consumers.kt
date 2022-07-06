package no.nav.su.se.bakover.web

import no.nav.su.se.bakover.web.services.tilbakekreving.TilbakekrevingConsumer
import no.nav.su.se.bakover.web.services.utbetaling.kvittering.UtbetalingKvitteringConsumer

data class Consumers(
    val tilbakekrevingConsumer: TilbakekrevingConsumer,
    val utbetalingKvitteringConsumer: UtbetalingKvitteringConsumer,
)
