package no.nav.su.se.bakover.web.kravgrunnlag

import no.nav.su.se.bakover.common.CorrelationId
import no.nav.su.se.bakover.hendelse.domain.JMSHendelseMetadata
import no.nav.su.se.bakover.web.komponenttest.AppComponents
import tilbakekreving.domain.kravgrunnlag.rått.RåttKravgrunnlag

/**
 * Emulerer at vi mottar et kravgrunnlag på IBM-køen til oppdrag.
 * @param correlationId brukes kun dersom ikke meta sendes inn.
 */
fun AppComponents.nyttKravgrunnlag(
    råttKravgrunnlag: RåttKravgrunnlag,
    correlationId: CorrelationId = CorrelationId.generate(),
    meta: JMSHendelseMetadata = JMSHendelseMetadata.fromCorrelationId(correlationId),
) {
    this.tilbakekrevingskomponenter.services.råttKravgrunnlagService.lagreRåttkravgrunnlagshendelse(
        råttKravgrunnlag = råttKravgrunnlag,
        meta = meta,
    )
}
