package no.nav.su.se.bakover.client

import no.finn.unleash.DefaultUnleash
import no.finn.unleash.FakeUnleash
import no.finn.unleash.Unleash
import no.finn.unleash.strategy.Strategy
import no.finn.unleash.util.UnleashConfig
import no.nav.su.se.bakover.common.ApplicationConfig
import org.slf4j.LoggerFactory

object UnleashBuilder {

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
