package no.nav.su.se.bakover.web.services.tilbakekreving

import no.nav.su.se.bakover.common.infrastructure.correlation.withCorrelationId
import no.nav.su.se.bakover.common.sikkerLogg
import org.slf4j.LoggerFactory
import javax.jms.JMSContext
import javax.jms.Session

/**
 * Har ansvar for:
 * 1. Integrasjonen mot meldingskøen.
 * 2. Konsumering av meldinger
 * 3. Deserialisere til String.
 * 4. Lagre
 *
 * For selve prosessering av kravgrunnlagene, se [SendTilbakekrevingsvedtakForRevurdering]
 */
internal class TilbakekrevingIbmMqConsumer(
    queueName: String,
    globalJmsContext: JMSContext,
    private val tilbakekrevingConsumer: TilbakekrevingConsumer,
) {
    private val log = LoggerFactory.getLogger(this::class.java)
    private val jmsContext = globalJmsContext.createContext(Session.AUTO_ACKNOWLEDGE)
    private val consumer = jmsContext.createConsumer(jmsContext.createQueue(queueName))

    init {
        consumer.setMessageListener { message ->
            try {
                withCorrelationId {
                    log.info("Mottok kravgrunnlag fra køen: $queueName. Se sikkerlogg for meldingsinnhold.")
                    message.getBody(String::class.java).let {
                        sikkerLogg.info("Kravgrunnlag lest fra $queueName, innhold: $it")
                        tilbakekrevingConsumer.onMessage(it)
                    }
                }
            } catch (ex: Exception) {
                log.error("Feil ved prosessering av melding fra: $queueName", ex)
                throw ex
            }
        }
        jmsContext.setExceptionListener { exception -> log.error("Feil mot $queueName", exception) }

        jmsContext.start()
    }
}
