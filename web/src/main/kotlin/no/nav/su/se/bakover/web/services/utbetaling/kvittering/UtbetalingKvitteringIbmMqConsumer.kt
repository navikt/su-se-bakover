package no.nav.su.se.bakover.web.services.utbetaling.kvittering

import no.nav.su.se.bakover.web.sikkerlogg
import org.slf4j.LoggerFactory
import javax.jms.JMSContext
import javax.jms.Session

class UtbetalingKvitteringIbmMqConsumer(
    kvitteringQueueName: String,
    globalJmsContext: JMSContext,
    private val kvitteringConsumer: UtbetalingKvitteringConsumer
) {
    private val log = LoggerFactory.getLogger(this::class.java)
    private val jmsContext = globalJmsContext.createContext(Session.AUTO_ACKNOWLEDGE)
    private val consumer = jmsContext.createConsumer(jmsContext.createQueue(kvitteringQueueName))

    init {
        consumer.setMessageListener { message ->
            try {
                log.info("Leser kvittering fra $kvitteringQueueName")
                message.getBody(String::class.java).let {
                    sikkerlogg.info("Kvittering lest fra $kvitteringQueueName, innhold:$it")
                    kvitteringConsumer.onMessage(it)
                }
            } catch (ex: Exception) {
                log.error("Feil ved prossessering av melding fra: $kvitteringQueueName", ex)
                throw ex
            }
        }
        jmsContext.start()
    }
}
