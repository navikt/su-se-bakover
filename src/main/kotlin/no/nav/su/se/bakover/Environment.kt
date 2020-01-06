package no.nav.su.se.bakover

data class Environment(
        val corsKey: String = "ALLOW_CORS_ORIGIN",
        val suPersonUrl: String = "http://su-person",
        val suInntektUrl: String = "http://su-inntekt",
        val oidcConfigUrl: String = getEnvVar("OIDC_CONFIG_URL"),
        val oidcClientId: String = getEnvVar("OIDC_CLIENT_ID"),
        val oidcRequiredGroup: String = getEnvVar("OIDC_REQUIRED_GROUP"),
        val allowCorsOrigin: String = getEnvVar(corsKey)
)

private fun getEnvVar(varName: String) = getOptionalEnvVar(varName) ?: throw Exception("mangler verdi for $varName")

private fun getOptionalEnvVar(varName: String): String? = System.getenv(varName)

