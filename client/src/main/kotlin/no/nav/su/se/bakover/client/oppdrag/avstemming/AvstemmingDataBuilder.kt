package no.nav.su.se.bakover.client.oppdrag.avstemming

import no.nav.su.se.bakover.client.oppdrag.toAvstemmingsdatoFormat
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemming
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemmingsnøkkel

class AvstemmingDataBuilder(
    val avstemming: Avstemming
) {
    val utbetalinger = avstemming.utbetalinger
    fun build(): AvstemmingDataRequest {

        return AvstemmingDataRequest(
            aksjon = Aksjonsdata(
                aksjonType = Aksjonsdata.AksjonType.DATA,
                kildeType = Aksjonsdata.KildeType.AVLEVERT,
                avstemmingType = Aksjonsdata.AvstemmingType.GRENSESNITTAVSTEMMING,
                nokkelFom = Avstemmingsnøkkel(avstemming.fraOgMed).toString(),
                nokkelTom = Avstemmingsnøkkel(avstemming.tilOgMed).toString(),
                avleverendeAvstemmingId = avstemming.id.toString()
            ),
            total = AvstemmingDataRequest.Totaldata(
                totalAntall = utbetalinger.size,
                totalBelop = utbetalinger.flatMap { it.utbetalingslinjer }.sumOf { it.beløp }.toBigDecimal(),
                fortegn = AvstemmingDataRequest.Fortegn.TILLEGG
            ),
            periode = AvstemmingDataRequest.Periodedata(
                datoAvstemtFom = avstemming.fraOgMed.toAvstemmingsdatoFormat(),
                datoAvstemtTom = avstemming.tilOgMed.toAvstemmingsdatoFormat()
            ),
            grunnlag = GrunnlagBuilder(utbetalinger).build(),
            detalj = DetaljBuilder(utbetalinger).build()
        ).also {
            it.sanityCheck()
        }
    }

    private fun AvstemmingDataRequest.sanityCheck() {
        check(this.total?.totalAntall == this.grunnlag.totaltAntall()) { "Totaldata og grunnlag er uenige om totalt antall utbetalinger!" }
        check(this.grunnlag.avvistAntall + this.grunnlag.manglerAntall + this.grunnlag.varselAntall == this.detalj.size) { "Det skal eksistere detaljer for alle utbetalinger med avvist/mangler/varsel" }
    }
}
