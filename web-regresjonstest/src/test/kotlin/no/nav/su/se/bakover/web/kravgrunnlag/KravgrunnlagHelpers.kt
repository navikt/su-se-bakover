package no.nav.su.se.bakover.web.kravgrunnlag

import no.nav.su.se.bakover.common.CorrelationId
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.hendelse.domain.JMSHendelseMetadata
import no.nav.su.se.bakover.test.kravgrunnlag.kravgrunnlagStatusendringXml
import no.nav.su.se.bakover.web.komponenttest.AppComponents
import no.nav.su.se.bakover.web.services.tilbakekreving.lagreRåttKravgrunnlagDetaljerForUtbetalingerSomMangler
import tilbakekreving.domain.kravgrunnlag.rått.RåttKravgrunnlag
import tilbakekreving.presentation.Tilbakekrevingskomponenter

/**
 * @param overstyrUtbetalingId er ment for å trigge mismatch mellom kravgrunnlag og utbetaling. Dersom det er flere som mangler kravgrunnlag, vil alle få samme utbetalingId.
 */
internal fun AppComponents.emulerViMottarKravgrunnlagDetaljer(
    overstyrUtbetalingId: List<UUID30?>? = null,
): List<HendelseId> {
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
    ).map {
        return it
    }
    return emptyList()
}

internal fun AppComponents.emulerViMottarKravgrunnlagstatusendring(
    saksnummer: String,
    fnr: String,
    eksternVedtakId: String,
    status: String = "SPER",
) {
    return this.tilbakekrevingskomponenter.emulerViMottarKravgrunnlagstatusendring(
        saksnummer = saksnummer,
        fnr = fnr,
        eksternVedtakId = eksternVedtakId,
        status = status,
    )
}

internal fun Tilbakekrevingskomponenter.emulerViMottarKravgrunnlagstatusendring(
    saksnummer: String,
    fnr: String,
    eksternVedtakId: String,
    status: String = "SPER",
) {
    // Emulerer at det kommer en statusendring på køen som matcher revurderingen sin simulering.
    this.services.råttKravgrunnlagService.lagreRåttkravgrunnlagshendelse(
        råttKravgrunnlag = RåttKravgrunnlag(
            xmlMelding = kravgrunnlagStatusendringXml(
                saksnummer = saksnummer,
                fnr = fnr,
                vedtakId = eksternVedtakId,
                status = status,
            ),
        ),
        meta = JMSHendelseMetadata.fromCorrelationId(CorrelationId.generate()),
    )
    // Siden vi ikke kjører jobbene i test-miljøet må vi også kjøre denne konsumenten.
    this.services.knyttKravgrunnlagTilSakOgUtbetalingKonsument.knyttKravgrunnlagTilSakOgUtbetaling(
        correlationId = CorrelationId.generate(),
    )
}
