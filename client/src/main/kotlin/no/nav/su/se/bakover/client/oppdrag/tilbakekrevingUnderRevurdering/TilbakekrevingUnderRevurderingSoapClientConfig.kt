package no.nav.su.se.bakover.client.oppdrag.tilbakekrevingUnderRevurdering

import common.infrastructure.cxf.wrapInStsClient
import no.nav.okonomi.tilbakekrevingservice.TilbakekrevingPortType
import no.nav.su.se.bakover.common.domain.config.ServiceUserConfig
import org.apache.cxf.jaxws.JaxWsProxyFactoryBean
import org.slf4j.LoggerFactory
import javax.xml.namespace.QName

/**
 * TODO jah: Alle filene i denne mappen slettes sammen med tilbakekreving under revurdering.
 */
class TilbakekrevingUnderRevurderingSoapClientConfig(
    private val tilbakekrevingServiceUrl: String,
    private val stsSoapUrl: String,
    private val disableCNCheck: Boolean,
    private val serviceUser: ServiceUserConfig,
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
            // features = listOf(LoggingFeature()) // Add LoggingFeature() to enable full logging of req/resp
        }.wrapInStsClient(stsSoapUrl, serviceUser, disableCNCheck)
    }
}
