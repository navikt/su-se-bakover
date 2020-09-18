package no.nav.su.se.bakover.client.oppdrag.avstemming

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.mars
import no.nav.su.se.bakover.domain.oppdrag.Kvittering
import org.junit.jupiter.api.Test
import java.math.BigDecimal

internal class GrunnlagBuilderTest {
    @Test
    fun `summerer grunnlag for alle typer`() {
        val expected = AvstemmingDataRequest.Grunnlagdata(
            godkjentAntall = 2,
            godkjentBelop = BigDecimal(1600),
            godkjentFortegn = AvstemmingDataRequest.Fortegn.TILLEGG,
            varselAntall = 1,
            varselBelop = BigDecimal(1400),
            varselFortegn = AvstemmingDataRequest.Fortegn.TILLEGG,
            avvistAntall = 1,
            avvistBelop = BigDecimal(10000),
            avvistFortegn = AvstemmingDataRequest.Fortegn.TILLEGG,
            manglerAntall = 1,
            manglerBelop = BigDecimal(5000),
            manglerFortegn = AvstemmingDataRequest.Fortegn.TILLEGG
        )

        GrunnlagBuilder(alleUtbetalinger()).build() shouldBe expected
    }

    @Test
    fun `håndterer fravær av enkelte typer`() {
        val expected = expected().copy(
            godkjentAntall = 1,
            godkjentBelop = BigDecimal(1000),
            godkjentFortegn = AvstemmingDataRequest.Fortegn.TILLEGG
        )

        GrunnlagBuilder(
            listOf(
                lagUtbetaling(
                    1.mars(2020),
                    Kvittering.Utbetalingsstatus.OK,
                    listOf(
                        lagUtbetalingLinje(1.mars(2020), 31.mars(2020), 1000),
                    )
                )
            )
        ).build() shouldBe expected
    }

    @Test
    fun `setter riktig fortegn`() {
        val expected = expected().copy(
            godkjentAntall = 1,
            godkjentBelop = BigDecimal(1000),
            godkjentFortegn = AvstemmingDataRequest.Fortegn.TILLEGG,
            avvistAntall = 1,
            avvistBelop = BigDecimal(-1000),
            avvistFortegn = AvstemmingDataRequest.Fortegn.FRADRAG
        )

        GrunnlagBuilder(
            listOf(
                lagUtbetaling(
                    1.mars(2020),
                    Kvittering.Utbetalingsstatus.OK,
                    listOf(
                        lagUtbetalingLinje(1.mars(2020), 31.mars(2020), 1000),
                    )
                ),
                lagUtbetaling(
                    1.mars(2020),
                    Kvittering.Utbetalingsstatus.FEIL,
                    listOf(
                        lagUtbetalingLinje(1.mars(2020), 31.mars(2020), -1000),
                    )
                )
            )
        ).build() shouldBe expected
    }

    private fun expected() = AvstemmingDataRequest.Grunnlagdata(
        godkjentAntall = 0,
        godkjentBelop = BigDecimal.ZERO,
        godkjentFortegn = AvstemmingDataRequest.Fortegn.TILLEGG,
        varselAntall = 0,
        varselBelop = BigDecimal.ZERO,
        varselFortegn = AvstemmingDataRequest.Fortegn.TILLEGG,
        avvistAntall = 0,
        avvistBelop = BigDecimal.ZERO,
        avvistFortegn = AvstemmingDataRequest.Fortegn.TILLEGG,
        manglerAntall = 0,
        manglerBelop = BigDecimal.ZERO,
        manglerFortegn = AvstemmingDataRequest.Fortegn.TILLEGG
    )
}
