package no.nav.su.se.bakover.client.oppdrag

import arrow.core.Either
import com.ibm.mq.jms.MQQueue
import kotlinx.coroutines.runBlocking
import no.nav.su.se.bakover.client.oppdrag.MqPublisher.CouldNotPublish
import no.nav.su.se.bakover.client.oppdrag.MqPublisher.MqPublisherConfig
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import javax.jms.JMSContext
import javax.jms.Session

class IbmMqPublisher(
    private val publisherConfig: MqPublisherConfig,
    private val jmsContext: JMSContext
) : MqPublisher {
    private val log: Logger = LoggerFactory.getLogger(this::class.java)

    override fun publish(vararg messages: String): Either<CouldNotPublish, Unit> {
        log.info("Sender ${messages.size} til kø ${publisherConfig.replyTo}")
        return jmsContext.createContext(Session.SESSION_TRANSACTED).use { context ->
            runBlocking {
                Either.catch {
                    val producer = context.createProducer()
                    messages.forEach {
                        producer.send(
                            MQQueue(publisherConfig.sendQueue),
                            context.createTextMessage(it).apply {
                                jmsReplyTo = MQQueue(publisherConfig.replyTo)
                            }
                        )
                    }
                    log.info("${messages.size} meldinger sendt, kaller commit()")
                    context.commit()
                    Unit
                }.mapLeft {
                    log.error("Kunne ikke sende meldinger med config $publisherConfig, kaller rollback()", it)
                    context.rollback()
                    CouldNotPublish
                }
            }
        }
    }
}
