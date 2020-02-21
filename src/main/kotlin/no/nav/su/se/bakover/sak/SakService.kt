package no.nav.su.se.bakover.sak

import com.google.gson.JsonObject
import io.ktor.util.KtorExperimentalAPI
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.kafka.KafkaConfigBuilder.Topics.SOKNAD_TOPIC
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord

@KtorExperimentalAPI
internal class SakService(
        private val postgresRepository: PostgresRepository,
        private val hendelseProducer: KafkaProducer<String, String>
) : Repository by postgresRepository {
    fun lagreSøknad(fnr: String, søknad: JsonObject): Long? = finnSak(fnr).lagreSøknad(søknad)

    private fun finnSak(fnr: String): Sak =
            postgresRepository.hentSak(fnr)
                    ?: postgresRepository.opprettSak(fnr).let {
                        postgresRepository.hentSak(it)!! // Her bør den saken vi nettopp opprettet eksistere...
                    }

    private fun Sak.lagreSøknad(søknad: JsonObject): Long? {
        return postgresRepository.lagreSøknad(sakId = this.id, søknadJson = søknad)?.also {
            hendelseProducer.send(ProducerRecord(SOKNAD_TOPIC, """
                {
                    "soknadId":$it,
                    "sakId":${this.id},
                    "soknad":$søknad
                }
            """.trimIndent()))
        }
    }
}