package no.nav.su.se.bakover.client.oppdrag.avstemming

import arrow.core.Either
import no.nav.su.se.bakover.client.oppdrag.MqPublisher
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemming
import no.nav.su.se.bakover.domain.oppdrag.avstemming.AvstemmingPublisher
import no.nav.su.se.bakover.domain.oppdrag.avstemming.AvstemmingPublisher.KunneIkkeSendeAvstemming

class AvstemmingMqPublisher(
    private val mqPublisher: MqPublisher,
) : AvstemmingPublisher {

    override fun publish(
        grensesnittavstemming: Avstemming.Grensesnittavstemming,
    ): Either<KunneIkkeSendeAvstemming, Avstemming.Grensesnittavstemming> {
        val avstemmingsdata = GrensesnittavstemmingDataBuilder(grensesnittavstemming).build()
        val startXml = avstemmingsdata.startXml()
        val dataXml = avstemmingsdata.dataXml()
        val stoppXml = avstemmingsdata.avsluttXml()

        return mqPublisher.publish(startXml, dataXml, stoppXml)
            .mapLeft { KunneIkkeSendeAvstemming }
            .map { grensesnittavstemming.copy(avstemmingXmlRequest = dataXml) }
    }

    override fun publish(
        konsistensavstemming: Avstemming.Konsistensavstemming,
    ): Either<KunneIkkeSendeAvstemming, Avstemming.Konsistensavstemming> {
        val avstemmingsdata = KonsistensavstemmingDataBuilder(konsistensavstemming).build()
        val startXml = avstemmingsdata.startXml()
        val dataXml = avstemmingsdata.dataXml()
        val stoppXml = avstemmingsdata.avsluttXml()

        return mqPublisher.publish(startXml, dataXml, stoppXml)
            .mapLeft { KunneIkkeSendeAvstemming }
            .map { konsistensavstemming.copy(avstemmingXmlRequest = dataXml) }
    }
}
