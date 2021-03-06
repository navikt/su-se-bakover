package no.nav.su.se.bakover.common

import io.github.cdimascio.dotenv.dotenv
import no.nav.su.se.bakover.common.EnvironmentConfig.getEnvironmentVariableOrDefault
import no.nav.su.se.bakover.common.EnvironmentConfig.getEnvironmentVariableOrThrow
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.config.SslConfigs
import org.apache.kafka.common.security.auth.SecurityProtocol
import org.apache.kafka.common.serialization.StringSerializer
import org.slf4j.LoggerFactory

private object EnvironmentConfig {
    private val env by lazy {
        dotenv {
            ignoreIfMissing = true
            systemProperties = true
        }
    }

    fun getEnvironmentVariableOrThrow(environmentVariableName: String): String {
        return env[environmentVariableName] ?: throwMissingEnvironmentVariable(environmentVariableName)
    }

    fun getEnvironmentVariableOrDefault(environmentVariableName: String, default: String): String {
        return env[environmentVariableName] ?: default
    }

    fun exists(environmentVariableName: String): Boolean {
        return env[environmentVariableName] != null
    }

    private fun throwMissingEnvironmentVariable(environmentVariableName: String): Nothing {
        throw IllegalStateException("Mangler environment variabelen '$environmentVariableName'. Dersom du prøver kjøre lokalt må den legges til i '.env'-fila. Se eksempler i '.env.template'.")
    }
}

