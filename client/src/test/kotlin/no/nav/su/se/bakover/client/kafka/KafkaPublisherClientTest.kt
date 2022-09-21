package no.nav.su.se.bakover.client.kafka

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.ApplicationConfig
import org.apache.kafka.clients.producer.MockProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.errors.TopicAuthorizationException
import org.apache.kafka.common.serialization.StringSerializer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.slf4j.helpers.NOPLogger
import java.time.Duration

internal class KafkaPublisherClientTest {

    private val config = ApplicationConfig.KafkaConfig.ProducerCfg(
        kafkaConfig = mapOf(),
        retryTaskInterval = Duration.ofMillis(1),
    )

    @Test
    fun `publiserer meldinger til kafka`() {
        val producers: MutableList<MockProducer<String, String>> = mutableListOf()
        KafkaPublisherClient(producerConfig = config, log = NOPLogger.NOP_LOGGER) { autoProducer(producers) }.publiser(
            topic = "happy",
            melding = "path",
        )
        producers shouldHaveSize 1
        producers.first().let {
            it.history() shouldBe listOf(ProducerRecord("happy", "path"))
        }
    }

    @Test
    fun `fanger exception hvis kall til send() feiler`() {
        val producers: MutableList<MockProducer<String, String>> = mutableListOf()
        assertDoesNotThrow {
            KafkaPublisherClient(producerConfig = config, log = NOPLogger.NOP_LOGGER) {
                autoProducer(producers).apply {
                    sendException = IllegalStateException()
                }
            }.publiser(
                topic = "not so happy",
                melding = "path",
            )
            producers shouldHaveSize 1
            producers.first().history() shouldBe emptyList()
        }
    }

    @Test
    fun `overlever ukjente feil i callback`() {
        val producers: MutableList<MockProducer<String, String>> = mutableListOf()
        assertDoesNotThrow {
            KafkaPublisherClient(producerConfig = config, log = NOPLogger.NOP_LOGGER) { manualProducer(producers) }.publiser(
                topic = "not so happy",
                melding = "path",
            )

            val producer = producers.first()
            producer.errorNext(RuntimeException())

            producers shouldHaveSize 1
        }
    }

    @Test
    fun `stenger eksisterende producer og oppretter ny dersom callback responderer med authorization-exception`() {
        val producers: MutableList<MockProducer<String, String>> = mutableListOf()
        KafkaPublisherClient(producerConfig = config, log = NOPLogger.NOP_LOGGER) { manualProducer(producers) }.publiser(
            topic = "not so happy",
            melding = "path",
        )

        val producer = producers.first()
        // fail with auth-exception to invoke instantiation of new producer
        producer.errorNext(TopicAuthorizationException("forbidden"))

        producers shouldHaveSize 2
        producers.let {
            it[0].closed() shouldBe true // first instance should be closed
            it[1].closed() shouldBe false // second instance should be alive
        }
    }

    @Test
    fun `forsøker å sende meldinger som har feilet på nytt`() {
        val producers: MutableList<MockProducer<String, String>> = mutableListOf()
        KafkaPublisherClient(producerConfig = config, log = NOPLogger.NOP_LOGGER) { manualProducer(producers) }.publiser(
            topic = "not so happy",
            melding = "path",
        )

        val producer = producers.first()
        producer.errorNext(TopicAuthorizationException("forbidden"))
        producer.completeNext()

        Thread.sleep(50)

        producers shouldHaveSize 2
        producers.let {
            it[0].history() shouldHaveSize 1
            it[1].history() shouldHaveSize 1
            it[0].history() shouldBe it[1].history() // attempt to send the same record
        }
    }

    /**
     * autoCompleteFuture = true -> complete future immediately and invoke callback
     */
    private fun autoProducer(producers: MutableList<MockProducer<String, String>> = mutableListOf()): MockProducer<String, String> =
        MockProducer(true, StringSerializer(), StringSerializer()).also { producers.add(it) }

    /**
     * autoCompleteFuture = false -> completion of future is controlled manually through completeNext()/errorNext() before callback is invoked.
     */
    private fun manualProducer(producers: MutableList<MockProducer<String, String>> = mutableListOf()): MockProducer<String, String> =
        MockProducer(false, StringSerializer(), StringSerializer()).also { producers.add(it) }
}
