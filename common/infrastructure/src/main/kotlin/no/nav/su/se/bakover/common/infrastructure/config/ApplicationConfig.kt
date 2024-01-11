package no.nav.su.se.bakover.common.infrastructure.config

import io.confluent.kafka.serializers.KafkaAvroDeserializer
import io.confluent.kafka.serializers.KafkaAvroDeserializerConfig
import io.github.cdimascio.dotenv.dotenv
import no.nav.su.se.bakover.common.domain.config.ServiceUserConfig
import no.nav.su.se.bakover.common.domain.config.TilbakekrevingConfig
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.infrastructure.config.EnvironmentConfig.getEnvironmentVariableOrDefault
import no.nav.su.se.bakover.common.infrastructure.config.EnvironmentConfig.getEnvironmentVariableOrNull
import no.nav.su.se.bakover.common.infrastructure.config.EnvironmentConfig.getEnvironmentVariableOrThrow
import no.nav.su.se.bakover.common.infrastructure.git.GitCommit
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.config.SslConfigs
import org.apache.kafka.common.security.auth.SecurityProtocol
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.LocalTime
import java.time.temporal.ChronoUnit

internal data object EnvironmentConfig {
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

    fun getEnvironmentVariableOrNull(environmentVariableName: String): String? {
        return env[environmentVariableName] ?: null
    }

    private fun throwMissingEnvironmentVariable(environmentVariableName: String): Nothing {
        throw IllegalStateException("Mangler environment variabelen '$environmentVariableName'. Dersom du prøver kjøre lokalt må den legges til i '.env'-fila. Se eksempler i '.env.template'.")
    }
}

