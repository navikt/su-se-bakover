package no.nav.su.se.bakover.client.stubs

import java.time.Duration
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import org.apache.kafka.clients.consumer.OffsetAndMetadata
import org.apache.kafka.clients.producer.Callback
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.apache.kafka.common.Metric
import org.apache.kafka.common.MetricName
import org.apache.kafka.common.PartitionInfo
import org.apache.kafka.common.TopicPartition
import org.slf4j.LoggerFactory

open class KafkaProducerStub : Producer<String, String> {
    val sentRecords = mutableListOf<ProducerRecord<String, String>>()

    override fun metrics(): MutableMap<MetricName, out Metric> = throw NotImplementedError()

    override fun partitionsFor(topic: String?): MutableList<PartitionInfo> = throw NotImplementedError()

    override fun flush() = throw NotImplementedError()

    override fun abortTransaction() = throw NotImplementedError()

    override fun commitTransaction() = throw NotImplementedError()

    override fun beginTransaction() = throw NotImplementedError()

    override fun initTransactions() = throw NotImplementedError()

    override fun sendOffsetsToTransaction(
        offsets: MutableMap<TopicPartition, OffsetAndMetadata>?,
        consumerGroupId: String?
    ) = throw NotImplementedError()

    override fun send(record: ProducerRecord<String, String>?): Future<RecordMetadata> {
        logger.info("Sending record:$record")
        record?.let { sentRecords.add(it) }
        return KafkaResponse
    }

    override fun send(record: ProducerRecord<String, String>?, callback: Callback?): Future<RecordMetadata> {
        logger.info("Sending record and calling callback, record:$record")
        record?.let { sentRecords.add(it) }
        return KafkaResponse.also { callback?.onCompletion(it.get(), null) }
    }

    override fun close() = throw NotImplementedError()

    override fun close(timeout: Duration?) = throw NotImplementedError()

    private val logger = LoggerFactory.getLogger(KafkaProducerStub::class.java)
}

object KafkaResponse : Future<RecordMetadata> {
    override fun isDone(): Boolean = true
    override fun get(): RecordMetadata = RecordMetadata(TopicPartition("topic", 0), 0, 0, 0, 0, 0, 0)
    override fun get(timeout: Long, unit: TimeUnit) = get()
    override fun cancel(mayInterruptIfRunning: Boolean): Boolean = false
    override fun isCancelled(): Boolean = false
}
