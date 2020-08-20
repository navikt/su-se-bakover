package no.nav.su.se.bakover.common

import io.github.cdimascio.dotenv.Dotenv
import io.github.cdimascio.dotenv.dotenv

object Config {

    private val env by lazy { init() }

    val isLocalOrRunningTests = env["NAIS_CLUSTER_NAME"] == null

    val vaultMountPath = env["VAULT_MOUNTPATH"] ?: ""

    val databaseName = env["DATABASE_NAME"] ?: "supstonad-db-local"
    val jdbcUrl = env["DATABASE_JDBC_URL"] ?: "jdbc:postgresql://localhost:5432/supstonad-db-local"

    val azureClientSecret = env["AZURE_CLIENT_SECRET"] ?: "secret"
    val azureWellKnownUrl = env["AZURE_WELLKNOWN_URL"] ?: "https://login.microsoftonline.com/966ac572-f5b7-4bbe-aa88-c76419c0f851/v2.0/.well-known/openid-configuration"
    val azureClientId = env["AZURE_CLIENT_ID"] ?: "24ea4acb-547e-45de-a6d3-474bd8bed46e"
    val azureBackendCallbackUrl = env["BACKEND_CALLBACK_URL"] ?: "http://localhost:8080/callback"
    val azureRequiredGroup = env["AZURE_REQUIRED_GROUP"] ?: "requiredgroup"
    val suInntektAzureClientId = env["SU_INNTEKT_AZURE_CLIENT_ID"] ?: "9cd61904-33ad-40e8-9cc8-19e4dab588c5"

    val pdlUrl = env["PDL_URL"] ?: "http://pdl-api.default.svc.nais.local"
    val suInntektUrl = env["SU_INNTEKT_URL"] ?: "http://su-inntekt.default.svc.nais.local"
    val pdfgenUrl = env["PDFGEN_URL"] ?: "http://su-pdfgen.default.svc.nais.local"
    val dokarkivUrl = env["DOKARKIV_URL"] ?: "http://dokarkiv.default.svc.nais.local"
    val oppgaveUrl = env["OPPGAVE_URL"] ?: "http://oppgave.default.svc.nais.local"
    val kodeverkUrl = env["KODEVERK_URL"] ?: "http://kodeverk.default.svc.nais.local"
    val stsUrl = env["STS_URL"] ?: "http://security-token-service.default.svc.nais.local"

    val stsUsername = env["username"] ?: "username"
    val stsPassword = env["password"] ?: "password"

    val pdfgenLocal = env["PDFGEN_LOCAL"]?.toBoolean() ?: false

    val corsAllowOrigin = env["ALLOW_CORS_ORIGIN"] ?: "localhost:1234"
    val suSeFramoverRedirectUrl = env["FRONTEND_REDIRECT_URL"] ?: "http://localhost:1234/auth/complete"

    fun init(): Dotenv {
        return dotenv {
            ignoreIfMissing = true
            systemProperties = true
        }
    }
}
