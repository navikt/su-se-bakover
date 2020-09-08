package no.nav.su.se.bakover.web.services.utbetaling.kvittering

import org.slf4j.LoggerFactory
import javax.jms.Connection
import javax.jms.Session

class KvitteringIbmMqConsumer(
    kvitteringQueueName: String,
    connection: Connection,
    private val kvitteringConsumer: KvitteringConsumer
) {
    private val log = LoggerFactory.getLogger(this::class.java)
    private val secureLog = LoggerFactory.getLogger("sikkerlogg")
    private val jmsSession =
        connection.createSession(false, Session.AUTO_ACKNOWLEDGE) // TODO vurder hvordan vi skal håndtere dette
    private val consumer = jmsSession.createConsumer(jmsSession.createQueue(kvitteringQueueName))

    init {
        consumer.setMessageListener { message ->
            try {
                log.info("Leser kvittering fra $kvitteringQueueName")
                message.getBody(String::class.java).let {
                    secureLog.info("Kvittering lest fra $kvitteringQueueName, innhold:$it")
                    kvitteringConsumer.onMessage(it)
                }
            } catch (ex: Exception) {
                log.error("Feil ved prossessering av melding fra: $kvitteringQueueName", ex)
                throw ex
            }
        }
    }
}
