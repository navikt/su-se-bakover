package no.nav.su.se.bakover.client

import no.nav.su.se.bakover.client.oppdrag.Simulering
import no.nav.su.se.bakover.client.oppdrag.simulering.SimuleringConfig
import no.nav.su.se.bakover.client.oppdrag.simulering.SimuleringService
import no.nav.su.se.bakover.client.stubs.oppdrag.SimuleringStub
import no.nav.su.se.bakover.common.isLocalOrRunningTests
import org.apache.cxf.bus.extension.ExtensionManagerBus
import org.slf4j.LoggerFactory

data class SOAPClients(
    val simulering: Simulering
)

object SOAPClientBuilder {
    private val env = System.getenv()
    private val logger = LoggerFactory.getLogger(this::class.java)
    private fun simulering(
        simuleringConfig: SimuleringConfig = SimuleringConfig(
            simuleringServiceUrl = env.getOrDefault("SIMULERING_URL", ""),
            stsSoapUrl = env.getOrDefault("STS_URL_SOAP", ""),
            username = env.getOrDefault("username", "username"),
            password = env.getOrDefault("password", "password"),
            disableCNCheck = true
        )
    ): Simulering = when (env.isLocalOrRunningTests()) {
        true -> SimuleringStub.also { logger.warn("********** Using stub for ${Simulering::class.java} **********") }
        else -> SimuleringService(simuleringConfig.wrapWithSTSSimulerFpService(ExtensionManagerBus()))
    }

    fun build(
        simulering: Simulering = simulering()
    ): SOAPClients {
        return SOAPClients(simulering)
    }
}
