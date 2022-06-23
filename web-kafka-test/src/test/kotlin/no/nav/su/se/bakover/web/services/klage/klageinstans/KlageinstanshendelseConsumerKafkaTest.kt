package no.nav.su.se.bakover.web.services.klage.klageinstans

import io.kotest.matchers.shouldBe
import no.nav.common.JAAS_PLAIN_LOGIN
import no.nav.common.JAAS_REQUIRED
import no.nav.common.KafkaEnvironment
import no.nav.su.se.bakover.domain.klage.UprosessertKlageinstanshendelse
import no.nav.su.se.bakover.service.klage.KlageinstanshendelseService
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedTidspunkt
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.config.SaslConfigs
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Isolated
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.timeout
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.slf4j.helpers.NOPLogger
import java.time.Duration
import java.util.UUID

// Hver test har sin egen topic
private const val TOPIC1 = "kafkaTopic1"
private const val TOPIC2 = "kafkaTopic2"

@Isolated
internal class KlageinstanshendelseConsumerKafkaTest {

    private val PARTITION = 0
    private val key = UUID.randomUUID().toString()

    @Test
    fun `Lagrer aktuelle og forkaster uaktuelle`() {
        val kafkaConsumer = kafkaConsumer(kafkaServer, "$TOPIC1-consumer-group")
        val klageinstanshendelseService = mock<KlageinstanshendelseService>()
        KlageinstanshendelseConsumer(
            consumer = kafkaConsumer,
            klageinstanshendelseService = klageinstanshendelseService,
            topicName = TOPIC1,
            pollTimeoutDuration = Duration.ofMillis(1000),
            clock = fixedClock,
            log = NOPLogger.NOP_LOGGER, // Don't spam logs running tests
            sikkerLogg = NOPLogger.NOP_LOGGER, // Don't spam logs running tests
        )
        val producer = kafkaProducer(kafkaServer)
        listOf(
            producer.send(genererKlageinstanshendelseMelding(TOPIC1, 0, "SUPSTONAD")),
            producer.send(genererKlageinstanshendelseMelding(TOPIC1, 1, "ANNENSTONAD")),
            producer.send(genererKlageinstanshendelseMelding(TOPIC1, 2, "SUPSTONAD")),
        ).forEach {
            // Venter til alle meldingene er sendt før vi prøver consume
            it.get()
        }

        val hendelser = argumentCaptor<UprosessertKlageinstanshendelse>()
        // Kunne alternativt brukt awaitility for å vente til currentOffset ble 3
        verify(klageinstanshendelseService, timeout(20000).times(2)).lagre(any())
        currentOffset(TOPIC1) shouldBe 3 // last offset (2) + 1
        listOf(
            producer.send(genererKlageinstanshendelseMelding(TOPIC1, 3, "ANNENSTONAD")),
            producer.send(genererKlageinstanshendelseMelding(TOPIC1, 4, "SUPSTONAD")),
            producer.send(genererKlageinstanshendelseMelding(TOPIC1, 5, "ANNENSTONAD")),
        ).forEach {
            // Venter til alle meldingene er sendt før vi prøver consume
            it.get()
        }

        verify(klageinstanshendelseService, timeout(20000).times(3)).lagre(hendelser.capture())
        hendelser.allValues.size shouldBe 3
        hendelser.allValues.forEachIndexed { index, uprosessertKlageinstanshendelse ->
            UprosessertKlageinstanshendelse(
                id = uprosessertKlageinstanshendelse.id,
                opprettet = fixedTidspunkt,
                metadata = UprosessertKlageinstanshendelse.Metadata(
                    topic = TOPIC1,
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
        currentOffset(TOPIC1) shouldBe 6 // last offset (5) + 1
    }

    @Test
    fun `Fant ikke kilde comitter siste leste melding og kaller en rebalance`() {
        val kafkaConsumer = kafkaConsumer(kafkaServer, "$TOPIC2-consumer-group")
        val klageinstanshendelseService = mock<KlageinstanshendelseService>()
        KlageinstanshendelseConsumer(
            consumer = kafkaConsumer,
            klageinstanshendelseService = klageinstanshendelseService,
            topicName = TOPIC2,
            pollTimeoutDuration = Duration.ofMillis(1000),
            clock = fixedClock,
            log = NOPLogger.NOP_LOGGER, // Don't spam logs running tests
            sikkerLogg = NOPLogger.NOP_LOGGER, // Don't spam logs running tests
        )
        val producer = kafkaProducer(kafkaServer)

        listOf(
            producer.send(genererKlageinstanshendelseMelding(TOPIC2, 0)),
            // Tvinger en FantIkkeKilde.left()
            producer.send(ProducerRecord(TOPIC2, PARTITION, 1L, key, "{}")),
            producer.send(genererKlageinstanshendelseMelding(TOPIC2, 2)),
        ).forEach { it.get() }
        // Venter først akkurat til vi har fått et kall til klageinstanshendelseService (som forventet)
        verify(klageinstanshendelseService, timeout(20000)).lagre(any())
        Thread.sleep(2000) // Venter deretter en liten stund til for å verifisere at det ikke kommer fler kall.
        verifyNoMoreInteractions(klageinstanshendelseService)
        currentOffset(TOPIC2) shouldBe 1 // last offset (0) + 1
    }

    private fun currentOffset(topic: String): Long {
        return kafkaServer.adminClient!!.listConsumerGroupOffsets("funKafkaConsumeGrpID").partitionsToOffsetAndMetadata().get()[TopicPartition(topic, PARTITION)]!!.offset()
    }

    private fun genererKlageinstanshendelseMelding(
        topic: String,
        offset: Long,
        kilde: String = "SUPSTONAD",
    ): ProducerRecord<String, String> {
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
        return ProducerRecord(topic, PARTITION, offset, offset.toString(), melding)
    }

    companion object {

        private lateinit var kafkaServer: KafkaEnvironment

        @BeforeAll
        @JvmStatic
        fun beforeAll() {
            kafkaServer = kafkaServer()
        }

        @AfterAll
        @JvmStatic
        fun afterAll() {
            kafkaServer.tearDown()
        }

        private fun kafkaServer() = KafkaEnvironment(
            topicInfos = listOf(
                KafkaEnvironment.TopicInfo(TOPIC1, 1),
                KafkaEnvironment.TopicInfo(TOPIC2, 1),
            ),
            withSchemaRegistry = true,
            withSecurity = true,
            autoStart = true,
        )

        private const val user = "srvkafkaclient"
        private const val pwd = "kafkaclient"

        private fun kafkaConsumer(kafkaServer: KafkaEnvironment, groupId: String) = KafkaConsumer<String, String>(
            // Borrowed from: https://github.com/navikt/kafka-embedded-env/blob/master/src/test/kotlin/no/nav/common/test/common/Utilities.kt
            mapOf(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to kafkaServer.brokersURL,
                ConsumerConfig.CLIENT_ID_CONFIG to groupId,
                ConsumerConfig.GROUP_ID_CONFIG to "funKafkaConsumeGrpID",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG to "false",
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "earliest",
                ConsumerConfig.MAX_POLL_RECORDS_CONFIG to 2,
                CommonClientConfigs.SECURITY_PROTOCOL_CONFIG to "SASL_PLAINTEXT",
                SaslConfigs.SASL_MECHANISM to "PLAIN",
                SaslConfigs.SASL_JAAS_CONFIG to "$JAAS_PLAIN_LOGIN $JAAS_REQUIRED username=\"$user\" password=\"$pwd\";",
            ),
        )

        private fun kafkaProducer(kafkaServer: KafkaEnvironment) = KafkaProducer<String, String>(
            // Borrowed from: https://github.com/navikt/kafka-embedded-env/blob/master/src/test/kotlin/no/nav/common/test/common/Utilities.kt
            mapOf(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to kafkaServer.brokersURL,
                ProducerConfig.CLIENT_ID_CONFIG to "funKafkaProduce",
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
                ProducerConfig.ACKS_CONFIG to "all",
                ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION to 1,
                ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG to 500,
                CommonClientConfigs.SECURITY_PROTOCOL_CONFIG to "SASL_PLAINTEXT",
                SaslConfigs.SASL_MECHANISM to "PLAIN",
                SaslConfigs.SASL_JAAS_CONFIG to "$JAAS_PLAIN_LOGIN $JAAS_REQUIRED username=\"${user}\" password=\"$pwd\";",
            ),
        )
    }
}
