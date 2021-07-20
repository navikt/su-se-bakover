package no.nav.su.se.bakover.web.services.PdlHendelser

import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.MockConsumer
import org.apache.kafka.clients.consumer.OffsetResetStrategy
import org.apache.kafka.common.TopicPartition
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class LeesahMqConsumerTest {
    private val TOPIC = "kafkaTopic"
    private val PARTITION = 0
    private val STARTING_OFFSET = 0

    private val mockConsumer = MockConsumer<String, String>(OffsetResetStrategy.EARLIEST)

    @BeforeEach
    private fun setup() {
        mockConsumer.schedulePollTask {
            mockConsumer.rebalance(listOf(TopicPartition(TOPIC, STARTING_OFFSET)))
            mockConsumer.addRecord(ConsumerRecord(TOPIC, PARTITION, 7, "keyzzz", "valuezzz"))
            mockConsumer.addRecord(ConsumerRecord(TOPIC, PARTITION, 8, "keyzzz", "valuezzz"))
        }

        mockConsumer.updateBeginningOffsets(mapOf(TopicPartition(TOPIC, 0) to 5))
    }

    @Test
    internal fun testzz() {
        val leesahConsumer = LeesahMqConsumer(topicName = TOPIC, consumer = mockConsumer)

        leesahConsumer.consume()
    }
}
