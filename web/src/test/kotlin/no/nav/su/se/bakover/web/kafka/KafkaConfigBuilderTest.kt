package no.nav.su.se.bakover.web.kafka

import io.ktor.config.MapApplicationConfig
import io.ktor.util.KtorExperimentalAPI
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.common.config.SaslConfigs
import org.apache.kafka.common.config.SslConfigs
import org.junit.jupiter.api.Test
import kotlin.test.assertNull
import kotlin.test.assertTrue

@KtorExperimentalAPI
internal class KafkaConfigBuilderTest {
    @Test
    fun `should configure producer without SSL`() {
        val env = MapApplicationConfig(
                "kafka.username" to "kafkaUser",
                "kafka.password" to "kafkaPassword",
                "kafka.bootstrap" to "bootstrappers",
                "kafka.trustStorePath" to "",
                "kafka.trustStorePassword" to ""
        )
        val config = KafkaConfigBuilder(env).producerConfig()
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
        val env = MapApplicationConfig(
                "kafka.username" to "kafkaUser",
                "kafka.password" to "kafkaPassword",
                "kafka.bootstrap" to "bootstrappers",
                "kafka.trustStorePath" to "truststorePath",
                "kafka.trustStorePassword" to "truststorePassword"
        )
        val config = KafkaConfigBuilder(env).producerConfig()
        assertTrue(config.getProperty(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG) == "bootstrappers")
        assertTrue(config.getProperty(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG) == "SASL_SSL")
        assertTrue(config.getProperty(SaslConfigs.SASL_JAAS_CONFIG).contains("kafkaUser"))
        assertTrue(config.getProperty(SaslConfigs.SASL_JAAS_CONFIG).contains("kafkaPassword"))
        assertTrue(config.getProperty(SaslConfigs.SASL_MECHANISM) == ("PLAIN"))
        assertTrue(config.getProperty(SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG).contains("truststorePath"))
        assertTrue(config.getProperty(SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG) == "truststorePassword")
    }
}