package no.nav.su.se.bakover.client.oppdrag.simulering

import no.nav.su.se.bakover.common.ApplicationConfig
import no.nav.system.os.eksponering.simulerfpservicewsbinding.SimulerFpService
import org.apache.cxf.Bus
import org.apache.cxf.binding.soap.Soap12
import org.apache.cxf.binding.soap.SoapMessage
import org.apache.cxf.bus.extension.ExtensionManagerBus
import org.apache.cxf.configuration.jsse.TLSClientParameters
import org.apache.cxf.endpoint.Client
import org.apache.cxf.frontend.ClientProxy
import org.apache.cxf.jaxws.JaxWsProxyFactoryBean
import org.apache.cxf.transport.http.HTTPConduit
import org.apache.cxf.ws.addressing.WSAddressingFeature
import org.apache.cxf.ws.policy.PolicyBuilder
import org.apache.cxf.ws.policy.PolicyEngine
import org.apache.cxf.ws.policy.attachment.reference.RemoteReferenceResolver
import org.apache.cxf.ws.security.SecurityConstants
import org.apache.cxf.ws.security.trust.STSClient
import org.apache.neethi.Policy
import org.slf4j.LoggerFactory
import javax.xml.namespace.QName

class SimuleringConfig(
    private val simuleringServiceUrl: String,
    private val stsSoapUrl: String,
    private val disableCNCheck: Boolean,
    private val serviceUser: ApplicationConfig.ServiceUserConfig
) {

    private val log = LoggerFactory.getLogger(this::class.java)

    private companion object {
        private const val WSDL = "wsdl/no/nav/system/os/eksponering/simulerfpservicewsbinding.wsdl"
        private const val NAMESPACE = "http://nav.no/system/os/eksponering/simulerFpServiceWSBinding"
        private val SERVICE = QName(NAMESPACE, "simulerFpService")
        private val PORT = QName(NAMESPACE, "simulerFpServicePort")
        private const val STS_CLIENT_AUTHENTICATION_POLICY = "classpath:untPolicy.xml"
        private const val STS_SAML_POLICY = "classpath:requestSamlPolicy.xml"
    }

    fun wrapWithSTSSimulerFpService(bus: Bus = ExtensionManagerBus()): SimulerFpService {
        log.info("using simuleringservice url $simuleringServiceUrl")
        val factory = JaxWsProxyFactoryBean().apply {
            address = simuleringServiceUrl
            wsdlURL = WSDL
            serviceName = SERVICE
            endpointName = PORT
            serviceClass = SimulerFpService::class.java
            features = listOf(WSAddressingFeature()) // Add LoggingFeature() to enable full logging of req/resp
        }
        return factory.create(SimulerFpService::class.java).apply {
            val sts = STSClient(bus).apply {
                isEnableAppliesTo = false
                isAllowRenewing = false

                location = stsSoapUrl
                properties = mapOf(
                    SecurityConstants.USERNAME to serviceUser.username,
                    SecurityConstants.PASSWORD to serviceUser.password
                )
                setPolicy(bus.resolvePolicy(STS_CLIENT_AUTHENTICATION_POLICY))
            }
            ClientProxy.getClient(this).apply {
                requestContext[SecurityConstants.STS_CLIENT] = sts
                requestContext[SecurityConstants.CACHE_ISSUED_TOKEN_IN_ENDPOINT] = true
                setClientEndpointPolicy(bus.resolvePolicy(STS_SAML_POLICY))
                if (disableCNCheck) {
                    val conduit = conduit as HTTPConduit
                    conduit.tlsClientParameters = TLSClientParameters().apply {
                        isDisableCNCheck = true
                    }
                }
            }
        }
    }

    private fun Bus.resolvePolicy(policyUri: String): Policy {
        val registry = getExtension(PolicyEngine::class.java).registry
        val resolved = registry.lookup(policyUri)

        val policyBuilder = getExtension(PolicyBuilder::class.java)
        val referenceResolver = RemoteReferenceResolver("", policyBuilder)

        return resolved ?: referenceResolver.resolveReference(policyUri)
    }

    private fun Client.setClientEndpointPolicy(policy: Policy) {
        val policyEngine: PolicyEngine = bus.getExtension(PolicyEngine::class.java)
        val message = SoapMessage(Soap12.getInstance())
        val endpointPolicy = policyEngine.getClientEndpointPolicy(endpoint.endpointInfo, null, message)
        policyEngine.setClientEndpointPolicy(endpoint.endpointInfo, endpointPolicy.updatePolicy(policy, message))
    }
}
