package no.nav.su.se.bakover.client.oppdrag

import arrow.core.left
import arrow.core.right
import com.ibm.mq.jms.MQQueue
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.mockito.kotlin.verify
import javax.jms.Destination
import javax.jms.JMSContext
import javax.jms.JMSProducer
import javax.jms.Message
import javax.jms.MessageFormatRuntimeException
import javax.jms.TextMessage

internal class IbmMqPublisherTest {

    private val parentJmsContext = mock(JMSContext::class.java)
    private val contextInUse = mock(JMSContext::class.java)
    private val producerMock = mock(JMSProducer::class.java)
    private val textMessageMock = mock(TextMessage::class.java)
    private val publisherConfig = MqPublisher.MqPublisherConfig(
        "sendQueue",
        "replyTo",
    )

    @BeforeEach
    fun beforeEach() {
        `when`(parentJmsContext.createContext(any(Int::class.java))).thenReturn(contextInUse)
        `when`(contextInUse.createProducer()).thenReturn(producerMock)
        `when`(contextInUse.createTextMessage(any())).then {
            textMessageMock.apply { text = it.getArgument(0) }
        }.thenReturn(textMessageMock)
    }

    @Test
    fun `should set message, sendQueue and replyTo`() {
        val destination = ArgumentCaptor.forClass(Destination::class.java)
        `when`(producerMock.send(destination.capture(), any(Message::class.java))).thenReturn(producerMock)

        val res = IbmMqPublisher(publisherConfig, parentJmsContext)
            .publish("message")
        verify(producerMock).send(any(Destination::class.java), any(Message::class.java))

        (destination.value as MQQueue).baseQueueName shouldBe publisherConfig.sendQueue
        verify(textMessageMock).text = "message"
        verify(textMessageMock).jmsReplyTo = MQQueue(publisherConfig.replyTo)

        res shouldBe Unit.right()
    }

    @Test
    fun `rollback when exception is thrown`() {
        `when`(producerMock.send(any(Destination::class.java), any(Message::class.java))).thenThrow(
            MessageFormatRuntimeException("error mate"),
        )

        val res = IbmMqPublisher(publisherConfig, parentJmsContext).publish("message")
        verify(producerMock).send(any(Destination::class.java), any(Message::class.java))
        verify(contextInUse).rollback()
        res shouldBe MqPublisher.CouldNotPublish.left()
    }

    @Test
    fun `commit when sent ok`() {
        `when`(producerMock.send(any(Destination::class.java), any(Message::class.java))).thenReturn(producerMock)

        val res = IbmMqPublisher(publisherConfig, parentJmsContext).publish("message")
        verify(producerMock).send(any(Destination::class.java), any(Message::class.java))
        verify(contextInUse).commit()
        res shouldBe Unit.right()
    }
}
