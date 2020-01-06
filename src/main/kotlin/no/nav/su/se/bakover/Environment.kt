package no.nav.su.se.bakover

data class Environment(
    val suPersonUrl: String = "http://su-person",
    val suInntektUrl: String = "http://su-inntekt",
    val oidcConfigUrl: String = getEnvVar("OIDC_CONFIG_URL"),
    val oidcClientId: String = getEnvVar("OIDC_CLIENT_ID"),
    val oidcRequiredGroup: String = getEnvVar("OIDC_REQUIRED_GROUP"),
    val allowCorsOrigin: String = getEnvVar("ALLOW_CORS_ORIGIN"),
    val azureTenant:String = getEnvVar("AZURE_TENANT_ID"),
    val jwtIssuer: String = "https://login.microsoftonline.com/$azureTenant/v2.0",
    val jwksUrl: String = "https://login.microsoftonline.com/$azureTenant/discovery/v2.0/keys"
)

private fun getEnvVar(varName: String) = getOptionalEnvVar(varName) ?: throw Exception("mangler verdi for $varName")

private fun getOptionalEnvVar(varName: String): String? = System.getenv(varName)

