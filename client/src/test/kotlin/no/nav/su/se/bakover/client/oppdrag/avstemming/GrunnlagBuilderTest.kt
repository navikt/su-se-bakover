package no.nav.su.se.bakover.client.oppdrag.avstemming

import arrow.core.nonEmptyListOf
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.mars
import no.nav.su.se.bakover.common.periode.mars
import no.nav.su.se.bakover.domain.oppdrag.Kvittering
import no.nav.su.se.bakover.test.utbetalingslinje
import org.junit.jupiter.api.Test

internal class GrunnlagBuilderTest {
    @Test
    fun `summerer grunnlag for alle typer`() {
        val expected = GrensesnittsavstemmingData.Grunnlagdata(
            godkjentAntall = 2,
            godkjentBelop = 1600.toBigDecimal(),
            godkjentFortegn = Fortegn.TILLEGG,
            varselAntall = 1,
            varselBelop = 1400.toBigDecimal(),
            varselFortegn = Fortegn.TILLEGG,
            avvistAntall = 1,
            avvistBelop = 10000.toBigDecimal(),
            avvistFortegn = Fortegn.TILLEGG,
            manglerAntall = 1,
            manglerBelop = 5000.toBigDecimal(),
            manglerFortegn = Fortegn.TILLEGG
        )

        GrunnlagBuilder(alleUtbetalinger()).build() shouldBe expected
    }

    @Test
    fun `håndterer fravær av enkelte typer`() {
        val expected = expected().copy(
            godkjentAntall = 1,
            godkjentBelop = 1000.toBigDecimal(),
            godkjentFortegn = Fortegn.TILLEGG
        )

        GrunnlagBuilder(
            listOf(
                lagUtbetaling(
                    opprettet = 1.mars(2020),
                    status = Kvittering.Utbetalingsstatus.OK,
                    linjer = nonEmptyListOf(
                        utbetalingslinje(periode = mars(2020), beløp = 1000),
                    ),
                ),
            ),
        ).build() shouldBe expected
    }

    @Test
    fun `setter riktig fortegn`() {
        val expected = expected().copy(
            godkjentAntall = 1,
            godkjentBelop = 1000.toBigDecimal(),
            godkjentFortegn = Fortegn.TILLEGG,
            avvistAntall = 1,
            avvistBelop = (-1000).toBigDecimal(),
            avvistFortegn = Fortegn.FRADRAG
        )

        GrunnlagBuilder(
            listOf(
                lagUtbetaling(
                    opprettet = 1.mars(2020),
                    status = Kvittering.Utbetalingsstatus.OK,
                    linjer = nonEmptyListOf(
                        utbetalingslinje(periode = mars(2020), beløp = 1000),
                    ),
                ),
                lagUtbetaling(
                    opprettet = 1.mars(2020),
                    status = Kvittering.Utbetalingsstatus.FEIL,
                    linjer = nonEmptyListOf(
                        utbetalingslinje(periode = mars(2020), beløp = -1000, uføregrad = 100),
                    ),
                ),
            ),
        ).build() shouldBe expected
    }

    private fun expected() = GrensesnittsavstemmingData.Grunnlagdata(
        godkjentAntall = 0,
        godkjentBelop = 0.toBigDecimal(),
        godkjentFortegn = Fortegn.TILLEGG,
        varselAntall = 0,
        varselBelop = 0.toBigDecimal(),
        varselFortegn = Fortegn.TILLEGG,
        avvistAntall = 0,
        avvistBelop = 0.toBigDecimal(),
        avvistFortegn = Fortegn.TILLEGG,
        manglerAntall = 0,
        manglerBelop = 0.toBigDecimal(),
        manglerFortegn = Fortegn.TILLEGG
    )
}
