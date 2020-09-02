package no.nav.su.se.bakover.client.oppdrag

import arrow.core.Either
import com.ibm.mq.jms.MQQueue
import kotlinx.coroutines.runBlocking
import no.nav.su.se.bakover.client.oppdrag.MqPublisher.CouldNotPublish
import no.nav.su.se.bakover.client.oppdrag.MqPublisher.MqPublisherConfig
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import javax.jms.Connection
import javax.jms.MessageProducer
import javax.jms.Session

class IbmMqPublisher(
    private val publisherConfig: MqPublisherConfig,
    connection: Connection

) : MqPublisher {
    private val logger: Logger = LoggerFactory.getLogger(this::class.java)
    private val session: Session = connection.createSession()
    private var producer: MessageProducer = session.createProducer(session.createQueue(publisherConfig.sendQueue))

    override fun publish(message: String): Either<CouldNotPublish, Unit> {
        logger.info("Sender melding: $message til kø ${publisherConfig.sendQueue} med svaradresse ${publisherConfig.replyTo}")
        return runBlocking {
            Either.catch {
                producer.send(
                    session.createTextMessage(message).apply {
                        jmsReplyTo = MQQueue(publisherConfig.replyTo)
                    }
                )
            }.mapLeft {
                logger.error("Kunne ikke sende melding med config $publisherConfig", it)
                CouldNotPublish
            }
        }
    }
}
