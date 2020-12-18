package no.nav.su.se.bakover.common

import io.github.cdimascio.dotenv.Dotenv
import io.github.cdimascio.dotenv.dotenv
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.config.SslConfigs
import org.apache.kafka.common.security.auth.SecurityProtocol
import org.apache.kafka.common.serialization.StringSerializer

object Config {

    private val env by lazy { init() }

    val isLocalOrRunningTests = env["NAIS_CLUSTER_NAME"] == null
    val isPreprod = env["NAIS_CLUSTER_NAME"] == "dev-fss"
    val leaderPodLookupPath = env["ELECTOR_PATH"] ?: ""

    val vaultMountPath = env["VAULT_MOUNTPATH"] ?: ""

    val databaseName = env["DATABASE_NAME"] ?: "supstonad-db-local"
    val jdbcUrl = env["DATABASE_JDBC_URL"] ?: "jdbc:postgresql://localhost:5432/supstonad-db-local"

    val azureClientSecret = env["AZURE_APP_CLIENT_SECRET"] ?: "Denne håndteres av nais. Må ligge i .env lokalt."
    val azureWellKnownUrl = env["AZURE_APP_WELL_KNOWN_URL"] ?: "https://login.microsoftonline.com/966ac572-f5b7-4bbe-aa88-c76419c0f851/v2.0/.well-known/openid-configuration"
    val azureClientId = env["AZURE_APP_CLIENT_ID"] ?: "26a62d18-70ce-48a6-9f4d-664607bd5188"
    val azureBackendCallbackUrl = env["BACKEND_CALLBACK_URL"] ?: "http://localhost:8080/callback"
    val azureGroupAttestant = env["AZURE_GROUP_ATTESTANT"] ?: "d75164fa-39e6-4149-956e-8404bc9080b6"
    val azureGroupSaksbehandler = env["AZURE_GROUP_SAKSBEHANDLER"] ?: "0ba009c4-d148-4a51-b501-4b1cf906889d"
    val azureGroupVeileder = env["AZURE_GROUP_VEILEDER"] ?: "062d4814-8538-4f3a-bcb9-32821af7909a"
    val oppgaveClientId = env["OPPGAVE_CLIENT_ID"] ?: "41ca50ba-e44f-4bc8-9e31-b745a0041926"

    val pdlUrl = env["PDL_URL"] ?: "http://pdl-api.default.svc.nais.local"
    val dokDistUrl = env["DOKDIST_URL"] ?: "http://dokdistfordeling.default.svc.nais.local"
    val pdfgenUrl = env["PDFGEN_URL"] ?: "http://su-pdfgen.supstonad.svc.nais.local"
    val dokarkivUrl = env["DOKARKIV_URL"] ?: "http://dokarkiv.default.svc.nais.local"
    val oppgaveUrl = env["OPPGAVE_URL"] ?: "http://oppgave.oppgavehandtering.svc.nais.local"
    val kodeverkUrl = env["KODEVERK_URL"] ?: "http://kodeverk.default.svc.nais.local"
    val stsUrl = env["STS_URL"] ?: "http://security-token-service.default.svc.nais.local"
    val skjermingUrl = env["SKJERMING_URL"] ?: "https://skjermede-personer-pip.nais.adeo.no"
    val dkifUrl = env["DKIF_URL"] ?: "http://dkif.default.svc.nais.local"

    val serviceUser = ServiceUser()
    val kafka = Kafka()

    data class ServiceUser(
        val username: String = env["username"] ?: "username",
        val password: String = env["password"] ?: "password"
    ) {
        override fun toString(): String {
            return "ServiceUser(username='$username', password='****')"
        }
    }

    val pdfgenLocal = env["PDFGEN_LOCAL"]?.toBoolean() ?: false
    val fnrForPersonMedSkjerming = env["DEV_FNR_WITH_SKJERMING"]

    val corsAllowOrigin = env["ALLOW_CORS_ORIGIN"] ?: "localhost:1234"
    private val frontendBaseUrl = env["FRONTEND_BASE_URL"] ?: "http://localhost:1234"
    val suSeFramoverLoginSuccessUrl = "$frontendBaseUrl/auth/complete"
    val suSeFramoverLogoutSuccessUrl = "$frontendBaseUrl/logout/complete"

    val oppdrag = Oppdrag(serviceUser = serviceUser)

    data class Oppdrag(
        val mqQueueManager: String = env["MQ_QUEUE_MANAGER"] ?: "MQ1LSC02",
        val mqPort: Int = env["MQ_PORT"]?.toInt() ?: 1413,
        val mqHostname: String = env["MQ_HOSTNAME"] ?: "b27apvl176.preprod.local",
        val mqChannel: String = env["MQ_CHANNEL"] ?: "Q1_SU_SE_BAKOVER",
        val serviceUser: ServiceUser,
        val utbetaling: Utbetaling = Utbetaling(),
        val avstemming: Avstemming = Avstemming(),
        val simulering: Simulering = Simulering(),
    ) {
        data class Utbetaling internal constructor(
            val mqSendQueue: String = env["MQ_SEND_QUEUE_UTBETALING"] ?: "QA.Q1_231.OB04_OPPDRAG_XML",
            val mqReplyTo: String = env["MQ_REPLY_TO"] ?: "QA.Q1_SU_SE_BAKOVER.OPPDRAG_KVITTERING"
        )

        data class Avstemming internal constructor(
            /* Setter target client = 1 for bakoverkompabilitet med stormaskin */
            val mqSendQueue: String = env["MQ_SEND_QUEUE_AVSTEMMING"] ?: "queue:///QA.Q1_234.OB29_AVSTEMMING_XML?targetClient=1",
        )

        data class Simulering internal constructor(
            val url: String = env["SIMULERING_URL"] ?: "",
            val stsSoapUrl: String = env["STS_URL_SOAP"] ?: ""
        )
    }

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
}
