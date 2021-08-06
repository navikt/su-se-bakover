package no.nav.su.se.bakover.web.services.PdlHendelser

import no.nav.person.pdl.leesah.Endringstype
import no.nav.person.pdl.leesah.Personhendelse
import no.nav.person.pdl.leesah.doedsfall.Doedsfall
import no.nav.person.pdl.leesah.sivilstand.Sivilstand
import no.nav.person.pdl.leesah.utflytting.UtflyttingFraNorge
import no.nav.su.se.bakover.common.Tidspunkt
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.MockConsumer
import org.apache.kafka.clients.consumer.OffsetResetStrategy
import org.apache.kafka.common.TopicPartition
import org.junit.jupiter.api.BeforeEach
import java.time.LocalDate

internal class LeesahMqConsumerTest {
    private val TOPIC = "kafkaTopic"
    private val PARTITION = 0
    private val STARTING_OFFSET = 0

    private val mockConsumer = MockConsumer<String, Personhendelse>(OffsetResetStrategy.EARLIEST)

    @BeforeEach
    private fun setup() {
        mockConsumer.schedulePollTask {
            mockConsumer.rebalance(listOf(TopicPartition(TOPIC, STARTING_OFFSET)))

            val record = generatePdlMelding(0)
            val record2 = generatePdlMelding(1)
            val record3 = generatePdlMelding(2)

            mockConsumer.addRecord(record)
            mockConsumer.addRecord(record2)
            mockConsumer.addRecord(record3)
        }

        mockConsumer.updateBeginningOffsets(mapOf(TopicPartition(TOPIC, PARTITION) to STARTING_OFFSET.toLong()))
    }

    private fun generatePdlMelding(offset: Long): ConsumerRecord<String, Personhendelse> {
        val personhendelse = Personhendelse(
            "23",
            listOf("55"),
            "master",
            Tidspunkt.now().instant,
            "DOEDSFALL_V1",
            Endringstype.OPPRETTET,
            "22",
            Doedsfall(LocalDate.now()),
            Sivilstand("asd", LocalDate.now(), "hehe", LocalDate.now()),
            UtflyttingFraNorge("asd", "qwe", LocalDate.now())
        )
        return ConsumerRecord(TOPIC, PARTITION, offset, "1234567890000", personhendelse)
    }
}
