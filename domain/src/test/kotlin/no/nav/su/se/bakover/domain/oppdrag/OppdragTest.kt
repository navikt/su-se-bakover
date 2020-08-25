package no.nav.su.se.bakover.domain.oppdrag

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.august
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.juni
import no.nav.su.se.bakover.common.mai
import no.nav.su.se.bakover.common.september
import no.nav.su.se.bakover.domain.beregning.BeregningsPeriode
import no.nav.su.se.bakover.domain.beregning.Sats.HØY
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

internal class OppdragTest {
    private val sakId = UUID.randomUUID()
    val oppdrag = Oppdrag(
        sakId = sakId
    )
    private val behandlingId = UUID.randomUUID()
    private val fnr = "12345678910"

    @Test
    fun `no existing oppdrag`() {
        val actual = oppdrag.generererUtbetaling(
            behandlingId = behandlingId,
            beregningsperioder = listOf(
                BeregningsPeriode(
                    fom = 1.januar(2020),
                    tom = 31.desember(2020),
                    beløp = 5600,
                    sats = HØY
                )
            )
        )

        val first = actual.utbetalingslinjer.first()
        actual shouldBe expectedUtbetaling(
            actual,
            listOf(
                expectedUtbetalingslinje(
                    utbetalingslinjeId = first.id,
                    opprettet = first.opprettet,
                    fom = 1.januar(2020),
                    tom = 31.desember(2020),
                    beløp = 5600,
                    forrigeUtbetalingslinjeId = null
                )
            )
        )
    }

    @Test
    fun `nye oppdragslinjer skal refere til forutgående oppdragslinjer`() {
        val forrigeUtbetalingslinjeId = UUID30.randomUUID()

        val eksisterendeOppdrag = Oppdrag(
            sakId = oppdrag.sakId,
            utbetalinger = mutableListOf(
                Utbetaling(
                    oppdragId = oppdrag.id,
                    behandlingId = behandlingId,
                    utbetalingslinjer = listOf(
                        Utbetalingslinje(
                            id = forrigeUtbetalingslinjeId,
                            opprettet = Instant.MIN,
                            fom = 1.januar(2018),
                            tom = 1.desember(2018),
                            forrigeUtbetalingslinjeId = null,
                            beløp = 5000
                        )
                    )
                )
            )
        )
        val nyUtbetaling = eksisterendeOppdrag.generererUtbetaling(
            behandlingId = behandlingId,
            beregningsperioder = listOf(
                BeregningsPeriode(
                    fom = 1.januar(2020),
                    tom = 31.mai(2020),
                    beløp = 5600,
                    sats = HØY
                ),
                BeregningsPeriode(
                    fom = 1.juni(2020),
                    tom = 31.august(2020),
                    beløp = 5700,
                    sats = HØY
                ),
                BeregningsPeriode(
                    fom = 1.september(2020),
                    tom = 31.desember(2020),
                    beløp = 5800,
                    sats = HØY
                )
            )
        )

        nyUtbetaling shouldBe Utbetaling(
            id = nyUtbetaling.id,
            opprettet = nyUtbetaling.opprettet,
            oppdragId = nyUtbetaling.oppdragId,
            simulering = null,
            behandlingId = behandlingId,
            utbetalingslinjer = listOf(
                expectedUtbetalingslinje(
                    utbetalingslinjeId = nyUtbetaling.utbetalingslinjer[0].id,
                    opprettet = nyUtbetaling.sisteUtbetalingslinje().opprettet,
                    fom = 1.januar(2020),
                    tom = 31.mai(2020),
                    beløp = 5600,
                    forrigeUtbetalingslinjeId = forrigeUtbetalingslinjeId
                ),
                expectedUtbetalingslinje(
                    utbetalingslinjeId = nyUtbetaling.utbetalingslinjer[1].id,
                    opprettet = nyUtbetaling.sisteUtbetalingslinje().opprettet,
                    fom = 1.juni(2020),
                    tom = 31.august(2020),
                    beløp = 5700,
                    forrigeUtbetalingslinjeId = nyUtbetaling.utbetalingslinjer[0].id
                ),
                expectedUtbetalingslinje(
                    utbetalingslinjeId = nyUtbetaling.utbetalingslinjer[2].id,
                    opprettet = nyUtbetaling.sisteUtbetalingslinje().opprettet,
                    fom = 1.september(2020),
                    tom = 31.desember(2020),
                    beløp = 5800,
                    forrigeUtbetalingslinjeId = nyUtbetaling.utbetalingslinjer[1].id
                )
            )
        )
    }

    private fun expectedUtbetaling(actual: Utbetaling, oppdragslinjer: List<Utbetalingslinje>): Utbetaling {
        return Utbetaling(
            id = actual.id,
            opprettet = actual.opprettet,
            oppdragId = oppdrag.id,
            behandlingId = behandlingId,
            simulering = null,
            utbetalingslinjer = oppdragslinjer,
        )
    }

    private fun expectedUtbetalingslinje(
        utbetalingslinjeId: UUID30,
        opprettet: Instant,
        fom: LocalDate,
        tom: LocalDate,
        beløp: Int,
        forrigeUtbetalingslinjeId: UUID30?
    ): Utbetalingslinje {
        return Utbetalingslinje(
            id = utbetalingslinjeId,
            opprettet = opprettet,
            fom = fom,
            tom = tom,
            forrigeUtbetalingslinjeId = forrigeUtbetalingslinjeId,
            beløp = beløp
        )
    }
}
