package tilbakekreving.infrastructure.client

import common.infrastructure.cxf.wrapInStsClient
import no.nav.okonomi.tilbakekrevingservice.TilbakekrevingPortType
import no.nav.su.se.bakover.common.domain.config.TilbakekrevingConfig
import org.apache.cxf.jaxws.JaxWsProxyFactoryBean
import org.slf4j.LoggerFactory
import javax.xml.namespace.QName

class TilbakekrevingSoapClientConfig(
    private val tilbakekrevingConfig: TilbakekrevingConfig,
) {

    private val log = LoggerFactory.getLogger(this::class.java)

    private companion object {
        private const val WSDL = "wsdl/no/nav/tilbakekreving/tilbakekreving-v1-tjenestespesifikasjon.wsdl"
        private const val NAMESPACE = "http://okonomi.nav.no/tilbakekrevingService/"
        private val SERVICE = QName(NAMESPACE, "TilbakekrevingService")
        private val PORT = QName(NAMESPACE, "TilbakekrevingServicePort")
    }

    fun tilbakekrevingSoapService(): TilbakekrevingPortType {
        log.info("Using tilbakekrevingService url ${tilbakekrevingConfig.soap.url} and sts url ${tilbakekrevingConfig.soap.stsSoapUrl}")
        return JaxWsProxyFactoryBean().apply {
            address = tilbakekrevingConfig.soap.url
            wsdlURL = WSDL
            serviceName = SERVICE
            endpointName = PORT
            serviceClass = TilbakekrevingPortType::class.java
            // features = listOf(LoggingFeature()) // Add LoggingFeature() to enable full logging of req/resp
        }.wrapInStsClient(tilbakekrevingConfig.soap.stsSoapUrl, tilbakekrevingConfig.serviceUserConfig, true)
    }
}
