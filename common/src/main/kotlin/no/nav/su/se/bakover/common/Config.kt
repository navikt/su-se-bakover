package no.nav.su.se.bakover.common

import io.github.cdimascio.dotenv.Dotenv
import io.github.cdimascio.dotenv.dotenv

object Config {

    private val env by lazy { init() }

    val isLocalOrRunningTests = env["NAIS_CLUSTER_NAME"] == null
    val leaderPodLookupPath = env["ELECTOR_PATH"] ?: ""

    val vaultMountPath = env["VAULT_MOUNTPATH"] ?: ""

    val databaseName = env["DATABASE_NAME"] ?: "supstonad-db-local"
    val jdbcUrl = env["DATABASE_JDBC_URL"] ?: "jdbc:postgresql://localhost:5432/supstonad-db-local"

    val azureClientSecret = env["AZURE_CLIENT_SECRET"] ?: "secret"
    val azureWellKnownUrl = env["AZURE_WELLKNOWN_URL"] ?: "https://login.microsoftonline.com/966ac572-f5b7-4bbe-aa88-c76419c0f851/v2.0/.well-known/openid-configuration"
    val azureClientId = env["AZURE_CLIENT_ID"] ?: "24ea4acb-547e-45de-a6d3-474bd8bed46e"
    val azureBackendCallbackUrl = env["BACKEND_CALLBACK_URL"] ?: "http://localhost:8080/callback"
    val azureGroupAttestant = env["AZURE_GROUP_ATTESTANT"] ?: "d75164fa-39e6-4149-956e-8404bc9080b6"
    val azureGroupSaksbehandler = env["AZURE_GROUP_SAKSBEHANDLER"] ?: "0ba009c4-d148-4a51-b501-4b1cf906889d"
    val azureGroupVeileder = env["AZURE_GROUP_VEILEDER"] ?: "062d4814-8538-4f3a-bcb9-32821af7909a"
    val suInntektAzureClientId = env["SU_INNTEKT_AZURE_CLIENT_ID"] ?: "9cd61904-33ad-40e8-9cc8-19e4dab588c5"

    val pdlUrl = env["PDL_URL"] ?: "http://pdl-api.default.svc.nais.local"
    val dokDistUrl = env["DOKDIST_URL"] ?: "http://dokdistfordeling.default.svc.nais.local"
    val suInntektUrl = env["SU_INNTEKT_URL"] ?: "http://su-inntekt.default.svc.nais.local"
    val pdfgenUrl = env["PDFGEN_URL"] ?: "http://su-pdfgen.default.svc.nais.local"
    val dokarkivUrl = env["DOKARKIV_URL"] ?: "http://dokarkiv.default.svc.nais.local"
    val oppgaveUrl = env["OPPGAVE_URL"] ?: "http://oppgave.default.svc.nais.local"
    val kodeverkUrl = env["KODEVERK_URL"] ?: "http://kodeverk.default.svc.nais.local"
    val stsUrl = env["STS_URL"] ?: "http://security-token-service.default.svc.nais.local"
    val skjermingUrl = env["SKJERMING_URL"] ?: "https://skjermede-personer-pip.nais.adeo.no"
    val dkifUrl = env["DKIF_URL"] ?: "http://dkif.default.svc.nais.local"

    val serviceUser = ServiceUser()
    data class ServiceUser(
        val username: String = env["username"] ?: "username",
        val password: String = env["password"] ?: "password"
    ) {
        override fun toString(): String {
            return "ServiceUser(username='$username', password='****')"
        }
    }

    val pdfgenLocal = env["PDFGEN_LOCAL"]?.toBoolean() ?: false

    val corsAllowOrigin = env["ALLOW_CORS_ORIGIN"] ?: "localhost:1234"
    val frontendBaseUrl = env["FRONTEND_BASE_URL"] ?: "http://localhost:1234"
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
        val avstemming: Avstemming = Avstemming()
    ) {
        data class Utbetaling(
            val mqSendQueue: String = env["MQ_SEND_QUEUE"] ?: "QA.Q1_231.OB04_OPPDRAG_XML",
            val mqReplyTo: String = env["MQ_REPLY_TO"] ?: "QA.Q1_SU_SE_BAKOVER.OPPDRAG_KVITTERING"
        )

        data class Avstemming(
            /* Setter target client = 1 for bakoverkompabilitet med stormaskin */
            val mqSendQueue: String = env["MQ_SEND_QUEUE"] ?: "queue:///QA.Q1_234.OB29_AVSTEMMING_XML?targetClient=1",
        )
    }

    data class Simulering(
        val url: String = env["SIMULERING_URL"] ?: "",
        val stsSoapUrl: String = env["STS_URL_SOAP"] ?: ""
    )

    fun init(): Dotenv {
        return dotenv {
            ignoreIfMissing = true
            systemProperties = true
        }
    }
}
