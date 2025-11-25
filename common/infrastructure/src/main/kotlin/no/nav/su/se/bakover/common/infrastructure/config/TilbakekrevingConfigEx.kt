package no.nav.su.se.bakover.common.infrastructure.config

import no.nav.su.se.bakover.common.domain.config.ServiceUserConfig
import no.nav.su.se.bakover.common.domain.config.TilbakekrevingConfig

fun TilbakekrevingConfig.Companion.createFromEnvironmentVariables() = TilbakekrevingConfig(
    mq = TilbakekrevingConfig.Mq.createFromEnvironmentVariables(),
    soap = TilbakekrevingConfig.Soap.createFromEnvironmentVariables(),
    serviceUserConfig = ServiceUserConfig.createFromEnvironmentVariables(isGCP()),
)

fun TilbakekrevingConfig.Mq.Companion.createFromEnvironmentVariables() = TilbakekrevingConfig.Mq(
    mottak = EnvironmentConfig.getEnvironmentVariableOrThrow("MQ_TILBAKEKREVING_MOTTAK"),
)

// TODO: SOS Denne vil ikke fungere i GCP - må gå via ny proxy før vi kan bruke TK i GCP
fun TilbakekrevingConfig.Soap.Companion.createFromEnvironmentVariables() = TilbakekrevingConfig.Soap(
    url = EnvironmentConfig.getEnvironmentVariableOrThrow("TILBAKEKREVING_URL"),
    stsSoapUrl = EnvironmentConfig.getEnvironmentVariableOrThrow("STS_URL_SOAP"),
)
