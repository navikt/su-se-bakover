package no.nav.su.se.bakover.kafka

import io.ktor.util.KtorExperimentalAPI
import no.nav.su.meldinger.kafka.MessageBuilder.Companion.toProducerRecord
import no.nav.su.meldinger.kafka.Topics
import no.nav.su.meldinger.kafka.soknad.NySoknad
import no.nav.su.se.bakover.azure.TokenExchange
import no.nav.su.se.bakover.domain.SøknadObserver
import no.nav.su.se.bakover.person.PersonOppslag
import org.apache.kafka.clients.producer.KafkaProducer

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

        kafka.send(NySoknad(
                fnr = event.fnr.toString(),
                sakId = "${event.sakId}",
                aktoerId = aktoerId,
                soknadId = "${event.søknadId}",
                soknad = event.søknadstekst
        ).toProducerRecord(Topics.SOKNAD_TOPIC)
        )

    }
}

