package no.nav.su.se.bakover.client.statistikk

import com.fasterxml.jackson.annotation.JsonInclude
import no.nav.su.se.bakover.common.Config
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.slf4j.LoggerFactory

internal class KafkaStatistikkProducer(
    private val producer: KafkaProducer<String, String> = KafkaProducer(Config.kafka.producerConfig)
) : StatistikkProducer {
    private val log = LoggerFactory.getLogger(this::class.java)

    override fun publiser(topic: Config.Kafka.StatistikkTopic, melding: String) {
        try {
            producer.send(ProducerRecord(topic.name, melding)) { recordMetadata, exception ->
                if (exception != null)
                    log.error("Feil ved publisering av statistikk til topic:${topic.name}", exception)
                else log.info("Publiserte statistikk til ${topic.name} med offset: ${recordMetadata.offset()}")
            }
        } catch (exception: Throwable) {
            log.error("Fanget exception ved utsending av statistikk til topic:${topic.name}", exception)
        }
    }
}

@JsonInclude(JsonInclude.Include.NON_NULL)
data class SakSchema(
    val funksjonellTid: String,
    val tekniskTid: String,
    val opprettetDato: String,
    val sakId: String,
    val aktorId: Int,
    val saksnummer: String,
    val ytelseType: String,
    val sakStatus: String,
    val avsender: String,
    val versjon: Int,
    val aktorer: List<Aktør>?,
    val underType: String?,
    val ytelseTypeBeskrivelse: String?,
    val underTypeBeskrivelse: String?,
    val sakStatusBeskrivelse: String?,
)

data class Aktør(val aktorId: Int, val rolle: String, val rolleBeskrivelse: String)
