package no.nav.su.se.bakover.client.oppdrag

import arrow.core.Either
import com.ibm.mq.jms.MQQueue
import kotlinx.coroutines.runBlocking
import no.nav.su.se.bakover.client.oppdrag.MqPublisher.CouldNotPublish
import no.nav.su.se.bakover.client.oppdrag.MqPublisher.MqPublisherConfig
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import javax.jms.JMSContext
import javax.jms.JMSProducer
import javax.jms.Session

class IbmMqPublisher(
    private val publisherConfig: MqPublisherConfig,
    private val jmsContext: JMSContext

) : MqPublisher {
    private val logger: Logger = LoggerFactory.getLogger(this::class.java)
    private var producer: JMSProducer = jmsContext.createProducer()

    init {
        publish(listOf("a", "b", "c"), true)
        publish(listOf("1", "2", "3"), false)
    }

    override fun publish(message: String): Either<CouldNotPublish, Unit> {
        logger.info("Sender melding: $message til kø ${publisherConfig.sendQueue} med svaradresse ${publisherConfig.replyTo}")
        return runBlocking {
            Either.catch {
                producer.send(
                    MQQueue(publisherConfig.sendQueue),
                    jmsContext.createTextMessage(message).apply {
                        jmsReplyTo = MQQueue(publisherConfig.replyTo)
                    }
                )
                Unit
            }.mapLeft {
                logger.error("Kunne ikke sende melding med config $publisherConfig", it)
                CouldNotPublish
            }
        }
    }

    override fun publish(messages: List<String>, commit: Boolean): Either<CouldNotPublish, Unit> {
        logger.info("Sender meldinger: $messages til kø ${publisherConfig.replyTo}")
        return jmsContext.createContext(Session.SESSION_TRANSACTED).use { context ->
            runBlocking {
                Either.catch {
                    val producer = context.createProducer()
                    messages.forEach {
                        producer.send(
                            MQQueue(publisherConfig.replyTo),
                            context.createTextMessage(it).apply {
                                // jmsReplyTo = MQQueue(publisherConfig.replyTo)
                            }
                        )
                    }
                    if (commit) context.commit() else context.rollback()
                    Unit
                }.mapLeft {
                    context.rollback()
                    CouldNotPublish
                }
            }
        }
    }
}
