package no.nav.su.se.bakover.client.oppdrag.utbetaling

import com.ibm.mq.jms.MQConnectionFactory
import com.ibm.msg.client.wmq.WMQConstants
import no.nav.su.se.bakover.common.Config
import javax.jms.Connection

object MqClient {

    fun jmsConnection(mqConfig: Config.Utbetaling): Connection =
        MQConnectionFactory().apply {
            hostName = mqConfig.mqHostname
            port = mqConfig.mqPort
            channel = mqConfig.mqChannel
            queueManager = mqConfig.mqQueueManager
            transportType = WMQConstants.WMQ_CM_CLIENT
        }.createConnection(mqConfig.mqUsername, mqConfig.mqPassword)
}
