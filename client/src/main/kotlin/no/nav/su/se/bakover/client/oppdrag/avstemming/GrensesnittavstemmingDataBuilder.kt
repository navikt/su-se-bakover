package no.nav.su.se.bakover.client.oppdrag.avstemming

import no.nav.su.se.bakover.client.oppdrag.toAvstemmingsdatoFormat
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemming
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemmingsnøkkel

internal class GrensesnittavstemmingDataBuilder(
    private val avstemming: Avstemming.Grensesnittavstemming,
) {
    private val utbetalinger = avstemming.utbetalinger

    fun build(): GrensesnittsavstemmingData {
        return GrensesnittsavstemmingData(
            aksjon = Aksjonsdata.Grensesnittsavstemming(
                nokkelFom = Avstemmingsnøkkel(avstemming.fraOgMed).toString(),
                nokkelTom = Avstemmingsnøkkel(avstemming.tilOgMed).toString(),
                avleverendeAvstemmingId = avstemming.id.toString(),
                underkomponentKode = avstemming.fagområde.toString(),
            ),
            total = Totaldata(
                totalAntall = utbetalinger.size,
                totalBelop = utbetalinger.flatMap { it.utbetalingslinjer }.sumOf { it.beløp }.toBigDecimal(),
                fortegn = Fortegn.TILLEGG,
            ),
            periode = Periodedata(
                datoAvstemtFom = avstemming.fraOgMed.toAvstemmingsdatoFormat(),
                datoAvstemtTom = avstemming.tilOgMed.toAvstemmingsdatoFormat(),
            ),
            grunnlag = GrunnlagBuilder(utbetalinger).build(),
            detalj = DetaljBuilder(utbetalinger).build(),
        ).also {
            it.sanityCheck()
        }
    }

    private fun GrensesnittsavstemmingData.sanityCheck() {
        check(total.totalAntall == grunnlag.totaltAntall()) { "Totaldata og grunnlag er uenige om totalt antall utbetalinger!" }
        check(grunnlag.avvistAntall + grunnlag.manglerAntall + grunnlag.varselAntall == detalj.size) { "Det skal eksistere detaljer for alle utbetalinger med avvist/mangler/varsel" }
    }
}
