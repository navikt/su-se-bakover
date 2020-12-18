package no.nav.su.se.bakover.common

import io.github.cdimascio.dotenv.Dotenv
import io.github.cdimascio.dotenv.dotenv
import no.nav.su.se.bakover.common.Config.getEnvironmentVariableOrThrow
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.config.SslConfigs
import org.apache.kafka.common.security.auth.SecurityProtocol
import org.apache.kafka.common.serialization.StringSerializer

object Config {

    private val env by lazy { init() }

    val isLocalOrRunningTests = env["NAIS_CLUSTER_NAME"] == null
    val leaderPodLookupPath = env["ELECTOR_PATH"] ?: ""

    val vaultMountPath = env["VAULT_MOUNTPATH"] ?: ""

    val oppgaveClientId = env["OPPGAVE_CLIENT_ID"] ?: "Denne er forskjellig per miljø. Må ligge i .env lokalt."

    val pdlUrl = env["PDL_URL"] ?: "http://pdl-api.default.svc.nais.local"
    val dokDistUrl = env["DOKDIST_URL"] ?: "http://dokdistfordeling.default.svc.nais.local"
    val pdfgenUrl = env["PDFGEN_URL"] ?: "http://su-pdfgen.supstonad.svc.nais.local"
    val dokarkivUrl = env["DOKARKIV_URL"] ?: "http://dokarkiv.default.svc.nais.local"
    val oppgaveUrl = env["OPPGAVE_URL"] ?: "http://oppgave.oppgavehandtering.svc.nais.local"
    val kodeverkUrl = env["KODEVERK_URL"] ?: "http://kodeverk.default.svc.nais.local"
    val stsUrl = env["STS_URL"] ?: "http://security-token-service.default.svc.nais.local"
    val skjermingUrl = env["SKJERMING_URL"] ?: "https://skjermede-personer-pip.nais.adeo.no"
    val dkifUrl = env["DKIF_URL"] ?: "http://dkif.default.svc.nais.local"

    val pdfgenLocal = env["PDFGEN_LOCAL"]?.toBoolean() ?: false

    val corsAllowOrigin = env["ALLOW_CORS_ORIGIN"] ?: "localhost:1234"
    private val frontendBaseUrl = env["FRONTEND_BASE_URL"] ?: "http://localhost:1234"
    val suSeFramoverLoginSuccessUrl = "$frontendBaseUrl/auth/complete"
    val suSeFramoverLogoutSuccessUrl = "$frontendBaseUrl/logout/complete"
    val kafka = Kafka()
    data class Kafka(
        private val common: Map<String, String> = Common().configure(),
        val producerConfig: Map<String, Any> = common + mapOf(
            ProducerConfig.ACKS_CONFIG to "all",
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java
        )
    ) {
        data class Common(
            val brokers: String = env["KAFKA_BROKERS"] ?: "brokers",
            val sslConfig: Map<String, String> = SslConfig().configure()
        ) {
            fun configure(): Map<String, String> =
                mapOf(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG to brokers) + sslConfig
        }

        data class SslConfig(
            val truststorePath: String = env["KAFKA_TRUSTSTORE_PATH"] ?: "truststorePath",
            val keystorePath: String = env["KAFKA_KEYSTORE_PATH"] ?: "keystorePath",
            val credstorePwd: String = env["KAFKA_CREDSTORE_PASSWORD"] ?: "credstorePwd"
        ) {
            fun configure(): Map<String, String> = mapOf(
                CommonClientConfigs.SECURITY_PROTOCOL_CONFIG to SecurityProtocol.SSL.name,
                SslConfigs.SSL_ENDPOINT_IDENTIFICATION_ALGORITHM_CONFIG to "", // Disable server host name verification
                SslConfigs.SSL_TRUSTSTORE_TYPE_CONFIG to "jks",
                SslConfigs.SSL_KEYSTORE_TYPE_CONFIG to "PKCS12",
                SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG to truststorePath,
                SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG to credstorePwd,
                SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG to keystorePath,
                SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG to credstorePwd,
                SslConfigs.SSL_KEY_PASSWORD_CONFIG to credstorePwd
            )
        }

        sealed class StatistikkTopic {
            abstract val name: String

            object Sak : StatistikkTopic() {
                override val name: String = "supstonad.aapen-su-sak-statistikk-v1"
            }

            object Behandling : StatistikkTopic() {
                override val name: String = "supstonad.aapen-su-behandling-statistikk-v1"
            }
        }
    }
    fun init(): Dotenv {
        return dotenv {
            ignoreIfMissing = true
            systemProperties = true
        }
    }

    internal fun getEnvironmentVariableOrThrow(environmentVariableName: String): String {
        return env[environmentVariableName] ?: throwMissingEnvironmentVariable(environmentVariableName)
    }

    private fun throwMissingEnvironmentVariable(environmentVariableName: String): Nothing {
        throw IllegalStateException("Mangler environment variabelen '$environmentVariableName'. Dersom du prøver kjøre lokalt må den legges til i '.env'-fila. Se eksempler i '.env.template'.")
    }
}

/**
 * This class will gradually replace the Config object - to make config possible to test without defaults.
 * Will start by just moving the easy part and the stuff that shouldn't have default config.
 * We could consider to return an ApplicationConfig based on if you're running locally or in preprod/prod.
 */
