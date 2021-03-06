package no.nav.su.se.bakover.client.oppdrag.avstemming

import arrow.core.Either
import no.nav.su.se.bakover.client.oppdrag.MqPublisher
import no.nav.su.se.bakover.client.oppdrag.XmlMapper
import no.nav.su.se.bakover.client.oppdrag.avstemming.Aksjonsdata.AksjonType.AVSLUTT
import no.nav.su.se.bakover.client.oppdrag.avstemming.Aksjonsdata.AksjonType.START
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemming
import no.nav.su.se.bakover.domain.oppdrag.avstemming.AvstemmingPublisher
import no.nav.su.se.bakover.domain.oppdrag.avstemming.AvstemmingPublisher.KunneIkkeSendeAvstemming

class AvstemmingMqPublisher(
    private val mqPublisher: MqPublisher
) : AvstemmingPublisher {

    override fun publish(
        avstemming: Avstemming
    ): Either<KunneIkkeSendeAvstemming, Avstemming> {

        val avstemmingsdata = AvstemmingDataBuilder(avstemming).build()
        val startXml = XmlMapper.writeValueAsString(AvstemmingStartRequest(avstemmingsdata.aksjon.copy(aksjonType = START)))
        val dataXml = XmlMapper.writeValueAsString(avstemmingsdata)
        val stoppXml = XmlMapper.writeValueAsString(AvstemmingStoppRequest(avstemmingsdata.aksjon.copy(aksjonType = AVSLUTT)))

        return mqPublisher.publish(startXml, dataXml, stoppXml)
            .mapLeft { KunneIkkeSendeAvstemming }
            .map { avstemming.copy(avstemmingXmlRequest = dataXml) }
    }
}
