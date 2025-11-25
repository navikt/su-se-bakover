package no.nav.su.se.bakover.common.infrastructure.config

import no.nav.su.se.bakover.common.domain.config.ServiceUserConfig

fun ServiceUserConfig.Companion.createFromEnvironmentVariables(isGcp: Boolean): ServiceUserConfig {
    return if (isGcp) {
        ServiceUserConfig(
            username = EnvironmentConfig.getEnvironmentVariableOrThrow("serviceuser"),
            password = EnvironmentConfig.getEnvironmentVariableOrThrow("serviceuserpw"),
        )
    } else {
        ServiceUserConfig(
            username = EnvironmentConfig.getEnvironmentVariableOrThrow("username"),
            password = EnvironmentConfig.getEnvironmentVariableOrThrow("password"),
        )
    }
}

fun ServiceUserConfig.Companion.createLocalConfig() = ServiceUserConfig(
    username = "unused",
    password = "unused",
)