data class ApplicationConfig(
    val serviceUser: ServiceUserConfig,
    val azure: AzureConfig,
    val oppdrag: OppdragConfig,
    val database: DatabaseConfig,
) {
    data class ServiceUserConfig(
        val username: String,
        val password: String,
    ) {
        override fun toString(): String {
            return "ServiceUser(username='$username', password='****')"
        }

        companion object {
            fun createFromEnvironmentVariables() = ServiceUserConfig(
                username = getEnvironmentVariableOrThrow("username"),
                password = getEnvironmentVariableOrThrow("password"),
            )

            fun createLocalConfig() = ServiceUserConfig(
                username = "unused",
                password = "unused",
            )
        }
    }

    data class AzureConfig(
        val clientSecret: String,
        val wellKnownUrl: String,
        val clientId: String,
        val backendCallbackUrl: String,
        val groups: AzureGroups,
    ) {
        data class AzureGroups(
            val attestant: String,
            val saksbehandler: String,
            val veileder: String,
        ) {
            fun asList() = listOf(attestant, saksbehandler, veileder)
        }

        companion object {
            fun createFromEnvironmentVariables() = AzureConfig(
                clientSecret = getEnvironmentVariableOrThrow("AZURE_APP_CLIENT_SECRET"),
                wellKnownUrl = getEnvironmentVariableOrThrow("AZURE_APP_WELL_KNOWN_URL"),
                clientId = getEnvironmentVariableOrThrow("AZURE_APP_CLIENT_ID"),
                backendCallbackUrl = getEnvironmentVariableOrThrow("BACKEND_CALLBACK_URL"),
                groups = AzureGroups(
                    attestant = getEnvironmentVariableOrThrow("AZURE_GROUP_ATTESTANT"),
                    saksbehandler = getEnvironmentVariableOrThrow("AZURE_GROUP_SAKSBEHANDLER"),
                    veileder = getEnvironmentVariableOrThrow("AZURE_GROUP_VEILEDER"),
                )
            )
        }
    }

    data class OppdragConfig(
        val mqQueueManager: String,
        val mqPort: Int,
        val mqHostname: String,
        val mqChannel: String,
        val utbetaling: UtbetalingConfig,
        val avstemming: AvstemmingConfig,
        val simulering: SimuleringConfig,
    ) {
        data class UtbetalingConfig constructor(
            val mqSendQueue: String,
            val mqReplyTo: String,
        ) {
            companion object {
                fun createFromEnvironmentVariables() = UtbetalingConfig(
                    mqSendQueue = getEnvironmentVariableOrThrow("MQ_SEND_QUEUE_UTBETALING"),
                    mqReplyTo = getEnvironmentVariableOrThrow("MQ_REPLY_TO"),
                )
            }
        }

        data class AvstemmingConfig constructor(
            val mqSendQueue: String,
        ) {
            companion object {
                fun createFromEnvironmentVariables() = AvstemmingConfig(
                    mqSendQueue = getEnvironmentVariableOrThrow("MQ_SEND_QUEUE_AVSTEMMING"),
                )
            }
        }

        data class SimuleringConfig constructor(
            val url: String,
            val stsSoapUrl: String,
        ) {
            companion object {
                fun createFromEnvironmentVariables() = SimuleringConfig(
                    url = getEnvironmentVariableOrThrow("SIMULERING_URL"),
                    stsSoapUrl = getEnvironmentVariableOrThrow("STS_URL_SOAP"),
                )
            }
        }

        companion object {
            fun createFromEnvironmentVariables() = OppdragConfig(
                mqQueueManager = getEnvironmentVariableOrThrow("MQ_QUEUE_MANAGER"),
                mqPort = getEnvironmentVariableOrThrow("MQ_PORT").toInt(),
                mqHostname = getEnvironmentVariableOrThrow("MQ_HOSTNAME"),
                mqChannel = getEnvironmentVariableOrThrow("MQ_CHANNEL"),
                utbetaling = UtbetalingConfig.createFromEnvironmentVariables(),
                avstemming = AvstemmingConfig.createFromEnvironmentVariables(),
                simulering = SimuleringConfig.createFromEnvironmentVariables(),
            )

            fun createLocalConfig() = OppdragConfig(
                mqQueueManager = "unused",
                mqPort = -1,
                mqHostname = "unused",
                mqChannel = "unused",
                utbetaling = UtbetalingConfig(
                    mqSendQueue = "unused",
                    mqReplyTo = "unused"
                ),
                avstemming = AvstemmingConfig(mqSendQueue = "unused"),
                simulering = SimuleringConfig(
                    url = "unused",
                    stsSoapUrl = "unused"
                )
            )
        }
    }

    data class DatabaseConfig(
        val databaseName: String,
        val jdbcUrl: String,
    ) {
        companion object {
            fun createFromEnvironmentVariables() = DatabaseConfig(
                databaseName = getEnvironmentVariableOrThrow("DATABASE_NAME"),
                jdbcUrl = getEnvironmentVariableOrThrow("DATABASE_JDBC_URL")
            )
        }
    }

    companion object {
        fun createFromEnvironmentVariables() = ApplicationConfig(
            serviceUser = ServiceUserConfig.createFromEnvironmentVariables(),
            azure = AzureConfig.createFromEnvironmentVariables(),
            oppdrag = OppdragConfig.createFromEnvironmentVariables(),
            database = DatabaseConfig.createFromEnvironmentVariables(),
        )

        fun createLocalConfig() = ApplicationConfig(
            serviceUser = ServiceUserConfig.createLocalConfig(),
            azure = AzureConfig.createFromEnvironmentVariables(),
            oppdrag = OppdragConfig.createLocalConfig(),
            database = DatabaseConfig.createFromEnvironmentVariables(),
        )
    }
}
