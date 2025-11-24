package no.nav.su.se.bakover.common.infrastructure.config

import no.nav.su.se.bakover.common.domain.config.ServiceUserConfig

// TODO: MÅ BYTTES UT kun for å teste
fun ServiceUserConfig.Companion.createFromEnvironmentVariables() = ServiceUserConfig(
    username = "username",
    password = "password",
)

fun ServiceUserConfig.Companion.createLocalConfig() = ServiceUserConfig(
    username = "unused",
    password = "unused",
)
