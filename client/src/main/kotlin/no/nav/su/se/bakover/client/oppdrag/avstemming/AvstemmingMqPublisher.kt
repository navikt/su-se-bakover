package no.nav.su.se.bakover.client.oppdrag.avstemming

import arrow.core.Either
import no.nav.su.se.bakover.client.oppdrag.MqPublisher
import no.nav.su.se.bakover.client.oppdrag.avstemming.Aksjonsdata.AksjonType
import no.nav.su.se.bakover.client.oppdrag.avstemming.Aksjonsdata.AksjonType.AVSLUTT
import no.nav.su.se.bakover.client.oppdrag.avstemming.Aksjonsdata.AksjonType.START
import no.nav.su.se.bakover.client.oppdrag.avstemming.Aksjonsdata.AvstemmingType
import no.nav.su.se.bakover.client.oppdrag.avstemming.Aksjonsdata.KildeType
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemming
import no.nav.su.se.bakover.domain.oppdrag.avstemming.AvstemmingPublisher
import no.nav.su.se.bakover.domain.oppdrag.avstemming.AvstemmingPublisher.KunneIkkeSendeAvstemming

class AvstemmingMqPublisher(
    private val mqPublisher: MqPublisher
) : AvstemmingPublisher {

    override fun publish(
        avstemming: Avstemming
    ): Either<KunneIkkeSendeAvstemming, Avstemming> {

        val startXml = AvstemmingXmlMapper.map(AvstemmingStartRequest(lagAksjonsdata(START)))
        val dataXml = AvstemmingXmlMapper.map(AvstemmingDataBuilder(avstemming.utbetalinger).build())
        val stoppXml = AvstemmingXmlMapper.map(AvstemmingStoppRequest(lagAksjonsdata(AVSLUTT)))

        return mqPublisher.publish(startXml, dataXml, stoppXml)
            .mapLeft { KunneIkkeSendeAvstemming }
            .map { avstemming.copy(avstemmingXmlRequest = dataXml) }
    }

    private fun lagAksjonsdata(aksjonType: AksjonType) = Aksjonsdata(
        aksjonType = aksjonType,
        kildeType = KildeType.AVLEVERT,
        avstemmingType = AvstemmingType.GRENSESNITTAVSTEMMING
    )
}
