package no.nav.su.se.bakover.client

import java.lang.RuntimeException
import java.util.concurrent.Future
import no.nav.su.se.bakover.client.stubs.KafkaProducerStub
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.producer.Callback
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.apache.kafka.common.config.SaslConfigs
import org.apache.kafka.common.config.SslConfigs
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow

internal class SuKafkaClientKtTest {

    @Test
    fun `test`() {
        assertDoesNotThrow { SuKafkaClientImpl(KafkaProducerStub()).send(KafkaMessage("topic", "key", "value")) }
        assertDoesNotThrow { SuKafkaClientImpl(object : KafkaProducerStub() {
            override fun send(record: ProducerRecord<String, String>?, callback: Callback?): Future<RecordMetadata> {
                throw RuntimeException("Error")
            }
        }).send(KafkaMessage("topic", "key", "value"))
        }
    }

    @Test
    fun `should configure producer without SSL`() {
        val config = KafkaConfigBuilder(mapOf(
                "username" to "kafkaUser",
                "password" to "kafkaPassword",
                "KAFKA_BOOTSTRAP_SERVERS" to "bootstrappers",
                "NAV_TRUSTSTORE_PATH" to "",
                "NAV_TRUSTSTORE_PASSWORD" to ""
        )).producerConfig()
        assertTrue(config.getProperty(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG) == "bootstrappers")
        assertTrue(config.getProperty(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG) == "PLAINTEXT")
        assertTrue(config.getProperty(SaslConfigs.SASL_JAAS_CONFIG).contains("kafkaUser"))
        assertTrue(config.getProperty(SaslConfigs.SASL_JAAS_CONFIG).contains("kafkaPassword"))
        assertTrue(config.getProperty(SaslConfigs.SASL_MECHANISM) == ("PLAIN"))
        assertNull(config.getProperty(SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG))
        assertNull(config.getProperty(SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG))
    }

    @Test
    fun `should configure producer with SSL`() {
        val config = KafkaConfigBuilder(mapOf(
                "username" to "kafkaUser",
                "password" to "kafkaPassword",
                "KAFKA_BOOTSTRAP_SERVERS" to "bootstrappers",
                "NAV_TRUSTSTORE_PATH" to "truststorePath",
                "NAV_TRUSTSTORE_PASSWORD" to "truststorePassword"
        )).producerConfig()

        assertTrue(config.getProperty(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG) == "bootstrappers")
        assertTrue(config.getProperty(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG) == "SASL_SSL")
        assertTrue(config.getProperty(SaslConfigs.SASL_JAAS_CONFIG).contains("kafkaUser"))
        assertTrue(config.getProperty(SaslConfigs.SASL_JAAS_CONFIG).contains("kafkaPassword"))
        assertTrue(config.getProperty(SaslConfigs.SASL_MECHANISM) == ("PLAIN"))
        assertTrue(config.getProperty(SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG).contains("truststorePath"))
        assertTrue(config.getProperty(SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG) == "truststorePassword")
    }
}
