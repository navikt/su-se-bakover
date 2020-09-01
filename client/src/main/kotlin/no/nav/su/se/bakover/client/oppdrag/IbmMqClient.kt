package no.nav.su.se.bakover.client.oppdrag

import arrow.core.Either
import com.ibm.mq.jms.MQConnectionFactory
import com.ibm.mq.jms.MQQueue
import com.ibm.msg.client.wmq.WMQConstants
import kotlinx.coroutines.runBlocking
import no.nav.su.se.bakover.client.oppdrag.MqClient.CouldNotPublish
import no.nav.su.se.bakover.client.oppdrag.MqClient.MqConfig
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import javax.jms.Connection
import javax.jms.MessageProducer
import javax.jms.Session

class IbmMqClient(
    private val config: MqConfig

) : MqClient {
    private val logger: Logger = LoggerFactory.getLogger(this::class.java)
    private val connection: Connection = MQConnectionFactory().apply {
        hostName = config.hostname
        port = config.port
        channel = config.channel
        queueManager = config.queueManager
        transportType = WMQConstants.WMQ_CM_CLIENT
    }.createConnection(config.username, config.password)
    private val session: Session = connection.createSession()
    private var producer: MessageProducer = session.createProducer(session.createQueue(config.sendQueue))

    override fun publish(message: String): Either<CouldNotPublish, Unit> {
        logger.info("Sender melding til ${config.hostname} på kø ${config.sendQueue} med svaradresse ${config.replyTo}")
        return runBlocking {
            Either.catch {
                producer.send(
                    session.createTextMessage(message).apply {
                        jmsReplyTo = MQQueue(config.replyTo)
                    }
                )
            }.mapLeft {
                logger.error("Kunne ikke sende melding med config $config", it)
                CouldNotPublish
            }
        }
    }
}
