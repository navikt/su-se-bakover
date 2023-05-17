package no.nav.su.se.bakover.common.infrastructure.jms

import com.ibm.mq.jms.MQConnectionFactory
import com.ibm.msg.client.wmq.WMQConstants
import no.nav.su.se.bakover.common.ApplicationConfig
import javax.jms.JMSContext

/**
 * Wrapper class for JmsConfig
 */
data class JmsConfig(
    private val applicationConfig: ApplicationConfig,
) {

    val jmsContext: JMSContext by lazy {
        createJmsContext()
    }

    private fun createJmsContext(): JMSContext {
        return MQConnectionFactory().apply {
            applicationConfig.oppdrag.let {
                hostName = it.mqHostname
                port = it.mqPort
                channel = it.mqChannel
                queueManager = it.mqQueueManager
                transportType = WMQConstants.WMQ_CM_CLIENT
                clientReconnectOptions = WMQConstants.WMQ_CLIENT_RECONNECT_Q_MGR
            }
        }.createContext(applicationConfig.serviceUser.username, applicationConfig.serviceUser.password)
    }
}
