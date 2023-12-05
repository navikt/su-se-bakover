package no.nav.su.se.bakover.common.infrastructure.config

import no.nav.su.se.bakover.common.domain.config.ServiceUserConfig
import no.nav.su.se.bakover.common.domain.config.TilbakekrevingConfig

fun TilbakekrevingConfig.Companion.createFromEnvironmentVariables() = TilbakekrevingConfig(
    mq = TilbakekrevingConfig.Mq.createFromEnvironmentVariables(),
    soap = TilbakekrevingConfig.Soap.createFromEnvironmentVariables(),
    serviceUserConfig = ServiceUserConfig.createFromEnvironmentVariables(),
)

fun TilbakekrevingConfig.Mq.Companion.createFromEnvironmentVariables() = TilbakekrevingConfig.Mq(
    mottak = EnvironmentConfig.getEnvironmentVariableOrThrow("MQ_TILBAKEKREVING_MOTTAK"),
)

fun TilbakekrevingConfig.Soap.Companion.createFromEnvironmentVariables() = TilbakekrevingConfig.Soap(
    url = EnvironmentConfig.getEnvironmentVariableOrThrow("TILBAKEKREVING_URL"),
    stsSoapUrl = EnvironmentConfig.getEnvironmentVariableOrThrow("STS_URL_SOAP"),
)
