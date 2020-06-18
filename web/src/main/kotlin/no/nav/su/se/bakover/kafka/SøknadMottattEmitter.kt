package no.nav.su.se.bakover.kafka

import io.ktor.util.KtorExperimentalAPI
import no.nav.su.meldinger.kafka.Topics
import no.nav.su.meldinger.kafka.soknad.NySøknad
import no.nav.su.se.bakover.CallContext
import no.nav.su.se.bakover.Fnr
import no.nav.su.se.bakover.PersonOppslag
import no.nav.su.se.bakover.SakEventObserver
import org.apache.kafka.clients.producer.KafkaProducer

@KtorExperimentalAPI
internal class SøknadMottattEmitter(
        private val kafka: KafkaProducer<String, String>,
        private val personClient: PersonOppslag
) : SakEventObserver {
    override fun nySøknadEvent(event: SakEventObserver.NySøknadEvent) {
        val søknadInnhold = event.søknadInnhold
        val aktørId = personClient.aktørId(Fnr(søknadInnhold.personopplysninger.fnr))
        kafka.send(NySøknad(
                correlationId = CallContext.correlationId(),
                fnr = søknadInnhold.personopplysninger.fnr,
                sakId = "${event.sakId}",
                aktørId = aktørId,
                søknadId = "${event.søknadId}",
                søknad = søknadInnhold.toJson()
        ).toProducerRecord(Topics.SØKNAD_TOPIC))
    }
}
