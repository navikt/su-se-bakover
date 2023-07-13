package no.nav.su.se.bakover.client

import io.getunleash.DefaultUnleash
import io.getunleash.FakeUnleash
import io.getunleash.Unleash
import io.getunleash.strategy.Strategy
import io.getunleash.util.UnleashConfig
import no.nav.su.se.bakover.common.infrastructure.config.ApplicationConfig
import org.slf4j.LoggerFactory

data object UnleashBuilder {

    private val log = LoggerFactory.getLogger(this::class.java)

    fun build(applicationConfig: ApplicationConfig): Unleash = when (applicationConfig.runtimeEnvironment == ApplicationConfig.RuntimeEnvironment.Nais) {
        true -> {
            DefaultUnleash(
                UnleashConfig.builder()
                    .appName(applicationConfig.unleash.appName)
                    .instanceId(applicationConfig.unleash.appName)
                    .unleashAPI(applicationConfig.unleash.unleashUrl)
                    .build(),
                IsNotProdStrategy(applicationConfig.naisCluster == ApplicationConfig.NaisCluster.Prod),
            )
        }
        false -> {
            FakeUnleash().apply { enableAll() }.also {
                log.warn("********** Using stub for ${Unleash::class.java} **********")
            }
        }
    }
}

class IsNotProdStrategy(private val isProd: Boolean) : Strategy {
    override fun getName() = "isNotProd"

    override fun isEnabled(parameters: Map<String, String>) = !isProd
}
