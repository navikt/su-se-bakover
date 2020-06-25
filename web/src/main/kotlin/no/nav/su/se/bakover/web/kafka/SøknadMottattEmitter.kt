package no.nav.su.se.bakover.web.kafka

import io.ktor.util.KtorExperimentalAPI
import no.nav.su.meldinger.kafka.Topics
import no.nav.su.meldinger.kafka.soknad.NySøknad
import no.nav.su.se.bakover.client.KafkaMessage
import no.nav.su.se.bakover.client.PersonOppslag
import no.nav.su.se.bakover.client.SuKafkaClient
import no.nav.su.se.bakover.common.CallContext
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.SakEventObserver

@KtorExperimentalAPI
internal class SøknadMottattEmitter(
    private val kafkaClient: SuKafkaClient,
    private val personClient: PersonOppslag
) : SakEventObserver {
    override fun nySøknadEvent(event: SakEventObserver.NySøknadEvent) {
        val søknadInnhold = event.søknadInnhold
        val aktørId = personClient.aktørId(Fnr(søknadInnhold.personopplysninger.fnr))
        val nySøknad = NySøknad(
            correlationId = CallContext.correlationId(),
            fnr = søknadInnhold.personopplysninger.fnr,
            sakId = "${event.sakId}",
            aktørId = aktørId,
            søknadId = "${event.søknadId}",
            søknad = søknadInnhold.toJson()
        )
        kafkaClient.send(KafkaMessage(Topics.SØKNAD_TOPIC, nySøknad.key(), nySøknad.value()))
    }
}
