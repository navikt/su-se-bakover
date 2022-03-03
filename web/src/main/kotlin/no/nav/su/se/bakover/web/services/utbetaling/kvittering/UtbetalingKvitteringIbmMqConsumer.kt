package no.nav.su.se.bakover.web.services.utbetaling.kvittering

import no.nav.su.se.bakover.common.sikkerLogg
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import java.util.UUID
import javax.jms.JMSContext
import javax.jms.Session

class UtbetalingKvitteringIbmMqConsumer(
    kvitteringQueueName: String,
    globalJmsContext: JMSContext,
    private val kvitteringConsumer: UtbetalingKvitteringConsumer,
) {
    private val log = LoggerFactory.getLogger(this::class.java)
    private val jmsContext = globalJmsContext.createContext(Session.CLIENT_ACKNOWLEDGE)
    private val consumer = jmsContext.createConsumer(jmsContext.createQueue(kvitteringQueueName))

    // TODO: Vurder å gjøre om dette til en jobb som poller med receive, i stedet for asynkron messageListener
    // Da slipper vi sannsynligvis exceptionListeneren og vi har litt mer kontroll på når/hvordan tråden kjører
    init {
        consumer.setMessageListener { message ->
            try {
                // Ktor legger på X-Correlation-ID for web-requests, men vi har ikke noe tilsvarende automagi for meldingskøen.
                MDC.put("X-Correlation-ID", UUID.randomUUID().toString())
                log.info("Mottok kvittering fra køen: $kvitteringQueueName. Se sikkerlogg for meldingsinnhold.")
                message.getBody(String::class.java).let {
                    sikkerLogg.info("Kvittering lest fra $kvitteringQueueName, innhold:$it")
                    kvitteringConsumer.onMessage(it)
                }
                message.acknowledge()
            } catch (ex: Exception) {
                log.error("Feil ved prossessering av melding fra: $kvitteringQueueName", ex)
            }
        }

        jmsContext.setExceptionListener { exception -> log.error("Feil mot $kvitteringQueueName", exception) }

        jmsContext.start()
    }
}
