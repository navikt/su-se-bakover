package no.nav.su.se.bakover.client.oppdrag.simulering

import no.nav.su.se.bakover.client.oppdrag.sts.wrapInStsClient
import no.nav.su.se.bakover.common.ApplicationConfig
import no.nav.system.os.eksponering.simulerfpservicewsbinding.SimulerFpService
import org.apache.cxf.jaxws.JaxWsProxyFactoryBean
import org.apache.cxf.transport.http.HttpConduitConfig
import org.apache.cxf.transport.http.HttpConduitFeature
import org.apache.cxf.transports.http.configuration.HTTPClientPolicy
import org.apache.cxf.ws.addressing.WSAddressingFeature
import org.slf4j.LoggerFactory
import javax.xml.namespace.QName

class SimuleringConfig(
    private val simuleringServiceUrl: String,
    private val stsSoapUrl: String,
    private val disableCNCheck: Boolean,
    private val serviceUser: ApplicationConfig.ServiceUserConfig,
) {
    private val log = LoggerFactory.getLogger(this::class.java)

    private companion object {
        private const val WSDL = "wsdl/no/nav/system/os/eksponering/simulerfpservicewsbinding.wsdl"
        private const val NAMESPACE = "http://nav.no/system/os/eksponering/simulerFpServiceWSBinding"
        private val SERVICE = QName(NAMESPACE, "simulerFpService")
        private val PORT = QName(NAMESPACE, "simulerFpServicePort")
    }

    fun wrapWithSTSSimulerFpService(): SimulerFpService {
        log.info("Using simuleringservice url $simuleringServiceUrl")
        return JaxWsProxyFactoryBean().apply {
            address = simuleringServiceUrl
            wsdlURL = WSDL
            serviceName = SERVICE
            endpointName = PORT
            serviceClass = SimulerFpService::class.java
            features = listOf(
                HttpConduitFeature().apply {
                    HttpConduitConfig().apply {
                        clientPolicy = HTTPClientPolicy().apply {
                            connectionTimeout = 2000
                            receiveTimeout = 5000
                        }
                    }
                },
                WSAddressingFeature(), // Add LoggingFeature() to enable full logging of req/resp
            )
        }.wrapInStsClient(stsSoapUrl, serviceUser, disableCNCheck)
    }
}
