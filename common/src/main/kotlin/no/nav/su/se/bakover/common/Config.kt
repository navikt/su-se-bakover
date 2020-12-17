package no.nav.su.se.bakover.common

import io.github.cdimascio.dotenv.Dotenv
import io.github.cdimascio.dotenv.dotenv
import no.nav.su.se.bakover.common.Config.getEnvironmentVariableOrThrow

object Config {

    private val env by lazy { init() }

    val isLocalOrRunningTests = env["NAIS_CLUSTER_NAME"] == null
    val leaderPodLookupPath = env["ELECTOR_PATH"] ?: ""

    val vaultMountPath = env["VAULT_MOUNTPATH"] ?: ""

    val databaseName by lazy { getEnvironmentVariableOrThrow("DATABASE_NAME") }
    val jdbcUrl by lazy { getEnvironmentVariableOrThrow("DATABASE_JDBC_URL") }

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

    val oppdrag by lazy { Oppdrag() }

    data class Oppdrag(
        val mqQueueManager: String = env["MQ_QUEUE_MANAGER"] ?: "MQ1LSC02",
        val mqPort: Int = env["MQ_PORT"]?.toInt() ?: 1413,
        val mqHostname: String = env["MQ_HOSTNAME"] ?: "b27apvl176.preprod.local",
        val mqChannel: String = env["MQ_CHANNEL"] ?: "Q1_SU_SE_BAKOVER",
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
    val serviceUser: ServiceUser,
    val azureConfig: AzureConfig,
) {
    data class ServiceUser(
        val username: String,
        val password: String,
    ) {
        override fun toString(): String {
            return "ServiceUser(username='$username', password='****')"
        }

        companion object {
            fun createFromEnvironmentVariables() = ServiceUser(
                username = getEnvironmentVariableOrThrow("username"),
                password = getEnvironmentVariableOrThrow("password"),
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
    companion object {
        fun createFromEnvironmentVariables() = ApplicationConfig(
            serviceUser = ServiceUser.createFromEnvironmentVariables(),
            azureConfig = AzureConfig.createFromEnvironmentVariables(),
        )
    }
}
