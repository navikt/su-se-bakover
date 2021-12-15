package no.nav.su.se.bakover.web.services.klage

import io.kotest.matchers.shouldBe
import no.nav.common.JAAS_PLAIN_LOGIN
import no.nav.common.JAAS_REQUIRED
import no.nav.common.KafkaEnvironment
import no.nav.su.se.bakover.domain.klage.UprosessertFattetKlagevedtak
import no.nav.su.se.bakover.service.klage.KlagevedtakService
import no.nav.su.se.bakover.service.toggles.ToggleService
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedTidspunkt
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
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
import java.time.Duration
import java.util.UUID

// Hver test har sin egen topic
private const val TOPIC1 = "kafkaTopic1"
private const val TOPIC2 = "kafkaTopic2"

@Isolated
internal class FattetKlagevedtakConsumerTest {

    private val PARTITION = 0
    private val key = UUID.randomUUID().toString()

    @Test
    fun `Mottar alle fatta klagevedtak`() {
        val kafkaConsumer = kafkaConsumer(kafkaServer, "$TOPIC1-consumer-group")
        val klagevedtakService = mock<KlagevedtakService>()
        FattetKlagevedtakConsumer(
            consumer = kafkaConsumer,
            klagevedtakService = klagevedtakService,
            periode = 1,
            initialDelay = 0,
            topicName = TOPIC1,
            pollTimeoutDuration = Duration.ofMillis(1000),
            clock = fixedClock,
            toggleService = object : ToggleService {
                override fun isEnabled(toggleName: String) = true
            },
        )
        val producer = kafkaProducer(kafkaServer)
        (0..5L).map {
            producer.send(genererFattetKlagevedtaksmelding(TOPIC1, it))
        }.forEach {
            // Venter til alle meldingene er sendt før vi prøver consume
            it.get()
        }
        val hendelser = argumentCaptor<UprosessertFattetKlagevedtak>()
        verify(klagevedtakService, timeout(20000).times(6)).lagre(hendelser.capture())
        hendelser.allValues.size shouldBe 6
        hendelser.allValues.forEachIndexed { index, klagevedtak ->
            UprosessertFattetKlagevedtak(
                id = klagevedtak.id,
                opprettet = fixedTidspunkt,
                metadata = UprosessertFattetKlagevedtak.Metadata(
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
        verifyNoMoreInteractions(klagevedtakService)
    }

    @Test
    fun `Fant ikke kilde comitter siste leste melding og kaller en rebalance`() {
        val kafkaConsumer = kafkaConsumer(kafkaServer, "$TOPIC2-consumer-group")
        val klagevedtakService = mock<KlagevedtakService>()
        FattetKlagevedtakConsumer(
            consumer = kafkaConsumer,
            klagevedtakService = klagevedtakService,
            periode = 1,
            initialDelay = 0,
            topicName = TOPIC2,
            pollTimeoutDuration = Duration.ofMillis(1000),
            clock = fixedClock,
            toggleService = object : ToggleService {
                override fun isEnabled(toggleName: String) = true
            },
        )
        val producer = kafkaProducer(kafkaServer)

        listOf(
            producer.send(genererFattetKlagevedtaksmelding(TOPIC2, 0)),
            // Tvinger en FantIkkeKilde.left()
            producer.send(ProducerRecord(TOPIC2, PARTITION, 1L, key, "{}")),
            producer.send(genererFattetKlagevedtaksmelding(TOPIC2, 2)),
        ).forEach { it.get() }
        // Venter først akkurat til vi har fått et kall til klagevedtakService (som forventet)
        verify(klagevedtakService, timeout(20000).times(1)).lagre(any())
        Thread.sleep(2000) // Venter deretter en liten stund til for å verifisere at det ikke kommer fler kall.
        verifyNoMoreInteractions(klagevedtakService)
    }

    private fun genererFattetKlagevedtaksmelding(
        topic: String,
        offset: Long,
    ): ProducerRecord<String, String> {
        val fattetKlagevedtaksmelding = """
            {
              "eventId": "$offset",
              "kildeReferanse":"$offset",
              "kilde":"SUPSTONAD",
              "utfall":"TRUKKET",
              "vedtaksbrevReferanse":null,
              "kabalReferanse":"$offset"
            }
        """.trimIndent()
        return ProducerRecord(topic, PARTITION, offset, offset.toString(), fattetKlagevedtaksmelding)
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

        private fun kafkaConsumer(kafkaServer: KafkaEnvironment, groupId: String) =
            KafkaConsumer<String, String>(
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
