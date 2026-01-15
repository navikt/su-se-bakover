package no.nav.su.se.bakover.common.infrastructure.config

import no.nav.su.se.bakover.common.domain.config.ServiceUserConfig

fun ServiceUserConfig.Companion.createFromEnvironmentVariables(): ServiceUserConfig {
    return ServiceUserConfig(
        username = EnvironmentConfig.getEnvironmentVariableOrThrow("serviceuser"),
        password = EnvironmentConfig.getEnvironmentVariableOrThrow("serviceuserpw"),
    )
}

fun ServiceUserConfig.Companion.createLocalConfig() = ServiceUserConfig(
    username = "unused",
    password = "unused",
)
