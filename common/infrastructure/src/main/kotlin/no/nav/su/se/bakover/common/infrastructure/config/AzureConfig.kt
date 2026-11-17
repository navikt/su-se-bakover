package no.nav.su.se.bakover.common.infrastructure.config

import no.nav.su.se.bakover.common.infrastructure.brukerrolle.AzureGroups

data class AzureConfig(
    val clientSecret: String,
    val wellKnownUrl: String,
    val clientId: String,
    val groups: AzureGroups,
) {
    companion object {
        fun createFromEnvironmentVariables(
            getEnvironmentVariableOrThrow: (String) -> String,
        ): AzureConfig {
            return AzureConfig(
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
        }

        fun createLocalConfig(
            getEnvironmentVariableOrDefault: (String, String) -> String,
        ): AzureConfig {
            return AzureConfig(
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
}
