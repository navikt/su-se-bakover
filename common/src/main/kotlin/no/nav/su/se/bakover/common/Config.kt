package no.nav.su.se.bakover.common

import io.github.cdimascio.dotenv.Dotenv
import io.github.cdimascio.dotenv.dotenv
import no.nav.su.se.bakover.common.Config.env
import no.nav.su.se.bakover.common.Config.getEnvironmentVariableOrDefault
import no.nav.su.se.bakover.common.Config.getEnvironmentVariableOrThrow
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.config.SslConfigs
import org.apache.kafka.common.security.auth.SecurityProtocol
import org.apache.kafka.common.serialization.StringSerializer

object Config {

    internal val env by lazy { init() }

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

    internal fun getEnvironmentVariableOrDefault(environmentVariableName: String, default: String): String {
        return env[environmentVariableName] ?: default
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
    val isLocalOrRunningTests: Boolean,
    val leaderPodLookupPath: String,
    val pdfgenLocal: Boolean,
    val corsAllowOrigin: String,
    val serviceUser: ServiceUserConfig,
    val azure: AzureConfig,
    val oppdrag: OppdragConfig,
    val database: DatabaseConfig,
    val clientsConfig: ClientsConfig,
    val frontendCallbackUrls: FrontendCallbackUrls,
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
        val vaultMountPath: String,
    ) {
        companion object {
            fun createFromEnvironmentVariables() = DatabaseConfig(
                databaseName = getEnvironmentVariableOrThrow("DATABASE_NAME"),
                jdbcUrl = getEnvironmentVariableOrThrow("DATABASE_JDBC_URL"),
                vaultMountPath = getEnvironmentVariableOrThrow("VAULT_MOUNTPATH"),
            )

            fun createLocalConfig() = DatabaseConfig(
                databaseName = getEnvironmentVariableOrDefault("DATABASE_NAME", "supstonad-db-local"),
                jdbcUrl = getEnvironmentVariableOrDefault(
                    "DATABASE_JDBC_URL",
                    "jdbc:postgresql://localhost:5432/supstonad-db-local"
                ),
                vaultMountPath = "",
            )
        }
    }

    data class ClientsConfig(
        val oppgaveConfig: OppgaveConfig,
        val pdlUrl: String,
        val dokDistUrl: String,
        val pdfgenUrl: String,
        val dokarkivUrl: String,
        val kodeverkUrl: String,
        val stsUrl: String,
        val skjermingUrl: String,
        val dkifUrl: String,
    ) {
        companion object {
            fun createFromEnvironmentVariables() = ClientsConfig(
                oppgaveConfig = OppgaveConfig.createFromEnvironmentVariables(),
                pdlUrl = getEnvironmentVariableOrDefault("PDL_URL", "http://pdl-api.default.svc.nais.local"),
                dokDistUrl = getEnvironmentVariableOrThrow("DOKDIST_URL"),
                pdfgenUrl = getEnvironmentVariableOrDefault("PDFGEN_URL", "http://su-pdfgen.supstonad.svc.nais.local"),
                dokarkivUrl = getEnvironmentVariableOrThrow("DOKARKIV_URL"),
                kodeverkUrl = getEnvironmentVariableOrDefault("KODEVERK_URL", "http://kodeverk.default.svc.nais.local"),
                stsUrl = getEnvironmentVariableOrThrow("STS_URL"),
                skjermingUrl = getEnvironmentVariableOrThrow("SKJERMING_URL"),
                dkifUrl = getEnvironmentVariableOrDefault("DKIF_URL", "http://dkif.default.svc.nais.local"),
            )

            fun createLocalConfig() = ClientsConfig(
                oppgaveConfig = OppgaveConfig.createLocalConfig(),
                pdlUrl = "mocked",
                dokDistUrl = "mocked",
                pdfgenUrl = "mocked",
                dokarkivUrl = "mocked",
                kodeverkUrl = "mocked",
                stsUrl = "mocked",
                skjermingUrl = "mocked",
                dkifUrl = "mocked",
            )
        }

        data class OppgaveConfig(
            val clientId: String,
            val url: String,
        ) {
            companion object {
                fun createFromEnvironmentVariables() = OppgaveConfig(
                    clientId = getEnvironmentVariableOrThrow("OPPGAVE_CLIENT_ID"),
                    url = getEnvironmentVariableOrThrow("OPPGAVE_URL"),
                )

                fun createLocalConfig() = OppgaveConfig(
                    clientId = "mocked",
                    url = "mocked",
                )
            }
        }
    }

    data class FrontendCallbackUrls(
        private val frontendBaseUrl: String,

    ) {
        val suSeFramoverLoginSuccessUrl = "$frontendBaseUrl/auth/complete"
        val suSeFramoverLogoutSuccessUrl = "$frontendBaseUrl/logout/complete"

        companion object {
            fun createFromEnvironmentVariables() = FrontendCallbackUrls(
                frontendBaseUrl = getEnvironmentVariableOrThrow("FRONTEND_BASE_URL")
            )

            fun createLocalConfig() = FrontendCallbackUrls(
                frontendBaseUrl = "http://localhost:1234"
            )
        }
    }

    companion object {

        fun createConfig() = if (isLocalOrRunningTests()) createLocalConfig() else createFromEnvironmentVariables()
        fun createFromEnvironmentVariables() = ApplicationConfig(
            isLocalOrRunningTests = false,
            leaderPodLookupPath = getEnvironmentVariableOrThrow("ELECTOR_PATH"),
            pdfgenLocal = false,
            corsAllowOrigin = getEnvironmentVariableOrThrow("ALLOW_CORS_ORIGIN"),
            serviceUser = ServiceUserConfig.createFromEnvironmentVariables(),
            azure = AzureConfig.createFromEnvironmentVariables(),
            oppdrag = OppdragConfig.createFromEnvironmentVariables(),
            database = DatabaseConfig.createFromEnvironmentVariables(),
            clientsConfig = ClientsConfig.createFromEnvironmentVariables(),
            frontendCallbackUrls = FrontendCallbackUrls.createFromEnvironmentVariables(),
        )

        fun createLocalConfig() = ApplicationConfig(
            isLocalOrRunningTests = true,
            leaderPodLookupPath = "",
            pdfgenLocal = getEnvironmentVariableOrDefault("PDFGEN_LOCAL", "false").toBoolean(),
            corsAllowOrigin = "localhost:1234",
            serviceUser = ServiceUserConfig.createLocalConfig(),
            azure = AzureConfig.createFromEnvironmentVariables(),
            oppdrag = OppdragConfig.createLocalConfig(),
            database = DatabaseConfig.createLocalConfig(),
            clientsConfig = ClientsConfig.createLocalConfig(),
            frontendCallbackUrls = FrontendCallbackUrls.createLocalConfig(),

        )

        fun isLocalOrRunningTests() = env["NAIS_CLUSTER_NAME"] == null
    }
}
