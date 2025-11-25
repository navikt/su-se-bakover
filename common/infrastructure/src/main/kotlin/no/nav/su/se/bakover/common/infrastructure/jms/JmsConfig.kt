package no.nav.su.se.bakover.common.infrastructure.jms

import com.ibm.mq.jms.MQConnectionFactory
import com.ibm.msg.client.jms.JmsConstants
import com.ibm.msg.client.wmq.WMQConstants
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import no.nav.su.se.bakover.common.infrastructure.config.ApplicationConfig
import org.slf4j.LoggerFactory
import javax.jms.JMSContext
import javax.jms.JMSException

/**
 * Wrapper class for JmsConfig
 *
 */

data class JmsConfig(
    private val applicationConfig: ApplicationConfig,
) {
    val log = LoggerFactory.getLogger(this::class.java)

    val jmsContext: JMSContext by lazy {
        runBlocking {
            try {
                withTimeout(10_000) {
                    createJmsContextWithTimeout()
                }.also {
                    log.info("JMSContext created successfully")
                }
            } catch (ex: JMSException) {
                log.error("Failed to create JMSContext: ${ex.message}", ex)
                throw ex // or handle/fallback if appropriate
            } catch (ex: TimeoutCancellationException) {
                log.error("JMSContext creation timed out", ex)
                throw ex
            }
        }
    }

    private fun createJmsContextWithTimeout(): JMSContext {
        log.info("Initializing JMSContext with timeout...")
        return MQConnectionFactory().apply {
            applicationConfig.oppdrag.let {
                hostName = it.mqHostname
                port = it.mqPort
                channel = it.mqChannel
                queueManager = it.mqQueueManager
                transportType = WMQConstants.WMQ_CM_CLIENT
                clientReconnectOptions = WMQConstants.WMQ_CLIENT_RECONNECT_Q_MGR
                clientReconnectTimeout = 5
                setBooleanProperty(JmsConstants.USER_AUTHENTICATION_MQCSP, true)
            }
        }.createContext(
            applicationConfig.serviceUser.username,
            applicationConfig.serviceUser.password,
        )
    }
}
