package no.nav.su.se.bakover.client.kafka

import no.nav.su.se.bakover.common.domain.kafka.KafkaPublisher
import no.nav.su.se.bakover.common.infrastructure.config.ApplicationConfig
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.errors.AuthorizationException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.LinkedList
import java.util.Queue
import java.util.Timer
import kotlin.concurrent.timer

private fun safeInitProducer(producerConfig: ApplicationConfig.KafkaConfig.ProducerCfg, log: Logger): KafkaProducer<String, String> {
    try {
        log.info("Attempting to initialize KafkaProducer with config:")
        producerConfig.kafkaConfig.forEach { (k, v) -> log.info("  $k = $v") }

        return KafkaProducer(producerConfig.kafkaConfig)
    } catch (e: Exception) {
        log.error("❌ Failed to initialize KafkaProducer", e)
        throw e
    }
}

internal class KafkaPublisherClient(
    private val producerConfig: ApplicationConfig.KafkaConfig.ProducerCfg,
    private val log: Logger = LoggerFactory.getLogger(KafkaPublisherClient::class.java),
    private val initProducer: () -> Producer<String, String> = { safeInitProducer(producerConfig, log) },
) : KafkaPublisher {
    private var producer: Producer<String, String> = initProducer()
    private val failed: Queue<ProducerRecord<String, String>> = LinkedList()

    init {
        retryTask(producerConfig)
    }

    override fun publiser(topic: String, melding: String) {
        val record = ProducerRecord<String, String>(topic, melding)
        try {
            producer.send(record) { recordMetadata, exception ->
                when (exception) {
                    null -> log.info("Publiserte meldig til $topic med offset: ${recordMetadata.offset()}")
                    is AuthorizationException -> {
                        log.warn("Autorisasjonsfeil ved publisering av melding til $topic, rekonfigurerer producer før retry.")
                        producer.close(Duration.ZERO)
                        producer = initProducer()
                        failed.add(record)
                    }
                    else -> {
                        log.error("Ukjent feil ved publisering av melding til topic:$topic", exception)
                        failed.add(record)
                    }
                }
            }
        } catch (exception: Throwable) {
            log.error("Fanget exception ved publisering av melding til topic:$topic", exception)
            failed.add(record)
        }
    }

    private fun retryTask(kafkaConfig: ApplicationConfig.KafkaConfig.ProducerCfg): Timer {
        val period = kafkaConfig.retryTaskInterval
        log.info("Konfigurerer retry task med periode $period for KafkaPublisherClient")
        return timer(
            name = "KafkaPublisherClient retry task",
            daemon = true,
            period = period.toMillis(),
        ) {
            failed.poll()?.let {
                log.info("Fant ${failed.size + 1} kafkameldinger som har feilet. Forsøker å sende første melding i køen på nytt.")
                publiser(it.topic(), it.value())
            }
        }
    }
}
