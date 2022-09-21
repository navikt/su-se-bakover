package no.nav.su.se.bakover.client.oppdrag.avstemming

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.client.oppdrag.avstemming.GrensesnittsavstemmingData.Detaljdata
import org.junit.jupiter.api.Test

internal class DetaljBuilderTest {

    companion object {
        val expectedOkMedVarsel = Detaljdata(
            detaljType = Detaljdata.Detaljtype.GODKJENT_MED_VARSEL,
            offnr = "12345678910",
            avleverendeTransaksjonNokkel = okMedVarselId.toString(),
            tidspunkt = "2020-03-02-00.00.00.000000",
        )

        val expectedAvvist = Detaljdata(
            detaljType = Detaljdata.Detaljtype.AVVIST,
            offnr = "12345678910",
            avleverendeTransaksjonNokkel = feildId.toString(),
            tidspunkt = "2020-03-01-00.00.00.000000",
        )

        val expectedManglerKvittering = Detaljdata(
            detaljType = Detaljdata.Detaljtype.MANGLENDE_KVITTERING,
            offnr = "12345678910",
            avleverendeTransaksjonNokkel = manglerKvitteringId.toString(),
            tidspunkt = "2020-03-02-00.00.00.000000",
        )
    }

    @Test
    fun `lager detalj for ok med varsel, avvist og manglende kvittering`() {
        DetaljBuilder(alleUtbetalinger()).build() shouldBe listOf(
            expectedOkMedVarsel,
            expectedAvvist,
            expectedManglerKvittering,
        )
    }
}
