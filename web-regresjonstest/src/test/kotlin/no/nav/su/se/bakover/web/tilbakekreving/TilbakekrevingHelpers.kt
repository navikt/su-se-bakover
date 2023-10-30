package no.nav.su.se.bakover.web.tilbakekreving

import no.nav.su.se.bakover.common.CorrelationId
import no.nav.su.se.bakover.web.komponenttest.AppComponents

internal fun AppComponents.oppdaterOppgave() {
    this.tilbakekrevingskomponenter.services.oppdaterOppgaveForTilbakekrevingshendelserKonsument.oppdaterOppgaver(
        correlationId = CorrelationId.generate(),
    )
}

internal fun AppComponents.lukkOppgave() {
    this.tilbakekrevingskomponenter.services.lukkOppgaveForTilbakekrevingshendelserKonsument.lukkOppgaver(
        correlationId = CorrelationId.generate(),
    )
}
