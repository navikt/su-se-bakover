package no.nav.su.se.bakover.client

import io.github.cdimascio.dotenv.dotenv
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
    private val env = dotenv {
        ignoreIfMissing = true
    }
    private val logger = LoggerFactory.getLogger(this::class.java)
    private fun simulering(
        simuleringConfig: SimuleringConfig = SimuleringConfig(
            simuleringServiceUrl = env["SIMULERING_URL"] ?: "",
            stsSoapUrl = env["STS_URL_SOAP"] ?: "",
            username = env["username"] ?: "username",
            password = env["password"] ?: "password",
            disableCNCheck = true
        )
    ): Simulering = when (isLocalOrRunningTests()) {
        true -> SimuleringStub.also { logger.warn("********** Using stub for ${Simulering::class.java} **********") }
        else -> SimuleringService(simuleringConfig.wrapWithSTSSimulerFpService(ExtensionManagerBus()))
    }

    fun build(
        simulering: Simulering = simulering()
    ): SOAPClients {
        return SOAPClients(simulering)
    }
}
