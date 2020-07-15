package no.nav.su.se.bakover.client

import no.nav.su.se.bakover.client.oppdrag.Oppdrag
import no.nav.su.se.bakover.client.oppdrag.simulering.SimuleringConfig
import no.nav.su.se.bakover.client.oppdrag.simulering.SimuleringService
import no.nav.su.se.bakover.client.stubs.oppdrag.OppdragStub
import no.nav.su.se.bakover.common.isLocalOrRunningTests
import org.apache.cxf.bus.extension.ExtensionManagerBus
import org.slf4j.LoggerFactory

data class SOAPClients(
    val oppdrag: Oppdrag
)

object SOAPClientBuilder {
    private val env = System.getenv()
    private val logger = LoggerFactory.getLogger(this::class.java)
    private fun oppdrag(
        simuleringConfig: SimuleringConfig = SimuleringConfig(
            simuleringServiceUrl = env.getOrDefault("SIMULERING_URL", ""),
            stsSoapUrl = env.getOrDefault("STS_URL_SOAP", ""),
            username = env.getOrDefault("username", "username"),
            password = env.getOrDefault("password", "password"),
            disableCNCheck = true
        )
    ): Oppdrag = when (env.isLocalOrRunningTests()) {
        true -> OppdragStub.also { logger.warn("********** Using stub for ${Oppdrag::class.java} **********") }
        else -> SimuleringService(simuleringConfig.wrapWithSTSSimulerFpService(ExtensionManagerBus()))
    }

    fun build(
        oppdrag: Oppdrag = oppdrag()
    ): SOAPClients {
        return SOAPClients(oppdrag)
    }
}