data class ApplicationConfig(
    val runtimeEnvironment: RuntimeEnvironment,
    val naisCluster: NaisCluster?,
    val gitCommit: GitCommit?,
    val leaderPodLookupPath: String,
    val pdfgenLocal: Boolean,
    val serviceUser: ServiceUserConfig,
    val azure: AzureConfig,
    val frikort: FrikortConfig,
    val oppdrag: OppdragConfig,
    val database: DatabaseConfig,
    val clientsConfig: ClientsConfig,
    val kafkaConfig: KafkaConfig,
    val kabalKafkaConfig: KabalKafkaConfig,
    val institusjonsoppholdKafkaConfig: InstitusjonsoppholdKafkaConfig,
) {
    enum class RuntimeEnvironment {
        Test,
        Local,
        Nais,
    }

    enum class NaisCluster {
        Dev,
        Prod,
    }

    data class FrikortConfig(
        val serviceUsername: List<String>,
        val useStubForSts: Boolean,
    ) {
        companion object {
            fun createFromEnvironmentVariables() = FrikortConfig(
                serviceUsername = getEnvironmentVariableOrThrow("FRIKORT_SERVICE_USERNAME").split(","),
                useStubForSts = false,
            )

            fun createLocalConfig() = FrikortConfig(
                serviceUsername = getEnvironmentVariableOrDefault("FRIKORT_SERVICE_USERNAME", "frikort").split(","),
                useStubForSts = getEnvironmentVariableOrDefault("USE_STUB_FOR_STS", "true") == "true",
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
        val tilbakekreving: TilbakekrevingConfig,
    ) {
        /**
         * https://navno.sharepoint.com/sites/fag-og-ytelser-fagsystemer/?cid=9ef8f6af-c41e-4f6f-9417-15e6fda16792
         */
        val ordinærÅpningstid = LocalTime.of(6, 0, 0) to LocalTime.of(21, 0, 0)

        data class UtbetalingConfig(
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

        data class AvstemmingConfig(
            val mqSendQueue: String,
        ) {
            companion object {
                fun createFromEnvironmentVariables() = AvstemmingConfig(
                    mqSendQueue = getEnvironmentVariableOrThrow("MQ_SEND_QUEUE_AVSTEMMING"),
                )
            }
        }

        data class SimuleringConfig(
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
                tilbakekreving = TilbakekrevingConfig.createFromEnvironmentVariables(),
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
                tilbakekreving = TilbakekrevingConfig(
                    mq = TilbakekrevingConfig.Mq("unused"),
                    soap = TilbakekrevingConfig.Soap("unused", "unused"),
                    serviceUserConfig = ServiceUserConfig("unused", "unused"),
                ),
            )
        }
    }

    sealed interface DatabaseConfig {
        val jdbcUrl: String

        data class RotatingCredentials(
            val databaseName: String,
            override val jdbcUrl: String,
            val vaultMountPath: String,
        ) : DatabaseConfig

        data class StaticCredentials(
            override val jdbcUrl: String,
        ) : DatabaseConfig {
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
        val kontaktOgReservasjonsregisterConfig: KontaktOgReservasjonsregisterConfig,
        val kabalConfig: KabalConfig,
        val safConfig: SafConfig,
        val skatteetatenConfig: SkatteetatenConfig,
    ) {
        companion object {
            fun createFromEnvironmentVariables() = ClientsConfig(
                oppgaveConfig = OppgaveConfig.createFromEnvironmentVariables(),
                pdlConfig = PdlConfig.createFromEnvironmentVariables(),
                dokDistUrl = getEnvironmentVariableOrThrow("DOKDIST_URL"),
                pdfgenUrl = getEnvironmentVariableOrDefault("PDFGEN_URL", "http://su-pdfgen.supstonad.svc.nais.local"),
                dokarkivUrl = getEnvironmentVariableOrThrow("DOKARKIV_URL"),
                kodeverkUrl = getEnvironmentVariableOrDefault("KODEVERK_URL", "http://kodeverk.team-rocket"),
                stsUrl = getEnvironmentVariableOrDefault(
                    "STS_URL",
                    "http://security-token-service.default.svc.nais.local",
                ),
                skjermingUrl = getEnvironmentVariableOrThrow("SKJERMING_URL"),
                kontaktOgReservasjonsregisterConfig = KontaktOgReservasjonsregisterConfig.createFromEnvironmentVariables(),
                kabalConfig = KabalConfig.createFromEnvironmentVariables(),
                safConfig = SafConfig.createFromEnvironmentVariables(),
                skatteetatenConfig = SkatteetatenConfig.createFromEnvironmentVariables(),
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
                kontaktOgReservasjonsregisterConfig = KontaktOgReservasjonsregisterConfig.createLocalConfig(),
                kabalConfig = KabalConfig.createLocalConfig(),
                safConfig = SafConfig.createLocalConfig(),
                skatteetatenConfig = SkatteetatenConfig.createLocalConfig(),
            )
        }

        data class KontaktOgReservasjonsregisterConfig(
            val appId: String,
            val url: String,
        ) {
            companion object {
                fun createFromEnvironmentVariables() = KontaktOgReservasjonsregisterConfig(
                    appId = getEnvironmentVariableOrThrow("KRR_APP_ID"),
                    url = getEnvironmentVariableOrThrow("KRR_URL"),
                )

                fun createLocalConfig() = KontaktOgReservasjonsregisterConfig(
                    appId = "mocked",
                    url = "mocked",
                )
            }
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

        data class KabalConfig(
            val url: String,
            val clientId: String,
        ) {
            companion object {
                fun createFromEnvironmentVariables() = KabalConfig(
                    url = getEnvironmentVariableOrDefault("KABAL_URL", "https://kabal-api.dev.intern.nav.no"),
                    clientId = getEnvironmentVariableOrDefault("KABAL_CLIENT_ID", "api://dev-gcp.klage.kabal-api"),
                )

                fun createLocalConfig() = KabalConfig(
                    url = "mocked",
                    clientId = "mocked",
                )
            }
        }

        data class SafConfig(
            val url: String,
            val clientId: String,
        ) {
            companion object {
                fun createFromEnvironmentVariables() = SafConfig(
                    url = getEnvironmentVariableOrDefault("SAF_URL", "https://saf.dev.intern.nav.no"),
                    clientId = getEnvironmentVariableOrDefault(
                        "SAF_CLIENT_ID",
                        "api:////dev-fss.teamdokumenthandtering.saf/.default",
                    ),
                )

                fun createLocalConfig() = SafConfig(
                    url = "mocked",
                    clientId = "mocked",
                )
            }
        }

        data class SkatteetatenConfig(
            val apiBaseUrl: String,
            val clientId: String,
            val consumerId: String,
            val rettighetspakke: String = "navSupplerendeStoenad",
        ) {
            companion object {
                fun createFromEnvironmentVariables(): SkatteetatenConfig {
                    val apiBaseUrl = getEnvironmentVariableOrThrow("SKATTEETATEN_URL")
                    val clientId = getEnvironmentVariableOrThrow("SKATT_CLIENT_ID")
                    return SkatteetatenConfig(
                        apiBaseUrl = apiBaseUrl,
                        clientId = clientId,
                        consumerId = NavIdentBruker.Saksbehandler.systembruker().toString(),
                    )
                }

                fun createLocalConfig() = SkatteetatenConfig(
                    apiBaseUrl = "mocked",
                    clientId = "mocked",
                    consumerId = NavIdentBruker.Saksbehandler.systembruker().toString(),
                )
            }
        }
    }

    data class KafkaConfig(
        val producerCfg: ProducerCfg,
        val consumerCfg: ConsumerCfg,
    ) {
        companion object {
            fun createFromEnvironmentVariables() = KafkaConfig(
                producerCfg = ProducerCfg(
                    kafkaConfig = CommonAivenKafkaConfig().configure() + mapOf(
                        ProducerConfig.ACKS_CONFIG to "all",
                        ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
                        ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
                    ),
                ),
                consumerCfg = ConsumerCfg(
                    CommonAivenKafkaConfig().configure() +
                        commonConsumerConfig(
                            keyDeserializer = StringDeserializer::class.java,
                            valueDeserializer = KafkaAvroDeserializer::class.java,
                            clientIdConfig = getEnvironmentVariableOrThrow("HOSTNAME"),
                        ) +
                        mapOf(
                            KafkaAvroDeserializerConfig.SPECIFIC_AVRO_READER_CONFIG to true,
                            KafkaAvroDeserializerConfig.BASIC_AUTH_CREDENTIALS_SOURCE to "USER_INFO",
                            KafkaAvroDeserializerConfig.USER_INFO_CONFIG to ConsumerCfg.getUserInfoConfig(),
                            KafkaAvroDeserializerConfig.SCHEMA_REGISTRY_URL_CONFIG to getEnvironmentVariableOrThrow("KAFKA_SCHEMA_REGISTRY"),
                        ),
                ),
            )

            fun createLocalConfig() = KafkaConfig(
                producerCfg = ProducerCfg(emptyMap()),
                consumerCfg = ConsumerCfg(emptyMap()),
            )
        }

        data class ProducerCfg(
            val kafkaConfig: Map<String, Any>,
            val retryTaskInterval: Duration = Duration.of(15, ChronoUnit.SECONDS),
        )

        data class ConsumerCfg(
            val kafkaConfig: Map<String, Any>,
        ) {
            companion object {
                fun getUserInfoConfig() = "${
                    getEnvironmentVariableOrDefault(
                        "KAFKA_SCHEMA_REGISTRY_USER",
                        "usr",
                    )
                }:${getEnvironmentVariableOrDefault("KAFKA_SCHEMA_REGISTRY_PASSWORD", "pwd")}"
            }
        }

        internal data class CommonAivenKafkaConfig(
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
                // Disable server host name verification
                SslConfigs.SSL_ENDPOINT_IDENTIFICATION_ALGORITHM_CONFIG to "",
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
            gitCommit = getEnvironmentVariableOrNull("NAIS_APP_IMAGE")?.let { GitCommit.fromString(it) }.also {
                if (it == null) log.error("Kunne ikke bestemme git commit hash fra environment variabel NAIS_APP_IMAGE.")
            },
            leaderPodLookupPath = getEnvironmentVariableOrThrow("ELECTOR_PATH"),
            pdfgenLocal = false,
            serviceUser = ServiceUserConfig.createFromEnvironmentVariables(),
            azure = AzureConfig.createFromEnvironmentVariables(::getEnvironmentVariableOrThrow),
            frikort = FrikortConfig.createFromEnvironmentVariables(),
            oppdrag = OppdragConfig.createFromEnvironmentVariables(),
            database = DatabaseConfig.createFromEnvironmentVariables(),
            clientsConfig = ClientsConfig.createFromEnvironmentVariables(),
            kafkaConfig = KafkaConfig.createFromEnvironmentVariables(),
            kabalKafkaConfig = KabalKafkaConfig.createFromEnvironmentVariables(),
            institusjonsoppholdKafkaConfig = InstitusjonsoppholdKafkaConfig.createFromEnvironmentVariables(),
        )

        fun createLocalConfig() = ApplicationConfig(
            runtimeEnvironment = RuntimeEnvironment.Local,
            naisCluster = naisCluster(),
            gitCommit = GitCommit("87a3a5155bf00b4d6854efcc24e8b929549c9302"),
            leaderPodLookupPath = "",
            pdfgenLocal = getEnvironmentVariableOrDefault("PDFGEN_LOCAL", "false").toBooleanStrict(),
            serviceUser = ServiceUserConfig.createLocalConfig(),
            azure = AzureConfig.createLocalConfig(::getEnvironmentVariableOrDefault),
            frikort = FrikortConfig.createLocalConfig(),
            oppdrag = OppdragConfig.createLocalConfig(),
            database = DatabaseConfig.createLocalConfig(),
            clientsConfig = ClientsConfig.createLocalConfig(),
            kafkaConfig = KafkaConfig.createLocalConfig(),
            kabalKafkaConfig = KabalKafkaConfig.createLocalConfig(),
            institusjonsoppholdKafkaConfig = InstitusjonsoppholdKafkaConfig.createLocalConfig(),
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
        fun fnrKode6() = getEnvironmentVariableOrNull("FNR_KODE6")
    }

    data class KabalKafkaConfig(
        val kafkaConfig: Map<String, Any>,
    ) {
        companion object {
            fun createFromEnvironmentVariables() = KabalKafkaConfig(
                kafkaConfig = KafkaConfig.CommonAivenKafkaConfig().configure() +
                    commonConsumerConfig(
                        keyDeserializer = StringDeserializer::class.java,
                        valueDeserializer = StringDeserializer::class.java,
                        clientIdConfig = getEnvironmentVariableOrThrow("HOSTNAME"),
                    ),
            )

            fun createLocalConfig() = KabalKafkaConfig(
                kafkaConfig = emptyMap(),
            )
        }
    }

    data class InstitusjonsoppholdKafkaConfig(
        val kafkaConfig: Map<String, Any>,
        val topicName: String,
    ) {
        companion object {
            fun createFromEnvironmentVariables() = InstitusjonsoppholdKafkaConfig(
                kafkaConfig = KafkaConfig.CommonAivenKafkaConfig().configure() +
                    commonConsumerConfig(
                        keyDeserializer = StringDeserializer::class.java,
                        valueDeserializer = StringDeserializer::class.java,
                        clientIdConfig = getEnvironmentVariableOrThrow("HOSTNAME"),
                    ),
                topicName = getEnvironmentVariableOrThrow("INSTITUSJONSOPPHOLD_TOPIC"),
            )

            fun createLocalConfig() = InstitusjonsoppholdKafkaConfig(
                kafkaConfig = emptyMap(),
                topicName = "INSTITUSJONSOPPHOLD_TOPIC",
            )
        }
    }
}

fun commonConsumerConfig(
    groupIdConfig: String = "su-se-bakover",
    maxPollRecordsConfig: Int = 100,
    enableAutoCommitConfig: Boolean = false,
    clientIdConfig: String,
    autoOffsetResetConfig: String = "earliest",
    keyDeserializer: Class<*>,
    valueDeserializer: Class<*>,
): Map<String, Any> {
    return mapOf(
        ConsumerConfig.GROUP_ID_CONFIG to groupIdConfig,
        ConsumerConfig.MAX_POLL_RECORDS_CONFIG to maxPollRecordsConfig,
        ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG to enableAutoCommitConfig.toString(),
        ConsumerConfig.CLIENT_ID_CONFIG to clientIdConfig,
        ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to autoOffsetResetConfig,
        ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to keyDeserializer,
        ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to valueDeserializer,
    )
}
