package no.nav.su.se.bakover.client

import io.github.cdimascio.dotenv.dotenv
import no.nav.su.se.bakover.client.stubs.oppdrag.SimuleringStub
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringClient
import org.slf4j.LoggerFactory

data class SOAPClients(
    val simulering: SimuleringClient
)

object SOAPClientBuilder {
    private val env = dotenv {
        ignoreIfMissing = true
    }
    private val logger = LoggerFactory.getLogger(this::class.java)
    private fun simulering(
//        simuleringConfig: SimuleringConfig = SimuleringConfig(
//            simuleringServiceUrl = env["SIMULERING_URL"] ?: "",
//            stsSoapUrl = env["STS_URL_SOAP"] ?: "",
//            username = env["username"] ?: "username",
//            password = env["password"] ?: "password",
//            disableCNCheck = true
//        )
    ): SimuleringClient = SimuleringStub.also { logger.warn("********** Using stub for ${SimuleringClient::class.java} **********") }
    // TODO: Enable real client when we get the right credentials etc.
//    when (isLocalOrRunningTests()) {
//        true -> SimuleringStub.also { logger.warn("********** Using stub for ${SimuleringClient::class.java} **********") }
//        else -> SimuleringSoapClient(simuleringConfig.wrapWithSTSSimulerFpService(ExtensionManagerBus()))
//    }

    fun build(
        simulering: SimuleringClient = simulering()
    ): SOAPClients {
        return SOAPClients(simulering)
    }
}
