package no.nav.su.se.bakover.web.kravgrunnlag

import no.nav.su.se.bakover.common.CorrelationId
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.hendelse.domain.JMSHendelseMetadata
import no.nav.su.se.bakover.test.kravgrunnlag.kravgrunnlagStatusendringXml
import no.nav.su.se.bakover.web.komponenttest.AppComponents
import no.nav.su.se.bakover.web.services.tilbakekreving.lagreRåttKravgrunnlagDetaljerForUtbetalingerSomMangler
import tilbakekreving.domain.kravgrunnlag.RåttKravgrunnlag

/**
 * @param overstyrUtbetalingId er ment for å trigge mismatch mellom kravgrunnlag og utbetaling. Dersom det er flere som mangler kravgrunnlag, vil alle få samme utbetalingId.
 */
internal fun AppComponents.emulerViMottarKravgrunnlagDetaljer(
    overstyrUtbetalingId: List<UUID30?>? = null,
) {
    // Emulerer at det kommer detaljer på køen som matcher revurderingen sin simulering.
    lagreRåttKravgrunnlagDetaljerForUtbetalingerSomMangler(
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

internal fun AppComponents.emulerViMottarKravgrunnlagstatusendring(
    saksnummer: String,
    fnr: String,
    eksternVedtakId: String,
) {
    // Emulerer at det kommer en statusendring på køen som matcher revurderingen sin simulering.
    this.tilbakekrevingskomponenter.services.råttKravgrunnlagService.lagreRåttkravgrunnlagshendelse(
        råttKravgrunnlag = RåttKravgrunnlag(
            xmlMelding = kravgrunnlagStatusendringXml(
                saksnummer = saksnummer,
                fnr = fnr,
                vedtakId = eksternVedtakId,
            ),
        ),
        meta = JMSHendelseMetadata.fromCorrelationId(CorrelationId.generate()),
    )
    // Siden vi ikke kjører jobbene i test-miljøet må vi også kjøre denne konsumenten.
    this.tilbakekrevingskomponenter.services.knyttKravgrunnlagTilSakOgUtbetalingKonsument.knyttKravgrunnlagTilSakOgUtbetaling(
        correlationId = CorrelationId.generate(),
    )
}
