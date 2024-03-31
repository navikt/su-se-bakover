package no.nav.su.se.bakover.web.services.klage.klageinstans

import behandling.klage.domain.UprosessertKlageinstanshendelse
import io.kotest.assertions.fail
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.service.klage.KlageinstanshendelseService
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedTidspunkt
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
import java.util.UUID
import java.util.concurrent.TimeUnit

private const val TOPIC = "kafkaTopic"
private const val PARTITION = 0

internal class KlageinstanshendelseConsumerKafkaTest {

    private val key = UUID.randomUUID().toString()

    @Test
    fun `Lagrer aktuelle og forkaster uaktuelle`() {
        val topicPartion = TopicPartition(
            TOPIC,
            PARTITION,
        )
        val kafkaConsumer = MockConsumer<String, String>(OffsetResetStrategy.EARLIEST)
        kafkaConsumer.updateBeginningOffsets(mapOf(topicPartion to 0))

        kafkaConsumer.genererKlageinstanshendelseMelding(0, "SUPSTONAD", withRebalance = true)
        kafkaConsumer.genererKlageinstanshendelseMelding(1, "ANNENSTONAD")
        kafkaConsumer.genererKlageinstanshendelseMelding(2, "SUPSTONAD")

        val klageinstanshendelseService = mock<KlageinstanshendelseService>()
        KlageinstanshendelseConsumer(
            consumer = kafkaConsumer,
            klageinstanshendelseService = klageinstanshendelseService,
            topicName = TOPIC,
            pollTimeoutDuration = Duration.ofMillis(500),
            clock = fixedClock,
            // Don't spam logs running tests
            log = NOPLogger.NOP_LOGGER,
            // Don't spam logs running tests
            sikkerLogg = NOPLogger.NOP_LOGGER,
        )
        val hendelser = argumentCaptor<UprosessertKlageinstanshendelse>()
        kafkaConsumer.lastComittedOffsetShouldBe(3)
        verify(klageinstanshendelseService, timeout(100).times(2)).lagre(any())

        kafkaConsumer.genererKlageinstanshendelseMelding(3, "ANNENSTONAD")
        kafkaConsumer.genererKlageinstanshendelseMelding(4, "SUPSTONAD")
        kafkaConsumer.genererKlageinstanshendelseMelding(5, "ANNENSTONAD")
        kafkaConsumer.lastComittedOffsetShouldBe(6)
        verify(klageinstanshendelseService, timeout(100).times(3)).lagre(hendelser.capture())
        hendelser.allValues.size shouldBe 3
        hendelser.allValues.forEachIndexed { index, uprosessertKlageinstanshendelse ->
            UprosessertKlageinstanshendelse(
                id = uprosessertKlageinstanshendelse.id,
                opprettet = fixedTidspunkt,
                metadata = UprosessertKlageinstanshendelse.Metadata(
                    topic = TOPIC,
                    hendelseId = index.toString(),
                    offset = index.toLong(),
                    partisjon = PARTITION,
                    key = key,
                    value = """
                        {
                          "eventId": "$index",
                          "kildeReferanse":"$index",
                          "kilde":"SUPSTONAD",
                          "utfall":"TRUKKET",
                          "vedtaksbrevReferanse":null,
                          "kabalReferanse":"$index"
                        }
                    """.trimIndent(),
                ),
            )
        }
        verifyNoMoreInteractions(klageinstanshendelseService)
        kafkaConsumer.lastComittedOffsetShouldBe(6) // last offset (5) + 1
    }

    @Test
    fun `Fant ikke kilde comitter siste leste melding og kaller en rebalance`() {
        val topicPartion = TopicPartition(
            TOPIC,
            PARTITION,
        )
        val kafkaConsumer = MockConsumer<String, String>(OffsetResetStrategy.EARLIEST)
        kafkaConsumer.updateBeginningOffsets(mapOf(topicPartion to 0))

        kafkaConsumer.genererKlageinstanshendelseMelding(0, "SUPSTONAD", withRebalance = true)
        kafkaConsumer.schedulePollTask {
            // Tvinger en FantIkkeKilde.left()
            kafkaConsumer.addRecord(
                ConsumerRecord(
                    TOPIC,
                    PARTITION,
                    1,
                    "1",
                    "{}",
                ),
            )
        }
        kafkaConsumer.schedulePollTask {
            fail("Den forrige pollen førte til en exception, som skal gi oss en 60s delay.")
        }

        val klageinstanshendelseService = mock<KlageinstanshendelseService>()
        KlageinstanshendelseConsumer(
            consumer = kafkaConsumer,
            klageinstanshendelseService = klageinstanshendelseService,
            topicName = TOPIC,
            pollTimeoutDuration = Duration.ofMillis(1000),
            clock = fixedClock,
            // Don't spam logs running tests
            log = NOPLogger.NOP_LOGGER,
            // Don't spam logs running tests
            sikkerLogg = NOPLogger.NOP_LOGGER,
        )
        // Venter først akkurat til vi har fått et kall til klageinstanshendelseService (som forventet)
        verify(klageinstanshendelseService, timeout(1000)).lagre(any())
        Thread.sleep(2000) // Venter deretter en liten stund til for å verifisere at det ikke kommer fler kall.
        verifyNoMoreInteractions(klageinstanshendelseService)
        kafkaConsumer.lastComittedOffsetShouldBe(1)
        kafkaConsumer.shouldRebalance() shouldBe true
    }

    private fun MockConsumer<String, String>.lastComittedOffsetShouldBe(
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

    private fun MockConsumer<String, String>.genererKlageinstanshendelseMelding(
        offset: Long,
        kilde: String,
        withRebalance: Boolean = false,
        topic: String = TOPIC,
        partition: Int = PARTITION,
    ) {
        val melding = """
            {
              "eventId": "$offset",
              "kildeReferanse":"$offset",
              "kilde":"$kilde",
              "utfall":"TRUKKET",
              "vedtaksbrevReferanse":null,
              "kabalReferanse":"$offset"
            }
        """.trimIndent()
        this.schedulePollTask {
            if (withRebalance) {
                this.rebalance(mutableListOf(TopicPartition(topic, partition)))
            }
            this.addRecord(ConsumerRecord(topic, partition, offset, offset.toString(), melding))
        }
    }
}
