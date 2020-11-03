package no.nav.su.se.bakover.client.oppdrag.avstemming

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.mars
import no.nav.su.se.bakover.domain.oppdrag.Kvittering
import org.junit.jupiter.api.Test

internal class GrunnlagBuilderTest {
    @Test
    fun `summerer grunnlag for alle typer`() {
        val expected = AvstemmingDataRequest.Grunnlagdata(
            godkjentAntall = 2,
            godkjentBelop = 1600.0.toBigDecimal(),
            godkjentFortegn = AvstemmingDataRequest.Fortegn.TILLEGG,
            varselAntall = 1,
            varselBelop = 1400.0.toBigDecimal(),
            varselFortegn = AvstemmingDataRequest.Fortegn.TILLEGG,
            avvistAntall = 1,
            avvistBelop = 10000.0.toBigDecimal(),
            avvistFortegn = AvstemmingDataRequest.Fortegn.TILLEGG,
            manglerAntall = 1,
            manglerBelop = 5000.0.toBigDecimal(),
            manglerFortegn = AvstemmingDataRequest.Fortegn.TILLEGG
        )

        GrunnlagBuilder(alleUtbetalinger()).build() shouldBe expected
    }

    @Test
    fun `håndterer fravær av enkelte typer`() {
        val expected = expected().copy(
            godkjentAntall = 1,
            godkjentBelop = 1000.0.toBigDecimal(),
            godkjentFortegn = AvstemmingDataRequest.Fortegn.TILLEGG
        )

        GrunnlagBuilder(
            listOf(
                lagUtbetaling(
                    opprettet = 1.mars(2020),
                    status = Kvittering.Utbetalingsstatus.OK,
                    linjer = listOf(
                        lagUtbetalingLinje(1.mars(2020), 31.mars(2020), 1000.0),
                    )
                )
            )
        ).build() shouldBe expected
    }

    @Test
    fun `setter riktig fortegn`() {
        val expected = expected().copy(
            godkjentAntall = 1,
            godkjentBelop = 1000.0.toBigDecimal(),
            godkjentFortegn = AvstemmingDataRequest.Fortegn.TILLEGG,
            avvistAntall = 1,
            avvistBelop = (-1000.0).toBigDecimal(),
            avvistFortegn = AvstemmingDataRequest.Fortegn.FRADRAG
        )

        GrunnlagBuilder(
            listOf(
                lagUtbetaling(
                    opprettet = 1.mars(2020),
                    status = Kvittering.Utbetalingsstatus.OK,
                    linjer = listOf(
                        lagUtbetalingLinje(1.mars(2020), 31.mars(2020), 1000.0),
                    )
                ),
                lagUtbetaling(
                    opprettet = 1.mars(2020),
                    status = Kvittering.Utbetalingsstatus.FEIL,
                    linjer = listOf(
                        lagUtbetalingLinje(1.mars(2020), 31.mars(2020), -1000.0),
                    )
                )
            )
        ).build() shouldBe expected
    }

    private fun expected() = AvstemmingDataRequest.Grunnlagdata(
        godkjentAntall = 0,
        godkjentBelop = 0.0.toBigDecimal(),
        godkjentFortegn = AvstemmingDataRequest.Fortegn.TILLEGG,
        varselAntall = 0,
        varselBelop = 0.0.toBigDecimal(),
        varselFortegn = AvstemmingDataRequest.Fortegn.TILLEGG,
        avvistAntall = 0,
        avvistBelop = 0.0.toBigDecimal(),
        avvistFortegn = AvstemmingDataRequest.Fortegn.TILLEGG,
        manglerAntall = 0,
        manglerBelop = 0.0.toBigDecimal(),
        manglerFortegn = AvstemmingDataRequest.Fortegn.TILLEGG
    )
}
