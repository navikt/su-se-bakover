package no.nav.su.se.bakover.client.stubs

import java.time.Duration
import java.util.concurrent.Future
import org.apache.kafka.clients.consumer.OffsetAndMetadata
import org.apache.kafka.clients.producer.Callback
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.apache.kafka.clients.producer.internals.FutureRecordMetadata
import org.apache.kafka.clients.producer.internals.ProduceRequestResult
import org.apache.kafka.common.Metric
import org.apache.kafka.common.MetricName
import org.apache.kafka.common.PartitionInfo
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.utils.Time
import org.slf4j.LoggerFactory

object KafkaProducerStub : Producer<String, String> {
    val sentRecords = mutableListOf<ProducerRecord<String, String>>()

    override fun metrics(): MutableMap<MetricName, out Metric> = throw NotImplementedError()

    override fun partitionsFor(topic: String?): MutableList<PartitionInfo> = throw NotImplementedError()

    override fun flush() = throw NotImplementedError()

    override fun abortTransaction() = throw NotImplementedError()

    override fun commitTransaction() = throw NotImplementedError()

    override fun beginTransaction() = throw NotImplementedError()

    override fun initTransactions() = throw NotImplementedError()

    override fun sendOffsetsToTransaction(offsets: MutableMap<TopicPartition, OffsetAndMetadata>?, consumerGroupId: String?) = throw NotImplementedError()

    override fun send(record: ProducerRecord<String, String>?): Future<RecordMetadata> {
        logger.info("Sending record", record)
        record?.let { sentRecords.add(it) }
        return getFutureRecordMetadata(record)
    }

    override fun send(record: ProducerRecord<String, String>?, callback: Callback?): Future<RecordMetadata> {
        logger.info("Sending record and calling callback", record)
        record?.let { sentRecords.add(it) }
        return getFutureRecordMetadata(record)
                .also { callback?.onCompletion(it.get(), null) }
    }

    override fun close() = throw NotImplementedError()

    override fun close(timeout: Duration?) = throw NotImplementedError()

    private val logger = LoggerFactory.getLogger(KafkaProducerStub::class.java)

    private fun getFutureRecordMetadata(record: ProducerRecord<String, String>?) =
            FutureRecordMetadata(ProduceRequestResult(TopicPartition(record!!.topic(), 0)), 0, 0, 0, 0, 0, Time.SYSTEM)
}
