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
import no.nav.su.se.bakover.common.extensions.januar
import no.nav.su.se.bakover.common.extensions.juni
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.service.personhendelser.PersonhendelseServiceImpl
import no.nav.su.se.bakover.common.tid.toTidspunkt
import no.nav.su.se.bakover.service.personhendelser.PersonhendelseService
import no.nav.su.se.bakover.test.fixedLocalDate
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.generer
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.MockConsumer
import org.apache.kafka.clients.consumer.OffsetAndMetadata
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
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

private const val TOPIC = "kafkaTopic"
private const val PARTITION = 0

internal class PersonhendelseConsumerKafkaTest {

    private val fnr = Fnr.generer()
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
        val personhendelseService = mock<PersonhendelseServiceImpl>()
        PersonhendelseConsumer(
            consumer = kafkaConsumer,
            personhendelseService = personhendelseService,
            topicName = TOPIC,
            pollTimeoutDuration = Duration.ofMillis(500),
            // Don't spam logs running tests
            log = NOPLogger.NOP_LOGGER,
            // Don't spam logs running tests
            sikkerLogg = NOPLogger.NOP_LOGGER,
        )
        kafkaConsumer.lastComittedShouldBe(6)
        val hendelser = argumentCaptor<no.nav.su.se.bakover.domain.personhendelse.Personhendelse.IkkeTilknyttetSak>()
        verify(personhendelseService, timeout(5000).times(6)).prosesserNyHendelse(hendelser.capture())
        hendelser.allValues shouldBe (0..5L).map {
            no.nav.su.se.bakover.domain.personhendelse.Personhendelse.IkkeTilknyttetSak(
                endringstype = no.nav.su.se.bakover.domain.personhendelse.Personhendelse.Endringstype.OPPRETTET,
                hendelse = no.nav.su.se.bakover.domain.personhendelse.Personhendelse.Hendelse.Dødsfall(fixedLocalDate),
                metadata = no.nav.su.se.bakover.domain.personhendelse.Personhendelse.Metadata(
                    personidenter = nonEmptyListOf(ident, fnr.toString()),
                    hendelseId = it.toString(),
                    tidligereHendelseId = null,
                    offset = it,
                    partisjon = PARTITION,
                    master = "FREG",
                    key = ident,
                    eksternOpprettet = fixedTidspunkt.instant.truncatedTo(ChronoUnit.MILLIS).toTidspunkt(),
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

        val personhendelseService = mock<PersonhendelseServiceImpl>()
        PersonhendelseConsumer(
            consumer = kafkaConsumer,
            personhendelseService = personhendelseService,
            topicName = TOPIC,
            pollTimeoutDuration = Duration.ofMillis(500),
            // Don't spam logs running tests
            log = NOPLogger.NOP_LOGGER,
            // Don't spam logs running tests
            sikkerLogg = NOPLogger.NOP_LOGGER,
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
            // hendelseId (UUID)
            offset.toString(),

            // personIdenter (liste med mix av fnr(11 siffer), ident(13 siffer), ++?)
            personIdenter,

            // master (f.eks. FREG)
            "FREG",

            // opprettet(f.eks. 2021-08-02T09:03:34.900Z)
            fixedTidspunkt.instant,

            // opplysningstype (DOEDSFALL_V1,UTFLYTTING_FRA_NORGE,SIVILSTAND_V1)
            "DOEDSFALL_V1",

            // endringstype (OPPRETTET,KORRIGERT,ANNULLERT,OPPHOERT)
            Endringstype.OPPRETTET,

            // tidligereHendelseId (Peker til tidligere hendelse ved korrigering og annullering.)
            null,

            // doedsfall (https://navikt.github.io/pdl/#_d%C3%B8dsfall)
            Doedsfall(fixedLocalDate),

            // sivilstand (https://navikt.github.io/pdl/#_sivilstand)
            Sivilstand(
                "GIFT",
                fixedLocalDate,
                "12345678910",
                null,
            ),

            // utflyttingFraNorge (https://navikt.github.io/pdl/#_utflytting)
            UtflyttingFraNorge(
                "ESP",
                "Barcelona",
                fixedLocalDate,
            ),
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

    private fun MockConsumer<String, Personhendelse>.lastComittedShouldBe(
        shouldBeOffset: Int,
        topic: String = TOPIC,
        partition: Int = PARTITION,
    ) {
        val topicPartition = TopicPartition(topic, partition)
        org.awaitility.Awaitility.await().atLeast(100, TimeUnit.MILLISECONDS).until {
            this.committed(setOf(topicPartition)) == mapOf(
                topicPartition to OffsetAndMetadata(shouldBeOffset.toLong()),
            )
        }
    }
}
