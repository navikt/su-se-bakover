package økonomi.infrastructure.kvittering.consumer

import no.nav.su.se.bakover.common.infrastructure.correlation.withCorrelationId
import no.nav.su.se.bakover.common.sikkerLogg
import org.slf4j.LoggerFactory
import javax.jms.JMSContext
import javax.jms.Session

/**
 * TODO jah: Denne slettes når vi tar i bruk UtbetalingKvitteringIbmMqConsumerV2
 */
class UtbetalingKvitteringIbmMqConsumer(
    kvitteringQueueName: String,
    globalJmsContext: JMSContext,
    private val kvitteringConsumer: UtbetalingKvitteringConsumer,
) {
    private val log = LoggerFactory.getLogger(this::class.java)
    private val jmsContext = globalJmsContext.createContext(Session.AUTO_ACKNOWLEDGE)
    private val consumer = jmsContext.createConsumer(jmsContext.createQueue(kvitteringQueueName))

    // TODO: Vurder å gjøre om dette til en jobb som poller med receive, i stedet for asynkron messageListener
    //  Da slipper vi sannsynligvis exceptionListeneren og vi har litt mer kontroll på når/hvordan tråden kjører
    init {
        consumer.setMessageListener { message ->
            try {
                withCorrelationId {
                    log.info("Mottok kvittering fra køen: $kvitteringQueueName. Se sikkerlogg for meldingsinnhold.")

                    message.getBody(String::class.java).let {
                        sikkerLogg.info("Kvittering lest fra $kvitteringQueueName, innhold:$it")
                        kvitteringConsumer.onMessage(it)
                    }
                }
            } catch (ex: Exception) {
                log.error(
                    "Feil ved prosessering av melding fra: $kvitteringQueueName. Se sikkerlogg for utdypende feil + context.",
                    RuntimeException("Trigger stacktrace for enklere debug."),
                )
                sikkerLogg.error("Feil ved prosessering av melding fra: $kvitteringQueueName. Melding: $message", ex)
                throw RuntimeException("Feil ved prosessering av melding fra: $kvitteringQueueName. Vi må kaste for å nacke meldingen. Se tilhørende errorlogg.")
            }
        }

        jmsContext.setExceptionListener { exception -> log.error("Feil mot $kvitteringQueueName", exception) }

        jmsContext.start()
    }
}
