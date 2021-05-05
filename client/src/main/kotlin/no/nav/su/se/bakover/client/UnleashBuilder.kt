package no.nav.su.se.bakover.client

import no.finn.unleash.DefaultUnleash
import no.finn.unleash.FakeUnleash
import no.finn.unleash.Unleash
import no.finn.unleash.strategy.Strategy
import no.finn.unleash.util.UnleashConfig
import no.nav.su.se.bakover.common.ApplicationConfig

object UnleashBuilder {
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
            FakeUnleash().apply { enableAll() }
        }
    }
}

class IsNotProdStrategy(private val isProd: Boolean) : Strategy {
    override fun getName() = "isNotProd"

    override fun isEnabled(parameters: Map<String, String>) = !isProd
}
