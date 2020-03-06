package no.nav.su.se.bakover.kafka

import io.ktor.util.KtorExperimentalAPI
import no.nav.su.meldinger.kafka.Topics
import no.nav.su.meldinger.kafka.soknad.NySøknad
import no.nav.su.se.bakover.azure.OAuth
import no.nav.su.se.bakover.domain.SøknadObserver
import no.nav.su.se.bakover.domain.SøknadObserver.SøknadMottattEvent
import no.nav.su.se.bakover.person.PersonOppslag
import org.apache.kafka.clients.producer.KafkaProducer

@KtorExperimentalAPI
internal class SøknadMottattEmitter(
    private val kafka: KafkaProducer<String, String>,
    private val azureClient: OAuth,
    private val suPersonClientId: String,
    private val personClient: PersonOppslag
) : SøknadObserver {
    override fun søknadMottatt(event: SøknadMottattEvent) {
        val token = azureClient.token(suPersonClientId)
        val aktørId = personClient.aktørId(event.fnr, token)
        kafka.send(event.somNySøknad(aktørId).toProducerRecord(Topics.SØKNAD_TOPIC))
    }
}
private fun SøknadMottattEvent.somNySøknad(aktørId: String): NySøknad = NySøknad(fnr = fnr.toString(), sakId = "${sakId}", aktørId = aktørId, søknadId = "${søknadId}", søknad = søknadstekst)
