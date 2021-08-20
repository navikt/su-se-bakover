package no.nav.su.se.bakover.web.services.personhendelser

import arrow.core.nonEmptyListOf
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.timeout
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig
import io.confluent.kafka.serializers.KafkaAvroDeserializer
import io.confluent.kafka.serializers.KafkaAvroDeserializerConfig
import io.confluent.kafka.serializers.KafkaAvroSerializer
import io.kotest.matchers.shouldBe
import no.nav.common.JAAS_PLAIN_LOGIN
import no.nav.common.JAAS_REQUIRED
import no.nav.common.KafkaEnvironment
import no.nav.person.pdl.leesah.Endringstype
import no.nav.person.pdl.leesah.doedsfall.Doedsfall
import no.nav.person.pdl.leesah.sivilstand.Sivilstand
import no.nav.person.pdl.leesah.utflytting.UtflyttingFraNorge
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.domain.AktørId
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.hendelse.Personhendelse
import no.nav.su.se.bakover.service.hendelser.PersonhendelseService
import no.nav.su.se.bakover.test.generer
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.config.SaslConfigs
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.LocalDate
import no.nav.person.pdl.leesah.Personhendelse as EksternPersonhendelse

// Hver test har sin egen topic
private const val TOPIC1 = "kafkaTopic1"
private const val TOPIC2 = "kafkaTopic2"

internal class PersonhendelseConsumerTest {

    private val PARTITION = 0
    private val fnr = Fnr.generer()
    private val ident = fnr.toString() + "00"
    private val key = "HEADER$ident"

    @Test
    fun `Mottar alle personhendelsene`() {
        val kafkaConsumer = kafkaConsumer(kafkaServer, "$TOPIC1-consumer-group")
        val personhendelseService = mock<PersonhendelseService>()
        PersonhendelseConsumer(
            consumer = kafkaConsumer,
            personhendelseService = personhendelseService,
            periode = 1,
            initialDelay = 0,
            topicName = TOPIC1,
            pollTimeoutDuration = Duration.ofMillis(1000),
        )
        val producer = kafkaProducer(kafkaServer)
        for (index in 0..5L) {
            producer.send(generatePdlMelding(TOPIC1, index))
        }
        val hendelser = argumentCaptor<Personhendelse.Ny>()
        verify(personhendelseService, timeout(10000).times(6)).prosesserNyHendelse(hendelser.capture())
        hendelser.allValues shouldBe (0..5L).map {
            Personhendelse.Ny(
                gjeldendeAktørId = AktørId(ident),
                endringstype = Personhendelse.Endringstype.OPPRETTET,
                hendelse = Personhendelse.Hendelse.Dødsfall(LocalDate.now()),
                personidenter = nonEmptyListOf(ident, fnr.toString()),
                metadata = Personhendelse.Metadata(
                    hendelseId = it.toString(),
                    tidligereHendelseId = null,
                    offset = it,
                    partisjon = PARTITION,
                    master = "FREG",
                    key = key,
                ),
            )
        }
        verifyNoMoreInteractions(personhendelseService)
    }

    @Test
    fun `commit timeout vil ikke polle neste melding `() {
        val kafkaConsumer = kafkaConsumer(kafkaServer, "$TOPIC2-consumer-group")
        val personhendelseService = mock<PersonhendelseService>()
        PersonhendelseConsumer(
            consumer = kafkaConsumer,
            personhendelseService = personhendelseService,
            periode = 1,
            initialDelay = 0,
            topicName = TOPIC2,
            pollTimeoutDuration = Duration.ofMillis(1000),
        )
        val producer = kafkaProducer(kafkaServer)
        // Tvinger en nullpointer exception
        producer.send(ProducerRecord(TOPIC2, PARTITION, key, null))
        producer.send(generatePdlMelding(TOPIC2, 0))
        verify(personhendelseService, timeout(5000).times(0)).prosesserNyHendelse(any())
        verifyNoMoreInteractions(personhendelseService)
    }

