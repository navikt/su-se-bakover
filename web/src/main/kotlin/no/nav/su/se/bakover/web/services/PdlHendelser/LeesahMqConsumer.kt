package no.nav.su.se.bakover.web.services.PdlHendelser

import org.apache.kafka.clients.consumer.Consumer
import org.slf4j.LoggerFactory
import java.time.Duration

class LeesahMqConsumer(
    private val topicName: String,
    private val consumer: Consumer<String, String>
) {
    private val log = LoggerFactory.getLogger(this::class.java)
    private val POLL_TIMEOUT_DURATION = Duration.ofMillis(5000)

    init {
        log.info("Setter opp Kafka-Consumer som lytter på $topicName fra PDL")
        consumer.subscribe(listOf(topicName))
    }

    internal fun consume() {
        val messages = consumer.poll(POLL_TIMEOUT_DURATION)

        if(!messages.isEmpty) {
            messages.forEach { record ->
                println("offset: ${record.offset()}, key: ${record.key()}, value: ${record.value()}")
                consumer.commitSync() //TODO ai: commit async eller sync?
            }
        }
    }
}
