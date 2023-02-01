package no.nav.su.se.bakover.web.services.personhendelser

import arrow.core.nonEmptyListOf
import io.kotest.assertions.fail
import io.kotest.matchers.shouldBe
import no.nav.person.pdl.leesah.Endringstype
import no.nav.person.pdl.leesah.Personhendelse
import no.nav.person.pdl.leesah.bostedsadresse.Bostedsadresse
import no.nav.person.pdl.leesah.doedsfall.Doedsfall
import no.nav.person.pdl.leesah.kontaktadresse.Kontaktadresse
import no.nav.person.pdl.leesah.sivilstand.Sivilstand
import no.nav.person.pdl.leesah.utflytting.UtflyttingFraNorge
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.juni
import no.nav.su.se.bakover.service.personhendelser.PersonhendelseService
import no.nav.su.se.bakover.test.fixedLocalDate
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.generer
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.MockConsumer
import org.apache.kafka.clients.consumer.OffsetResetStrategy
import org.apache.kafka.common.TopicPartition
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.timeout
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.slf4j.helpers.NOPLogger
import java.time.Duration

private const val TOPIC = "kafkaTopic"
private const val PARTITION = 0

internal class PersonhendelseConsumerKafkaTest {

    private val fnr = no.nav.su.se.bakover.common.Fnr.generer()
    private val ident = fnr.toString() + "00"
    private val key = "\u0000$ident"

    @Test
    fun `Mottar alle personhendelsene`() {
        val topicPartion = TopicPartition(TOPIC, PARTITION)
        val kafkaConsumer = MockConsumer<String, Personhendelse>(OffsetResetStrategy.EARLIEST)
        kafkaConsumer.updateBeginningOffsets(mapOf(topicPartion to 0))
        (0..5L).map {
            kafkaConsumer.schedulePollTask {
                if (it == 0L) {
                    kafkaConsumer.rebalance(mutableListOf(topicPartion))
                }
                kafkaConsumer.addRecord(generatePdlMelding(it))
            }
        }
        val personhendelseService = mock<PersonhendelseService>()
        PersonhendelseConsumer(
            consumer = kafkaConsumer,
            personhendelseService = personhendelseService,
            topicName = TOPIC,
            pollTimeoutDuration = Duration.ofMillis(500),
            log = NOPLogger.NOP_LOGGER, // Don't spam logs running tests
            sikkerLogg = NOPLogger.NOP_LOGGER, // Don't spam logs running tests
        )

        val hendelser = argumentCaptor<no.nav.su.se.bakover.domain.personhendelse.Personhendelse.IkkeTilknyttetSak>()
        verify(personhendelseService, timeout(1000).times(6)).prosesserNyHendelse(hendelser.capture())
        hendelser.allValues shouldBe (0..5L).map {
            no.nav.su.se.bakover.domain.personhendelse.Personhendelse.IkkeTilknyttetSak(
                endringstype = no.nav.su.se.bakover.domain.personhendelse.Personhendelse.Endringstype.OPPRETTET,
                hendelse = no.nav.su.se.bakover.domain.personhendelse.Personhendelse.Hendelse.Dødsfall(no.nav.su.se.bakover.test.fixedLocalDate),
                metadata = no.nav.su.se.bakover.domain.personhendelse.Personhendelse.Metadata(
                    personidenter = nonEmptyListOf(ident, fnr.toString()),
                    hendelseId = it.toString(),
                    tidligereHendelseId = null,
                    offset = it,
                    partisjon = PARTITION,
                    master = "FREG",
                    key = ident,
                ),
            )
        }
        verifyNoMoreInteractions(personhendelseService)
    }

    @Test
    fun `commit timeout vil ikke polle neste melding `() {
        val topicPartion = TopicPartition(TOPIC, PARTITION)
        val kafkaConsumer = MockConsumer<String, Personhendelse>(OffsetResetStrategy.EARLIEST)
        kafkaConsumer.updateBeginningOffsets(mapOf(topicPartion to 0))
        kafkaConsumer.schedulePollTask {
            kafkaConsumer.rebalance(mutableListOf(topicPartion))
            kafkaConsumer.addRecord(ConsumerRecord(TOPIC, PARTITION, 0, key, null))
        }
        kafkaConsumer.schedulePollTask {
            fail("Den forrige pollen førte til en exception, som skal gi oss en 60s delay.")
        }

        val personhendelseService = mock<PersonhendelseService>()
        PersonhendelseConsumer(
            consumer = kafkaConsumer,
            personhendelseService = personhendelseService,
            topicName = TOPIC,
            pollTimeoutDuration = Duration.ofMillis(500),
            log = NOPLogger.NOP_LOGGER, // Don't spam logs running tests
            sikkerLogg = NOPLogger.NOP_LOGGER, // Don't spam logs running tests
        )
        Thread.sleep(2000) // Venter deretter en liten stund til for å verifisere at det ikke kommer fler kall.
        verify(personhendelseService, timeout(1000).times(0)).prosesserNyHendelse(any())
        verifyNoMoreInteractions(personhendelseService)
        kafkaConsumer.committed(setOf(topicPartion)) shouldBe mapOf()
        kafkaConsumer.shouldRebalance() shouldBe true
    }

    private fun generatePdlMelding(
        offset: Long,
        topic: String = TOPIC,
        partition: Int = PARTITION,
        personIdenter: List<String> = listOf(ident, fnr.toString()),
    ): ConsumerRecord<String, Personhendelse> {
        val vegadresse = no.nav.person.pdl.leesah.common.adresse.Vegadresse(
            "matrikkelId", "husnummer",
            "husbokstav", "bruksenhetsnummer",
            "adressenavn", "kommunenummer",
            "bydelsnummer", "tilleggsnavn",
            "postnummer", null,
        )

        val personhendelse = Personhendelse(
            offset.toString(), // hendelseId (UUID)
            personIdenter, // personIdenter (liste med mix av fnr(11 siffer), ident(13 siffer), ++?)
            "FREG", // master (f.eks. FREG)
            fixedTidspunkt.instant, // opprettet(f.eks. 2021-08-02T09:03:34.900Z)
            "DOEDSFALL_V1", // opplysningstype (DOEDSFALL_V1,UTFLYTTING_FRA_NORGE,SIVILSTAND_V1)
            Endringstype.OPPRETTET, // endringstype (OPPRETTET,KORRIGERT,ANNULLERT,OPPHOERT)
            null, // tidligereHendelseId (Peker til tidligere hendelse ved korrigering og annullering.)
            Doedsfall(fixedLocalDate), // doedsfall (https://navikt.github.io/pdl/#_d%C3%B8dsfall)
            Sivilstand(
                "GIFT",
                fixedLocalDate,
                "12345678910",
                null,
            ), // sivilstand (https://navikt.github.io/pdl/#_sivilstand)
            UtflyttingFraNorge(
                "ESP",
                "Barcelona",
                fixedLocalDate,
            ), // utflyttingFraNorge (https://navikt.github.io/pdl/#_utflytting)
            Kontaktadresse(
                1.januar(2021), 5.juni(2025),
                "Innland", "coAdressenavn", null,
                vegadresse, null, null, null,
            ),
            Bostedsadresse(
                1.januar(2021),
                1.januar(2021),
                5.juni(2025),
                "coAdressenavn",
                vegadresse,
                null,
                null,
                null,
            ),
        )
        // Emulerer at PDL-kafka legger på 0-byte
        return ConsumerRecord(topic, partition, offset, key, personhendelse)
    }
}
