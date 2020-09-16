package no.nav.su.se.bakover.web.services.utbetaling.kvittering

import no.nav.su.se.bakover.web.sikkerlogg
import org.slf4j.LoggerFactory
import javax.jms.JMSContext
import javax.jms.Session

class AvstemmingKvitteringIbmMqConsumer(
    kvitteringQueueName: String,
    globalJmxContext: JMSContext
) {
    private val log = LoggerFactory.getLogger(this::class.java)
    private val jmxContext = globalJmxContext.createContext(Session.AUTO_ACKNOWLEDGE)
    private val consumer = jmxContext.createConsumer(jmxContext.createQueue(kvitteringQueueName))

    init {
        consumer.setMessageListener { message ->
            try {
                log.info("Leser kvittering fra $kvitteringQueueName")
                message.getBody(String::class.java).let {
                    sikkerlogg.info("Kvittering lest fra $kvitteringQueueName, innhold:$it")
                    log.info("Kvittering lest fra $kvitteringQueueName, innhold:$it") // TODO: Slett når vi har debugget ferdig
                }
            } catch (ex: Exception) {
                log.error("Feil ved prossessering av melding fra: $kvitteringQueueName", ex)
                throw ex
            }
        }
        jmxContext.start()
    }
}
