package no.nav.su.se.bakover.client.oppdrag

import arrow.core.Either
import com.ibm.mq.jms.MQQueue
import no.nav.su.se.bakover.client.oppdrag.MqPublisher.CouldNotPublish
import no.nav.su.se.bakover.client.oppdrag.MqPublisher.MqPublisherConfig
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import javax.jms.JMSContext
import javax.jms.Session

class IbmMqPublisher(
    private val publisherConfig: MqPublisherConfig,
    private val jmsContext: JMSContext,
) : MqPublisher {

    private val log: Logger = LoggerFactory.getLogger(this::class.java)

    override fun publish(vararg messages: String): Either<CouldNotPublish, Unit> {
        if (publisherConfig.replyTo != null) {
            log.info("Publiserer ${messages.size} melding(er) på køen ${publisherConfig.sendQueue} med reply-kø ${publisherConfig.replyTo}")
        } else {
            log.info("Publiserer ${messages.size} melding(er) på køen  ${publisherConfig.sendQueue} uten reply-kø")
        }
        return jmsContext.createContext(Session.SESSION_TRANSACTED).use { context ->
            Either.catch {
                val producer = context.createProducer()
                messages.forEach {
                    producer.send(
                        MQQueue(publisherConfig.sendQueue),
                        context.createTextMessage(it).apply {
                            if (publisherConfig.replyTo != null) {
                                jmsReplyTo = MQQueue(publisherConfig.replyTo)
                            }
                        },
                    )
                }
                context.commit()
            }.mapLeft {
                log.error("Kunne ikke sende meldinger med config $publisherConfig, kaller rollback()", it)
                context.rollback()
                CouldNotPublish
            }
        }
    }
}
