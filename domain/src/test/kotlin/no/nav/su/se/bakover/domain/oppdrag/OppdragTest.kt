package no.nav.su.se.bakover.domain.oppdrag

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.april
import no.nav.su.se.bakover.common.august
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.juni
import no.nav.su.se.bakover.common.mai
import no.nav.su.se.bakover.common.september
import no.nav.su.se.bakover.common.toTidspunkt
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.beregning.Sats
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Month
import java.util.UUID

internal class OppdragTest {
    private val sakId = UUID.randomUUID()
    private lateinit var oppdrag: Oppdrag
    private val fnr = Fnr("12345678910")

    @BeforeEach
    fun beforeEach() {
        oppdrag = Oppdrag(
            id = UUID30.randomUUID(),
            opprettet = Tidspunkt.EPOCH,
            sakId = sakId
        )
    }

    @Test
    fun `ingen eksisterende utbetalinger`() {
        val actual = oppdrag.genererUtbetaling(
            utbetalingsperioder = listOf(
                Utbetalingsperiode(
                    fraOgMed = 1.januar(2020),
                    tilOgMed = 31.desember(2020),
                    beløp = 5600,
                )
            ),
            fnr
        )

        val first = actual.utbetalingslinjer.first()
        actual shouldBe expectedUtbetaling(
            actual,
            listOf(
                expectedUtbetalingslinje(
                    utbetalingslinjeId = first.id,
                    opprettet = first.opprettet,
                    fraOgMed = 1.januar(2020),
                    tilOgMed = 31.desember(2020),
                    beløp = 5600,
                    forrigeUtbetalingslinjeId = null
                )
            )
        )
    }

    @Test
    fun `nye utbetalingslinjer skal refere til forutgående utbetalingslinjer`() {
        val forrigeUtbetalingslinjeId = UUID30.randomUUID()

        val eksisterendeOppdrag = Oppdrag(
            id = UUID30.randomUUID(),
            opprettet = Tidspunkt.EPOCH,
            sakId = oppdrag.sakId,
            utbetalinger = mutableListOf(
                Utbetaling(
                    kvittering = Kvittering(Kvittering.Utbetalingsstatus.OK, ""),
                    oppdragsmelding = Oppdragsmelding(Oppdragsmelding.Oppdragsmeldingstatus.SENDT, ""),
                    utbetalingslinjer = listOf(
                        Utbetalingslinje(
                            id = forrigeUtbetalingslinjeId,
                            opprettet = Tidspunkt.MIN,
                            fraOgMed = 1.januar(2018),
                            tilOgMed = 1.desember(2018),
                            forrigeUtbetalingslinjeId = null,
                            beløp = 5000
                        )
                    ),
                    fnr = fnr
                )
            )
        )

        val nyUtbetaling = eksisterendeOppdrag.genererUtbetaling(
            utbetalingsperioder = listOf(
                Utbetalingsperiode(
                    fraOgMed = 1.januar(2020),
                    tilOgMed = 31.mai(2020),
                    beløp = 5600,
                ),
                Utbetalingsperiode(
                    fraOgMed = 1.juni(2020),
                    tilOgMed = 31.august(2020),
                    beløp = 5700,
                ),
                Utbetalingsperiode(
                    fraOgMed = 1.september(2020),
                    tilOgMed = 31.desember(2020),
                    beløp = 5800,
                )
            ),
            fnr
        )

        nyUtbetaling shouldBe Utbetaling(
            id = nyUtbetaling.id,
            opprettet = nyUtbetaling.opprettet,
            simulering = null,
            utbetalingslinjer = listOf(
                expectedUtbetalingslinje(
                    utbetalingslinjeId = nyUtbetaling.utbetalingslinjer[0].id,
                    opprettet = nyUtbetaling.utbetalingslinjer[0].opprettet,
                    fraOgMed = 1.januar(2020),
                    tilOgMed = 31.mai(2020),
                    beløp = 5600,
                    forrigeUtbetalingslinjeId = forrigeUtbetalingslinjeId
                ),
                expectedUtbetalingslinje(
                    utbetalingslinjeId = nyUtbetaling.utbetalingslinjer[1].id,
                    opprettet = nyUtbetaling.utbetalingslinjer[1].opprettet,
                    fraOgMed = 1.juni(2020),
                    tilOgMed = 31.august(2020),
                    beløp = 5700,
                    forrigeUtbetalingslinjeId = nyUtbetaling.utbetalingslinjer[0].id
                ),
                expectedUtbetalingslinje(
                    utbetalingslinjeId = nyUtbetaling.utbetalingslinjer[2].id,
                    opprettet = nyUtbetaling.utbetalingslinjer[2].opprettet,
                    fraOgMed = 1.september(2020),
                    tilOgMed = 31.desember(2020),
                    beløp = 5800,
                    forrigeUtbetalingslinjeId = nyUtbetaling.utbetalingslinjer[1].id
                )
            ),
            fnr = fnr
        )
    }

