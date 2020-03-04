package no.nav.su.se.bakover.kafka

import io.ktor.util.KtorExperimentalAPI
import no.nav.su.se.bakover.azure.AzureClient
import no.nav.su.se.bakover.azure.TokenExchange
import no.nav.su.se.bakover.domain.SøknadObserver
import no.nav.su.se.bakover.person.PersonOppslag
import no.nav.su.se.bakover.person.SuPersonClient
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord

@KtorExperimentalAPI
internal class SøknadMottattEmitter(
        private val kafka: KafkaProducer<String, String>,
        private val azureClient: TokenExchange,
        private val suPersonClientId: String,
        private val personClient: PersonOppslag
) : SøknadObserver {
    override fun søknadMottatt(event: SøknadObserver.SøknadMottattEvent) {
        val token = azureClient.token(suPersonClientId)
        val aktoerId = personClient.aktoerId(event.fnr, token)

        kafka.send(
                ProducerRecord(
                        KafkaConfigBuilder.Topics.SOKNAD_TOPIC,
                        "${event.sakId}",
                        """
                {
                    "soknadId":${event.søknadId},
                    "sakId":${event.sakId},
                    "soknad":${event.søknadstekst},
                    "aktoerId":"$aktoerId"
                }
            """.trimIndent())
        )
    }
}

