package no.nav.su.se.bakover.common

import io.github.cdimascio.dotenv.Dotenv
import io.github.cdimascio.dotenv.dotenv

object Config {

    private val env by lazy { init() }

    val isLocalOrRunningTests = env["NAIS_CLUSTER_NAME"] == null
    val leaderPodLookupPath = env["ELECTOR_PATH"] ?: ""

    val vaultMountPath = env["VAULT_MOUNTPATH"] ?: ""

    val databaseName by lazy {
        getEnvironmentVariableOrThrow("DATABASE_JDBC_URL")
    }

    val jdbcUrl by lazy {
        getEnvironmentVariableOrThrow("DATABASE_JDBC_URL")
    }

    val azureClientSecret = env["AZURE_APP_CLIENT_SECRET"] ?: "Denne håndteres av nais. Må ligge i .env lokalt."
    val azureWellKnownUrl =
        env["AZURE_APP_WELL_KNOWN_URL"] ?: "http://localhost:12345/denne-haandteres-av-nais/og/maa-ligge-i-env-lokalt"
    val azureClientId = env["AZURE_APP_CLIENT_ID"] ?: "Denne håndteres av nais. Må ligge i .env lokalt."
    val azureBackendCallbackUrl = env["BACKEND_CALLBACK_URL"] ?: "Denne håndteres av nais. Må ligge i .env lokalt."
    val azureGroupAttestant = env["AZURE_GROUP_ATTESTANT"] ?: "Denne er forskjellig per miljø. Må ligge i .env lokalt."
    val azureGroupSaksbehandler =
        env["AZURE_GROUP_SAKSBEHANDLER"] ?: "0ba009c4-d148-4a51-b501-4b1cf906889d" // Tester feiler hvis denne endres
    val azureGroupVeileder =
        env["AZURE_GROUP_VEILEDER"] ?: "062d4814-8538-4f3a-bcb9-32821af7909a" // Tester feiler hvis denne endres
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

    val serviceUser by lazy { ServiceUser() }

    data class ServiceUser(
        val username: String = getEnvironmentVariableOrThrow("username"),
        val password: String = getEnvironmentVariableOrThrow("password"),
    ) {
        override fun toString(): String {
            return "ServiceUser(username='$username', password='****')"
        }
    }

    val pdfgenLocal = env["PDFGEN_LOCAL"]?.toBoolean() ?: false

    val corsAllowOrigin = env["ALLOW_CORS_ORIGIN"] ?: "localhost:1234"
    private val frontendBaseUrl = env["FRONTEND_BASE_URL"] ?: "http://localhost:1234"
    val suSeFramoverLoginSuccessUrl = "$frontendBaseUrl/auth/complete"
    val suSeFramoverLogoutSuccessUrl = "$frontendBaseUrl/logout/complete"

    val oppdrag by lazy { Oppdrag(serviceUser = serviceUser) }

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
            val mqSendQueue: String = env["MQ_SEND_QUEUE_AVSTEMMING"]
                ?: "queue:///QA.Q1_234.OB29_AVSTEMMING_XML?targetClient=1",
        )

        data class Simulering internal constructor(
            val url: String = env["SIMULERING_URL"] ?: "",
            val stsSoapUrl: String = env["STS_URL_SOAP"] ?: ""
        )
    }

    fun init(): Dotenv {
        return dotenv {
            ignoreIfMissing = true
            systemProperties = true
        }
    }

    private fun getEnvironmentVariableOrThrow(environmentVariableName: String): String {
        return env[environmentVariableName] ?: throwMissingEnvironmentVariable(environmentVariableName)
    }

    private fun throwMissingEnvironmentVariable(environmentVariableName: String): Nothing {
        throw IllegalStateException("Mangler environment variabelen '$environmentVariableName'. Dersom du prøver kjøre lokalt må den legges til i '.env'-fila. Se eksempler i '.env.template'.")
    }
}
