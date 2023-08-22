package no.nav.su.se.bakover.web

import no.nav.su.se.bakover.web.services.tilbakekreving.TilbakekrevingConsumer
import økonomi.infrastructure.kvittering.consumer.UtbetalingKvitteringConsumer

data class Consumers(
    val tilbakekrevingConsumer: TilbakekrevingConsumer,
    val utbetalingKvitteringConsumer: UtbetalingKvitteringConsumer,
)
