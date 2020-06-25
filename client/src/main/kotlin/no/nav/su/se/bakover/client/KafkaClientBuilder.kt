package no.nav.su.se.bakover.client

import no.nav.su.se.bakover.client.stubs.KafkaProducerStub
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.config.SaslConfigs
import org.apache.kafka.common.config.SslConfigs
import org.apache.kafka.common.serialization.StringSerializer
import org.slf4j.LoggerFactory
import java.io.File
import java.util.*

interface KafkaClientsBuilder {
    fun buildProducer(): Producer<String, String>
}

object KafkaClientBuilder : KafkaClientsBuilder {
    private val env = System.getenv()

    override fun buildProducer(): Producer<String, String> = when (env.isLocalOrRunningTests()) {
        true -> KafkaProducerStub
        else -> KafkaProducer(KafkaConfigBuilder(env).producerConfig(), StringSerializer(), StringSerializer())
    }
}

internal class KafkaConfigBuilder(private val env: Map<String, String>) {
    private val LOG = LoggerFactory.getLogger(KafkaConfigBuilder::class.java)

    fun producerConfig() = kafkaBaseConfig().apply {
        put(ProducerConfig.ACKS_CONFIG, "all")
        put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, "1")
    }

    private fun kafkaBaseConfig() = Properties().apply {
        put(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, env.getOrDefault("KAFKA_BOOTSTRAP_SERVERS", "localhost:8080"))
        put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "PLAINTEXT")
        val username = env.getOrDefault("username", "kafkaUser")
        val password = env.getOrDefault("password", "kafkaPassword")
        put(SaslConfigs.SASL_JAAS_CONFIG, "org.apache.kafka.common.security.plain.PlainLoginModule required username=\"$username\" password=\"$password\";")
        put(SaslConfigs.SASL_MECHANISM, "PLAIN")

        val truststorePath = env.getOrDefault("NAV_TRUSTSTORE_PATH", "")
        val truststorePassword = env.getOrDefault("NAV_TRUSTSTORE_PASSWORD", "")
        if (truststorePath != "" && truststorePassword != "")
            try {
                put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "SASL_SSL")
                put(SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG, File(truststorePath).absolutePath)
                put(SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG, truststorePassword)
                LOG.info("Configured '${SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG}' location ")
            } catch (ex: Exception) {
                LOG.error("Failed to set '${SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG}' location", ex)
            }
    }
}
