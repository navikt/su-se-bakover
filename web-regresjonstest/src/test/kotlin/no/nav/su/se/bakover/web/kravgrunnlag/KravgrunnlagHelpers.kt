package no.nav.su.se.bakover.web.kravgrunnlag

import no.nav.su.se.bakover.common.CorrelationId
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.web.komponenttest.AppComponents
import no.nav.su.se.bakover.web.services.tilbakekreving.lagreRåttKravgrunnlagForUtbetalingerSomMangler

/**
 * @param overstyrUtbetalingId er ment for å trigge mismatch mellom kravgrunnlag og utbetaling. Dersom det er flere som mangler kravgrunnlag, vil alle få samme utbetalingId.
 */
internal fun AppComponents.emulerViMottarKravgrunnlag(
    overstyrUtbetalingId: List<UUID30?>? = null,
) {
    // Emulerer at det kommer et kravgrunnlag på køen som matcher revurderingen sin simulering.
    lagreRåttKravgrunnlagForUtbetalingerSomMangler(
        sessionFactory = this.databaseRepos.sessionFactory,
        service = this.tilbakekrevingskomponenter.services.råttKravgrunnlagService,
        clock = this.clock,
        overstyrUtbetalingId = overstyrUtbetalingId,
    )
    // Siden vi ikke kjører jobbene i test-miljøet må vi også kjøre denne konsumenten.
    this.tilbakekrevingskomponenter.services.knyttKravgrunnlagTilSakOgUtbetalingKonsument.knyttKravgrunnlagTilSakOgUtbetaling(
        correlationId = CorrelationId.generate(),
    )
}
