package no.nav.su.se.bakover.common.infrastructure.jms

import com.ibm.mq.jms.MQConnectionFactory
import com.ibm.msg.client.jms.JmsConstants
import com.ibm.msg.client.wmq.WMQConstants
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import no.nav.su.se.bakover.common.infrastructure.config.ApplicationConfig
import org.slf4j.LoggerFactory
import javax.jms.ExceptionListener
import javax.jms.JMSContext
import javax.jms.JMSException
import kotlin.system.exitProcess

/**
 * Wrapper class for JmsConfig
 * Vurder     it.setIntProperty(WMQConstants.JMS_IBM_CHARACTER_SET, UTF_8_WITH_PUA)
 * private const val UTF_8_WITH_PUA = 1208
 */

data class JmsConfig(
    private val applicationConfig: ApplicationConfig,
) {
    val log = LoggerFactory.getLogger(this::class.java)

    private fun exceptionListener() =
        ExceptionListener {
            log.error(
                "En feil oppstod med tilkoblingen mot jms: ${it.message}. " +
                    "Restarter appen for å sette opp tilkobling på nytt",
                it,
            )

            // Trigger restart av appen for å sette opp tilkobling mot MQ på nytt
            exitProcess(-1)
        }
    val jmsContext: JMSContext?

    /*
        Denne sørger for at jmscontext blir startet i main tråden slik at ingen coroutines evt får en deadlock her.
        Forbedring er å gjøre dette i susebakover(.. oppsettet men det var fult av if else på env for å tilpasse testoppsett så gjorde det slik for å unngå rewrite av hele oppsettet.
     */
    init {
        jmsContext = if (applicationConfig.runtimeEnvironment == ApplicationConfig.RuntimeEnvironment.Nais) {
            runBlocking {
                try {
                    withTimeout(10_000) {
                        createJmsContextWithTimeout()
                    }.also { ctx ->
                        log.info("JMSContext created successfully")
                        ctx.exceptionListener = exceptionListener()
                    }
                } catch (ex: JMSException) {
                    log.error("Failed to create JMSContext: ${ex.message}", ex)
                    throw ex
                } catch (ex: TimeoutCancellationException) {
                    log.error("JMSContext creation timed out", ex)
                    throw ex
                }
            }
        } else {
            null
        }
    }

    private fun createJmsContextWithTimeout(): JMSContext {
        log.info("Initializing JMSContext with timeout...")
        System.setProperty("com.ibm.mq.cfg.TCP.Connect_Timeout", "10")
        return MQConnectionFactory().apply {
            applicationConfig.oppdrag.let {
                hostName = it.mqHostname
                port = it.mqPort
                channel = it.mqChannel
                queueManager = it.mqQueueManager
                transportType = WMQConstants.WMQ_CM_CLIENT
                clientReconnectOptions = WMQConstants.WMQ_CLIENT_RECONNECT_Q_MGR
                clientReconnectTimeout = 300
                setBooleanProperty(JmsConstants.USER_AUTHENTICATION_MQCSP, true)
            }
        }.createContext(
            applicationConfig.serviceUser.username,
            applicationConfig.serviceUser.password,
        ).apply { exceptionListener = exceptionListener() }
    }
}