    @Test
    fun `tar utgangspunkt i nyeste utbetalte ved opprettelse av nye utbetalinger`() {
        val first = Utbetaling(
            opprettet = LocalDate.of(2020, Month.JANUARY, 1).atStartOfDay().toTidspunkt(),
            oppdragsmelding = Oppdragsmelding(Oppdragsmelding.Oppdragsmeldingstatus.SENDT, ""),
            kvittering = Kvittering(Kvittering.Utbetalingsstatus.OK, ""),
            utbetalingslinjer = emptyList(),
            fnr = fnr
        )

        val second = Utbetaling(
            opprettet = LocalDate.of(2020, Month.FEBRUARY, 1).atStartOfDay().toTidspunkt(),
            oppdragsmelding = Oppdragsmelding(Oppdragsmelding.Oppdragsmeldingstatus.SENDT, ""),
            kvittering = Kvittering(Kvittering.Utbetalingsstatus.FEIL, ""),
            utbetalingslinjer = emptyList(),
            fnr = fnr
        )

        val third = Utbetaling(
            opprettet = LocalDate.of(2020, Month.MARCH, 1).atStartOfDay().toTidspunkt(),
            oppdragsmelding = Oppdragsmelding(Oppdragsmelding.Oppdragsmeldingstatus.SENDT, ""),
            kvittering = Kvittering(Kvittering.Utbetalingsstatus.OK_MED_VARSEL, ""),
            utbetalingslinjer = emptyList(),
            fnr = fnr
        )
        val fourth = Utbetaling(
            opprettet = LocalDate.of(2020, Month.JULY, 1).atStartOfDay().toTidspunkt(),
            oppdragsmelding = Oppdragsmelding(Oppdragsmelding.Oppdragsmeldingstatus.SENDT, ""),
            kvittering = Kvittering(Kvittering.Utbetalingsstatus.FEIL, ""),
            utbetalingslinjer = emptyList(),
            fnr = fnr
        )
        val fifth = Utbetaling(
            opprettet = LocalDate.of(2020, Month.JULY, 1).atStartOfDay().toTidspunkt(),
            oppdragsmelding = Oppdragsmelding(Oppdragsmelding.Oppdragsmeldingstatus.FEIL, ""),
            kvittering = null,
            utbetalingslinjer = emptyList(),
            fnr = fnr
        )

        val oppdrag = Oppdrag(
            id = UUID30.randomUUID(),
            opprettet = Tidspunkt.EPOCH,
            sakId = sakId,
            utbetalinger = mutableListOf(first, second, third, fourth, fifth)
        )
        oppdrag.sisteOversendteUtbetaling() shouldBe third
    }

    @Test
    fun `konverterer beregning til utbetalingsperioder`() {
        val opprettet = LocalDateTime.of(2020, Month.JANUARY, 1, 12, 1, 1).toTidspunkt()
        val b = Beregning(
            fraOgMed = 1.januar(2020),
            tilOgMed = 31.desember(2020),
            sats = Sats.HØY,
            opprettet = opprettet,
            fradrag = emptyList(),
            forventetInntekt = 0
        )

        val actualUtbetaling = oppdrag.genererUtbetaling(b, fnr)
        actualUtbetaling shouldBe Utbetaling(
            opprettet = actualUtbetaling.opprettet,
            kvittering = null,
            utbetalingslinjer = listOf(
                Utbetalingslinje(
                    id = actualUtbetaling.utbetalingslinjer[0].id,
                    opprettet = actualUtbetaling.utbetalingslinjer[0].opprettet,
                    fraOgMed = 1.januar(2020),
                    tilOgMed = 30.april(2020),
                    forrigeUtbetalingslinjeId = null,
                    beløp = 20637
                ),
                Utbetalingslinje(
                    id = actualUtbetaling.utbetalingslinjer[1].id,
                    opprettet = actualUtbetaling.utbetalingslinjer[1].opprettet,
                    fraOgMed = 1.mai(2020),
                    tilOgMed = 31.desember(2020),
                    forrigeUtbetalingslinjeId = actualUtbetaling.utbetalingslinjer[0].id,
                    beløp = 20946
                )
            ),
            fnr = fnr,
            id = actualUtbetaling.id,
            simulering = null,
            oppdragsmelding = null,
            avstemmingId = null
        )
    }

    private fun expectedUtbetaling(actual: Utbetaling, oppdragslinjer: List<Utbetalingslinje>): Utbetaling {
        return Utbetaling(
            id = actual.id,
            opprettet = actual.opprettet,
            simulering = null,
            utbetalingslinjer = oppdragslinjer,
            fnr = fnr,
        )
    }

    private fun expectedUtbetalingslinje(
        utbetalingslinjeId: UUID30,
        opprettet: Tidspunkt,
        fraOgMed: LocalDate,
        tilOgMed: LocalDate,
        beløp: Int,
        forrigeUtbetalingslinjeId: UUID30?
    ): Utbetalingslinje {
        return Utbetalingslinje(
            id = utbetalingslinjeId,
            opprettet = opprettet,
            fraOgMed = fraOgMed,
            tilOgMed = tilOgMed,
            forrigeUtbetalingslinjeId = forrigeUtbetalingslinjeId,
            beløp = beløp
        )
    }
}
