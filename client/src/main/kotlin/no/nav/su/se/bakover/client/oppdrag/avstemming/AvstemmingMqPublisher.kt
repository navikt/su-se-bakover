package no.nav.su.se.bakover.client.oppdrag.avstemming

import arrow.core.Either
import arrow.core.Tuple4
import no.nav.su.se.bakover.client.oppdrag.MqPublisher
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemming
import no.nav.su.se.bakover.domain.oppdrag.avstemming.AvstemmingPublisher
import no.nav.su.se.bakover.domain.oppdrag.avstemming.AvstemmingPublisher.KunneIkkeSendeAvstemming

internal class AvstemmingMqPublisher(
    private val mqPublisher: MqPublisher,
) : AvstemmingPublisher {

    override fun publish(
        grensesnittavstemming: Avstemming.Grensesnittavstemming,
    ): Either<KunneIkkeSendeAvstemming, Avstemming.Grensesnittavstemming> {
        val xml = lagGrensesnittavstemmingXml(grensesnittavstemming)
        return mqPublisher.publish(xml.first, xml.second, xml.third)
            .mapLeft { KunneIkkeSendeAvstemming }
            .map { grensesnittavstemming.copy(avstemmingXmlRequest = xml.toString()) }
    }

    override fun publish(
        konsistensavstemming: Avstemming.Konsistensavstemming.Ny,
    ): Either<KunneIkkeSendeAvstemming, Avstemming.Konsistensavstemming.Ny> {
        val xml = lagKonsistensavstemmingXml(konsistensavstemming)
        return mqPublisher.publish(xml.first, *xml.second.toTypedArray(), xml.third, xml.fourth)
            .mapLeft { KunneIkkeSendeAvstemming }
            .map { konsistensavstemming.copy(avstemmingXmlRequest = xml.toString()) }
    }
}

internal fun lagGrensesnittavstemmingXml(grensesnittavstemming: Avstemming.Grensesnittavstemming): Triple<String, String, String> {
    val avstemmingsdata = GrensesnittavstemmingDataBuilder(grensesnittavstemming).build()
    val startXml = avstemmingsdata.startXml()
    val dataXml = avstemmingsdata.dataXml()
    val stoppXml = avstemmingsdata.avsluttXml()
    return Triple(startXml, dataXml, stoppXml)
}

internal fun lagKonsistensavstemmingXml(konsistensavstemming: Avstemming.Konsistensavstemming.Ny): Tuple4<String, List<String>, String, String> {
    val requestbuilder = KonsistensavstemmingRequestBuilder(konsistensavstemming)
    return Tuple4(
        first = requestbuilder.startXml(),
        second = requestbuilder.dataXml(),
        third = requestbuilder.totaldataXml(),
        fourth = requestbuilder.avsluttXml(),
    )
}