data class ApplicationConfig(
    val runtimeEnvironment: RuntimeEnvironment,
    val naisCluster: NaisCluster?,
    val leaderPodLookupPath: String,
    val pdfgenLocal: Boolean,
    val serviceUser: ServiceUserConfig,
    val azure: AzureConfig,
    val frikort: FrikortConfig,
    val oppdrag: OppdragConfig,
    val database: DatabaseConfig,
    val clientsConfig: ClientsConfig,
    val kafkaConfig: KafkaConfig,
    val unleash: UnleashConfig,
) {
    enum class RuntimeEnvironment {
        Test,
        Local,
        Nais
    }

    enum class NaisCluster {
        Dev,
        Prod
    }

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

    data class FrikortConfig(
        val serviceUsername: String,
        val useStubForSts: Boolean,
    ) {
        companion object {
            fun createFromEnvironmentVariables() = FrikortConfig(
                serviceUsername = getEnvironmentVariableOrThrow("FRIKORT_SERVICE_USERNAME"),
                useStubForSts = false,
            )

            fun createLocalConfig() = FrikortConfig(
                serviceUsername = getEnvironmentVariableOrDefault("FRIKORT_SERVICE_USERNAME", "frikort"),
                useStubForSts = getEnvironmentVariableOrDefault("USE_STUB_FOR_STS", "true") == "true",
            )
        }
    }

    data class AzureConfig(
        val clientSecret: String,
        val wellKnownUrl: String,
        val clientId: String,
        val groups: AzureGroups,
    ) {
        data class AzureGroups(
            val attestant: String,
            val saksbehandler: String,
            val veileder: String,
            val drift: String,
        ) {
            fun asList() = listOf(attestant, saksbehandler, veileder, drift)
        }

        companion object {
            fun createFromEnvironmentVariables() = AzureConfig(
                clientSecret = getEnvironmentVariableOrThrow("AZURE_APP_CLIENT_SECRET"),
                wellKnownUrl = getEnvironmentVariableOrThrow("AZURE_APP_WELL_KNOWN_URL"),
                clientId = getEnvironmentVariableOrThrow("AZURE_APP_CLIENT_ID"),
                groups = AzureGroups(
                    attestant = getEnvironmentVariableOrThrow("AZURE_GROUP_ATTESTANT"),
                    saksbehandler = getEnvironmentVariableOrThrow("AZURE_GROUP_SAKSBEHANDLER"),
                    veileder = getEnvironmentVariableOrThrow("AZURE_GROUP_VEILEDER"),
                    drift = getEnvironmentVariableOrThrow("AZURE_GROUP_DRIFT"),
                ),
            )

            fun createLocalConfig() = AzureConfig(
                clientSecret = getEnvironmentVariableOrDefault("AZURE_APP_CLIENT_SECRET", "Denne brukes bare dersom man bruker en reell PDL/Oppgave-integrasjon o.l."),
                wellKnownUrl = getEnvironmentVariableOrDefault(
                    "AZURE_APP_WELL_KNOWN_URL",
                    "http://localhost:4321/default/.well-known/openid-configuration",
                ),
                clientId = getEnvironmentVariableOrDefault(
                    "AZURE_APP_CLIENT_ID",
                    "su-se-bakover",
                ),
                groups = AzureGroups(
                    attestant = getEnvironmentVariableOrDefault(
                        "AZURE_GROUP_ATTESTANT",
                        "d75164fa-39e6-4149-956e-8404bc9080b6",
                    ),
                    saksbehandler = getEnvironmentVariableOrDefault(
                        "AZURE_GROUP_SAKSBEHANDLER",
                        "0ba009c4-d148-4a51-b501-4b1cf906889d",
                    ),
                    veileder = getEnvironmentVariableOrDefault(
                        "AZURE_GROUP_VEILEDER",
                        "062d4814-8538-4f3a-bcb9-32821af7909a",
                    ),
                    drift = getEnvironmentVariableOrDefault(
                        "AZURE_GROUP_DRIFT",
                        "5ccd88bd-58d6-41a7-9652-5e0597b00f9b",
                    ),
                ),
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
                    mqReplyTo = "unused",
                ),
                avstemming = AvstemmingConfig(mqSendQueue = "unused"),
                simulering = SimuleringConfig(
                    url = "unused",
                    stsSoapUrl = "unused",
                ),
            )
        }
    }

    data class UnleashConfig(
        val unleashUrl: String,
        val appName: String,
    ) {
        companion object {
            fun createFromEnvironmentVariables() = UnleashConfig(
                getEnvironmentVariableOrDefault("UNLEASH_URL", "https://unleash.nais.io/api"),
                getEnvironmentVariableOrDefault("NAIS_APP_NAME", "su-se-bakover"),
            )
        }
    }

    sealed class DatabaseConfig {
        abstract val jdbcUrl: String

        data class RotatingCredentials(
            val databaseName: String,
            override val jdbcUrl: String,
            val vaultMountPath: String,
        ) : DatabaseConfig()

        data class StaticCredentials(
            override val jdbcUrl: String,
        ) : DatabaseConfig() {
            val username = "user"
            val password = "pwd"
        }

        companion object {
            fun createFromEnvironmentVariables() = RotatingCredentials(
                databaseName = getEnvironmentVariableOrThrow("DATABASE_NAME"),
                jdbcUrl = getEnvironmentVariableOrThrow("DATABASE_JDBC_URL"),
                vaultMountPath = getEnvironmentVariableOrThrow("VAULT_MOUNTPATH"),
            )

            fun createLocalConfig() = StaticCredentials(
                jdbcUrl = getEnvironmentVariableOrDefault(
                    "DATABASE_JDBC_URL",
                    "jdbc:postgresql://localhost:5432/supstonad-db-local",
                ),
            )
        }
    }

    data class ClientsConfig(
        val oppgaveConfig: OppgaveConfig,
        val pdlConfig: PdlConfig,
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
                pdlConfig = PdlConfig.createFromEnvironmentVariables(),
                dokDistUrl = getEnvironmentVariableOrThrow("DOKDIST_URL"),
                pdfgenUrl = getEnvironmentVariableOrDefault("PDFGEN_URL", "http://su-pdfgen.supstonad.svc.nais.local"),
                dokarkivUrl = getEnvironmentVariableOrThrow("DOKARKIV_URL"),
                kodeverkUrl = getEnvironmentVariableOrDefault("KODEVERK_URL", "http://kodeverk.default.svc.nais.local"),
                stsUrl = getEnvironmentVariableOrDefault(
                    "STS_URL",
                    "http://security-token-service.default.svc.nais.local",
                ),
                skjermingUrl = getEnvironmentVariableOrThrow("SKJERMING_URL"),
                dkifUrl = getEnvironmentVariableOrDefault("DKIF_URL", "http://dkif.default.svc.nais.local"),
            )

            fun createLocalConfig() = ClientsConfig(
                oppgaveConfig = OppgaveConfig.createLocalConfig(),
                pdlConfig = PdlConfig.createLocalConfig(),
                dokDistUrl = "mocked",
                pdfgenUrl = "mocked",
                dokarkivUrl = "mocked",
                kodeverkUrl = "mocked",
                stsUrl = getEnvironmentVariableOrDefault(
                    "STS_URL",
                    "mocked",
                ),
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

        data class PdlConfig(
            val url: String,
            val clientId: String,
        ) {
            companion object {
                fun createFromEnvironmentVariables() = PdlConfig(
                    url = getEnvironmentVariableOrDefault("PDL_URL", "http://pdl-api.default.svc.nais.local"),
                    clientId = getEnvironmentVariableOrThrow("PDL_CLIENT_ID"),
                )

                fun createLocalConfig() = PdlConfig(
                    url = "mocked",
                    clientId = "mocked",
                )
            }
        }
    }

    data class KafkaConfig(
        private val common: Map<String, String>,
        val producerCfg: ProducerCfg,
    ) {
        companion object {
            fun createFromEnvironmentVariables() = KafkaConfig(
                common = Common().configure(),
                producerCfg = ProducerCfg(
                    kafkaConfig = Common().configure() + mapOf(
                        ProducerConfig.ACKS_CONFIG to "all",
                        ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
                        ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
                    ),
                ),
            )

            fun createLocalConfig() = KafkaConfig(
                common = emptyMap(),
                producerCfg = ProducerCfg(emptyMap()),
            )
        }

        data class ProducerCfg(
            val kafkaConfig: Map<String, Any>,
            val retryTaskInterval: Long = 15_000L,
        )

        private data class Common(
            val brokers: String = getEnvironmentVariableOrDefault("KAFKA_BROKERS", "brokers"),
            val sslConfig: Map<String, String> = SslConfig().configure(),
        ) {
            fun configure(): Map<String, String> =
                mapOf(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG to brokers) + sslConfig
        }

        private data class SslConfig(
            val truststorePath: String = getEnvironmentVariableOrDefault("KAFKA_TRUSTSTORE_PATH", "truststorePath"),
            val keystorePath: String = getEnvironmentVariableOrDefault("KAFKA_KEYSTORE_PATH", "keystorePath"),
            val credstorePwd: String = getEnvironmentVariableOrDefault("KAFKA_CREDSTORE_PASSWORD", "credstorePwd"),
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
                SslConfigs.SSL_KEY_PASSWORD_CONFIG to credstorePwd,
            )
        }
    }

    companion object {

        private val log by lazy {
            // We have to delay logback initialization until after we can determine if we are running locally or not.
            // Ref logback-local.xml
            LoggerFactory.getLogger(this::class.java)
        }

        fun createConfig() = if (isRunningLocally()) createLocalConfig() else createFromEnvironmentVariables()

        fun createFromEnvironmentVariables() = ApplicationConfig(
            runtimeEnvironment = RuntimeEnvironment.Nais,
            naisCluster = naisCluster(),
            leaderPodLookupPath = getEnvironmentVariableOrThrow("ELECTOR_PATH"),
            pdfgenLocal = false,
            serviceUser = ServiceUserConfig.createFromEnvironmentVariables(),
            azure = AzureConfig.createFromEnvironmentVariables(),
            frikort = FrikortConfig.createFromEnvironmentVariables(),
            oppdrag = OppdragConfig.createFromEnvironmentVariables(),
            database = DatabaseConfig.createFromEnvironmentVariables(),
            clientsConfig = ClientsConfig.createFromEnvironmentVariables(),
            kafkaConfig = KafkaConfig.createFromEnvironmentVariables(),
            unleash = UnleashConfig.createFromEnvironmentVariables(),
        )

        fun createLocalConfig() = ApplicationConfig(
            runtimeEnvironment = RuntimeEnvironment.Local,
            naisCluster = naisCluster(),
            leaderPodLookupPath = "",
            pdfgenLocal = getEnvironmentVariableOrDefault("PDFGEN_LOCAL", "false").toBoolean(),
            serviceUser = ServiceUserConfig.createLocalConfig(),
            azure = AzureConfig.createLocalConfig(),
            frikort = FrikortConfig.createLocalConfig(),
            oppdrag = OppdragConfig.createLocalConfig(),
            database = DatabaseConfig.createLocalConfig(),
            clientsConfig = ClientsConfig.createLocalConfig(),
            kafkaConfig = KafkaConfig.createLocalConfig(),
            unleash = UnleashConfig.createFromEnvironmentVariables(),
        ).also {
            log.warn("**********  Using local config (the environment variable 'NAIS_CLUSTER_NAME' is missing.)")
        }

        private fun naisCluster(): NaisCluster? =
            with(getEnvironmentVariableOrDefault("NAIS_CLUSTER_NAME", "")) {
                when {
                    startsWith("prod-") -> NaisCluster.Prod
                    startsWith("dev-") -> NaisCluster.Dev
                    else -> null
                }
            }

        fun isRunningLocally() = naisCluster() == null
        fun isNotProd() = isRunningLocally() || naisCluster() == NaisCluster.Dev
    }
}
