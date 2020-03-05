package no.nav.su.se.bakover.kafka

import io.ktor.util.KtorExperimentalAPI
import no.nav.su.meldinger.kafka.Topics
import no.nav.su.meldinger.kafka.soknad.KafkaMessage.Companion.toProducerRecord
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
        val aktørId = personClient.aktørId(event.fnr, token)
        kafka.send(event.somNySøknad(aktørId).toProducerRecord(Topics.SOKNAD_TOPIC))
    }
}
private fun SøknadObserver.SøknadMottattEvent.somNySøknad(aktørId: String): NySoknad = NySoknad(fnr = fnr.toString(), sakId = "${sakId}", aktoerId = aktørId, soknadId = "${søknadId}", soknad = søknadstekst)