    private fun generatePdlMelding(
        topic: String,
        offset: Long,
        personIdenter: List<String> = listOf(ident, fnr.toString()),
    ): ProducerRecord<String, EksternPersonhendelse> {
        val personhendelse = EksternPersonhendelse(
            offset.toString(), // hendelseId (UUID)
            personIdenter, // personIdenter (liste med mix av fnr(11 siffer), ident(13 siffer), ++?)
            "FREG", // master (f.eks. FREG)
            Tidspunkt.now().instant, // opprettet(f.eks. 2021-08-02T09:03:34.900Z)
            HendelseMapper.Opplysningstype.DØDSFALL.value, // opplysningstype (DOEDSFALL_V1,UTFLYTTING_FRA_NORGE,SIVILSTAND_V1)
            Endringstype.OPPRETTET, // endringstype (OPPRETTET,KORRIGERT,ANNULLERT,OPPHOERT)
            null, // tidligereHendelseId (Peker til tidligere hendelse ved korrigering og annullering.)
            Doedsfall(LocalDate.now()), // doedsfall (https://navikt.github.io/pdl/#_d%C3%B8dsfall)
            Sivilstand(
                "GIFT",
                LocalDate.now(),
                "12345678910",
                null,
            ), // sivilstand (https://navikt.github.io/pdl/#_sivilstand)
            UtflyttingFraNorge(
                "ESP",
                "Barcelona",
                LocalDate.now(),
            ), // utflyttingFraNorge (https://navikt.github.io/pdl/#_utflytting)
        )
        // Emulerer at PDL-kafka legger på 6 ukjente karakterer før de appender key
        return ProducerRecord(topic, PARTITION, offset, key, personhendelse)
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
            topicInfos = listOf(KafkaEnvironment.TopicInfo(TOPIC1, 1), KafkaEnvironment.TopicInfo(TOPIC2, 1)),
            withSchemaRegistry = true,
            withSecurity = true,
            autoStart = true,
        )

        private const val user = "srvkafkaclient"
        private const val pwd = "kafkaclient"

        private fun kafkaConsumer(kafkaServer: KafkaEnvironment, groupId: String) =
            KafkaConsumer<String, EksternPersonhendelse>(
                // Borrowed from: https://github.com/navikt/kafka-embedded-env/blob/master/src/test/kotlin/no/nav/common/test/common/Utilities.kt
                mapOf(
                    ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to kafkaServer.brokersURL,
                    KafkaAvroDeserializerConfig.SPECIFIC_AVRO_READER_CONFIG to true,
                    ConsumerConfig.CLIENT_ID_CONFIG to groupId,
                    ConsumerConfig.GROUP_ID_CONFIG to "funKafkaConsumeGrpID",
                    ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to KafkaAvroDeserializer::class.java,
                    ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to KafkaAvroDeserializer::class.java,
                    AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG to kafkaServer.schemaRegistry?.url,
                    ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG to "false",
                    ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "earliest",
                    ConsumerConfig.MAX_POLL_RECORDS_CONFIG to 2,
                    CommonClientConfigs.SECURITY_PROTOCOL_CONFIG to "SASL_PLAINTEXT",
                    SaslConfigs.SASL_MECHANISM to "PLAIN",
                    SaslConfigs.SASL_JAAS_CONFIG to "$JAAS_PLAIN_LOGIN $JAAS_REQUIRED username=\"$user\" password=\"$pwd\";",
                ),
            )

        private fun kafkaProducer(kafkaServer: KafkaEnvironment) = KafkaProducer<String, EksternPersonhendelse>(
            // Borrowed from: https://github.com/navikt/kafka-embedded-env/blob/master/src/test/kotlin/no/nav/common/test/common/Utilities.kt
            mapOf(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to kafkaServer.brokersURL,
                ProducerConfig.CLIENT_ID_CONFIG to "funKafkaProduce",
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to KafkaAvroSerializer::class.java,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to KafkaAvroSerializer::class.java,
                AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG to kafkaServer.schemaRegistry?.url,
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
