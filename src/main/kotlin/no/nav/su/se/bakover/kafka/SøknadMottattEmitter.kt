package no.nav.su.se.bakover.kafka

import io.ktor.util.KtorExperimentalAPI
import no.nav.su.se.bakover.domain.SøknadObserver
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord

@KtorExperimentalAPI
internal class SøknadMottattEmitter(private val kafka: KafkaProducer<String, String>) :
    SøknadObserver {
    override fun søknadMottatt(event: SøknadObserver.SøknadMottattEvent) {
        kafka.send(
            ProducerRecord(
                KafkaConfigBuilder.Topics.SOKNAD_TOPIC,
                "${event.sakId}",
                """
                {
                    "soknadId":${event.søknadId},
                    "sakId":${event.sakId},
                    "soknad":${event.søknadstekst}
                }
            """.trimIndent())
        )
    }
}

