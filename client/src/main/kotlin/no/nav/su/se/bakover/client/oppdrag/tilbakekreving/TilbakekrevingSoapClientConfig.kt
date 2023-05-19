package no.nav.su.se.bakover.client.oppdrag.tilbakekreving

import no.nav.okonomi.tilbakekrevingservice.TilbakekrevingPortType
import no.nav.su.se.bakover.client.oppdrag.sts.wrapInStsClient
import no.nav.su.se.bakover.common.infrastructure.config.ApplicationConfig
import org.apache.cxf.jaxws.JaxWsProxyFactoryBean
import org.apache.cxf.ws.addressing.WSAddressingFeature
import org.slf4j.LoggerFactory
import javax.xml.namespace.QName

class TilbakekrevingSoapClientConfig(
    private val tilbakekrevingServiceUrl: String,
    private val stsSoapUrl: String,
    private val disableCNCheck: Boolean,
    private val serviceUser: ApplicationConfig.ServiceUserConfig,
) {

    private val log = LoggerFactory.getLogger(this::class.java)

    private companion object {
        private const val WSDL = "wsdl/no/nav/tilbakekreving/tilbakekreving-v1-tjenestespesifikasjon.wsdl"
        private const val NAMESPACE = "http://okonomi.nav.no/tilbakekrevingService/"
        private val SERVICE = QName(NAMESPACE, "TilbakekrevingService")
        private val PORT = QName(NAMESPACE, "TilbakekrevingServicePort")
    }

    fun tilbakekrevingSoapService(): TilbakekrevingPortType {
        log.info("Using tilbakekrevingService url $tilbakekrevingServiceUrl")
        return JaxWsProxyFactoryBean().apply {
            address = tilbakekrevingServiceUrl
            wsdlURL = WSDL
            serviceName = SERVICE
            endpointName = PORT
            serviceClass = TilbakekrevingPortType::class.java
            features = listOf(WSAddressingFeature()) // Add LoggingFeature() to enable full logging of req/resp
        }.wrapInStsClient(stsSoapUrl, serviceUser, disableCNCheck)
    }
}
