package no.nav.su.se.bakover.common

import io.confluent.kafka.serializers.KafkaAvroDeserializer
import io.confluent.kafka.serializers.KafkaAvroDeserializerConfig
import io.github.cdimascio.dotenv.dotenv
import no.nav.su.se.bakover.common.EnvironmentConfig.getEnvironmentVariableOrDefault
import no.nav.su.se.bakover.common.EnvironmentConfig.getEnvironmentVariableOrNull
import no.nav.su.se.bakover.common.EnvironmentConfig.getEnvironmentVariableOrThrow
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.config.SaslConfigs
import org.apache.kafka.common.config.SslConfigs
import org.apache.kafka.common.security.auth.SecurityProtocol
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.temporal.ChronoUnit

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
    val kabalKafkaConfig: KabalKafkaConfig,
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
                clientSecret = getEnvironmentVariableOrDefault(
                    "AZURE_APP_CLIENT_SECRET",
                    "Denne brukes bare dersom man bruker en reell PDL/Oppgave-integrasjon o.l.",
                ),
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
        val tilbakekreving: TilbakekrevingConfig,
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

        data class TilbakekrevingConfig(
            val mq: Mq,
            val soap: Soap,
        ) {
            companion object {
                fun createFromEnvironmentVariables() = TilbakekrevingConfig(
                    mq = Mq.createFromEnvironmentVariables(),
                    soap = Soap.createFromEnvironmentVariables(),
                )
            }

            data class Mq(
                val mottak: String,
            ) {
                companion object {
                    fun createFromEnvironmentVariables() = Mq(
                        mottak = getEnvironmentVariableOrThrow("MQ_TILBAKEKREVING_MOTTAK"),
                    )
                }
            }

            data class Soap(
                val url: String,
            ) {
                companion object {
                    fun createFromEnvironmentVariables() = Soap(
                        url = getEnvironmentVariableOrThrow("TILBAKEKREVING_URL"),
                    )
                }
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
                    soap = TilbakekrevingConfig.Soap("unused"),
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
        val kabalConfig: KabalConfig,
        val safConfig: SafConfig,
        val maskinportenConfig: MaskinportenConfig,
        val skatteetatenConfig: SkatteetatenConfig,
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
                kabalConfig = KabalConfig.createFromEnvironmentVariables(),
                safConfig = SafConfig.createFromEnvironmentVariables(),
                maskinportenConfig = MaskinportenConfig.createFromEnvironmentVariables(),
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
                dkifUrl = "mocked",
                kabalConfig = KabalConfig.createLocalConfig(),
                safConfig = SafConfig.createLocalConfig(),
                maskinportenConfig = MaskinportenConfig.createLocalConfig(),
                skatteetatenConfig = SkatteetatenConfig.createLocalConfig(),
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
            val apiUri: String,

        ) {
            companion object {
                fun createFromEnvironmentVariables() = SkatteetatenConfig(getEnvironmentVariableOrDefault("SKATTEETATEN_URL", "https://api-test.sits.no"))
                fun createLocalConfig() = SkatteetatenConfig("mocked")
            }
        }

        data class MaskinportenConfig(
            val clientId: String,
            val scopes: String,
            val clientJwk: String,
            val wellKnownUrl: String,
            val issuer: String,
            val jwksUri: String,
            val tokenEndpoint: String,
        ) {
            companion object {
                fun createFromEnvironmentVariables() = MaskinportenConfig(
                    clientId = getEnvironmentVariableOrDefault("MASKINPORTEN_CLIENT_ID", "maskinporten_client_id"),
                    scopes = getEnvironmentVariableOrDefault("MASKINPORTEN_SCOPES", "maskinporten_scopes"),
                    clientJwk = getEnvironmentVariableOrDefault("MASKINPORTEN_CLIENT_JWK", "maskinporten_client_jwk"),
                    wellKnownUrl = getEnvironmentVariableOrDefault("MASKINPORTEN_WELL_KNOWN_URL", "maskinporten_well_known_url"),
                    issuer = getEnvironmentVariableOrDefault("MASKINPORTEN_ISSUER", "maskinporten_issuer"),
                    jwksUri = getEnvironmentVariableOrDefault("MASKINPORTEN_JWKS_URI", "maskinporten_jwks_uri"),
                    tokenEndpoint = getEnvironmentVariableOrDefault("MASKINPORTEN_TOKEN_ENDPOINT", "maskinporten_token_endpoint")
                )

                fun createLocalConfig(): MaskinportenConfig {
                    return MaskinportenConfig(
                        clientId = "mocked",
                        scopes = "mocked",
                        clientJwk = "mocked",
                        wellKnownUrl = "mocked",
                        issuer = "mocked",
                        jwksUri = "mocked",
                        tokenEndpoint = "mocked",
                    )
                }
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
                    CommonOnpremKafkaConfig().configure() + mapOf(
                        KafkaAvroDeserializerConfig.SPECIFIC_AVRO_READER_CONFIG to true,
                        KafkaAvroDeserializerConfig.SCHEMA_REGISTRY_URL_CONFIG to getEnvironmentVariableOrDefault(
                            "KAFKA_ONPREM_SCHEMA_REGISTRY",
                            "schema_onprem_registry",
                        ),
                        KafkaAvroDeserializerConfig.BASIC_AUTH_CREDENTIALS_SOURCE to "USER_INFO",
                        KafkaAvroDeserializerConfig.USER_INFO_CONFIG to ConsumerCfg.getUserInfoConfig(),
                        ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to KafkaAvroDeserializer::class.java,
                        ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to KafkaAvroDeserializer::class.java,
                        ConsumerConfig.CLIENT_ID_CONFIG to getEnvironmentVariableOrDefault(
                            "HOSTNAME",
                            "su-se-bakover-hostname",
                        ),
                        ConsumerConfig.GROUP_ID_CONFIG to "su-se-bakover",
                        ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG to "false",
                        ConsumerConfig.MAX_POLL_RECORDS_CONFIG to 100,
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

        private data class CommonOnpremKafkaConfig(
            val brokers: String = getEnvironmentVariableOrDefault("KAFKA_ONPREM_BROKERS", "kafka_onprem_brokers"),
            val saslConfigs: Map<String, String> = SaslConfig().configure(),
        ) {
            fun configure(): Map<String, String> =
                mapOf(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG to brokers) + saslConfigs
        }

        private data class SaslConfig(
            val truststorePath: String = getEnvironmentVariableOrDefault("NAV_TRUSTSTORE_PATH", "truststorePath"),
            val credstorePwd: String = getEnvironmentVariableOrDefault("NAV_TRUSTSTORE_PASSWORD", "credstorePwd"),
            val username: String = getEnvironmentVariableOrDefault("username", "not-a-real-srvuser"),
            val password: String = getEnvironmentVariableOrDefault("password", "not-a-real-srvpassword"),
        ) {
            fun configure(): Map<String, String> = mapOf(
                CommonClientConfigs.SECURITY_PROTOCOL_CONFIG to SecurityProtocol.SASL_SSL.name,
                SaslConfigs.SASL_MECHANISM to "PLAIN",
                SaslConfigs.SASL_JAAS_CONFIG to "org.apache.kafka.common.security.plain.PlainLoginModule required username=\"$username\" password=\"$password\";",
                SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG to truststorePath,
                SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG to credstorePwd,
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
            kabalKafkaConfig = KabalKafkaConfig.createFromEnvironmentVariables(),
        )

        fun createLocalConfig() = ApplicationConfig(
            runtimeEnvironment = RuntimeEnvironment.Local,
            naisCluster = naisCluster(),
            leaderPodLookupPath = "",
            pdfgenLocal = getEnvironmentVariableOrDefault("PDFGEN_LOCAL", "false").toBooleanStrict(),
            serviceUser = ServiceUserConfig.createLocalConfig(),
            azure = AzureConfig.createLocalConfig(),
            frikort = FrikortConfig.createLocalConfig(),
            oppdrag = OppdragConfig.createLocalConfig(),
            database = DatabaseConfig.createLocalConfig(),
            clientsConfig = ClientsConfig.createLocalConfig(),
            kafkaConfig = KafkaConfig.createLocalConfig(),
            unleash = UnleashConfig.createFromEnvironmentVariables(),
            kabalKafkaConfig = KabalKafkaConfig.createLocalConfig(),
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
                kafkaConfig = KafkaConfig.CommonAivenKafkaConfig().configure() + mapOf(
                    ConsumerConfig.GROUP_ID_CONFIG to "su-se-bakover",
                    ConsumerConfig.CLIENT_ID_CONFIG to getEnvironmentVariableOrThrow("HOSTNAME"),
                    ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG to "false",
                    ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "earliest",
                    ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
                    ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
                    ConsumerConfig.MAX_POLL_RECORDS_CONFIG to 100,

                ),
            )

            fun createLocalConfig() = KabalKafkaConfig(
                kafkaConfig = emptyMap(),
            )
        }
    }
}
