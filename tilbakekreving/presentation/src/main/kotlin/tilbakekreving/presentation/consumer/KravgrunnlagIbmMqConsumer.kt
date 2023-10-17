package tilbakekreving.presentation.consumer

import no.nav.su.se.bakover.common.infrastructure.correlation.withCorrelationId
import no.nav.su.se.bakover.common.sikkerLogg
import no.nav.su.se.bakover.hendelse.infrastructure.persistence.toJMSHendelseMetadata
import org.slf4j.LoggerFactory
import tilbakekreving.application.service.RåttKravgrunnlagService
import tilbakekreving.domain.kravgrunnlag.RåttKravgrunnlag
import java.lang.RuntimeException
import javax.jms.JMSContext
import javax.jms.Session

/**
 * Har ansvar for:
 * 1. Motta kravgrunnlag fra meldingskøen.
 * 2. Kalle Service for å lagre kravgrunnlaget.
 *
 * Denne skal erstatte TilbakekrevingIbmMqConsumer
 */
class KravgrunnlagIbmMqConsumer(
    queueName: String,
    globalJmsContext: JMSContext,
    private val service: RåttKravgrunnlagService,
) {
    private val log = LoggerFactory.getLogger(this::class.java)
    private val jmsContext = globalJmsContext.createContext(Session.AUTO_ACKNOWLEDGE)
    private val consumer = jmsContext.createConsumer(jmsContext.createQueue(queueName))

    init {
        consumer.setMessageListener { message ->
            try {
                withCorrelationId { correlationId ->
                    log.info("Mottok kravgrunnlag fra køen: $queueName. Se sikkerlogg for meldingsinnhold.")
                    message.getBody(String::class.java).let {
                        sikkerLogg.info("Kravgrunnlag lest fra $queueName, innhold: $it")
                        service.lagreRåttkravgrunnlagshendelse(
                            råttKravgrunnlag = RåttKravgrunnlag(it),
                            meta = message.toJMSHendelseMetadata(correlationId),
                        )
                        log.info("Lagret kravgrunnlag fra køen: $queueName. Acker meldingen.")
                    }
                }
            } catch (ex: Exception) {
                log.error("Feil ved prosessering av melding fra: $queueName", ex)
                throw RuntimeException("Feil ved prosessering av melding fra: $queueName. Vi må kaste for å nacke meldingen. Se tilhørende errorlogg.")
            }
        }
        jmsContext.setExceptionListener { exception -> log.error("Feil mot $queueName", exception) }

        jmsContext.start()
    }
}
