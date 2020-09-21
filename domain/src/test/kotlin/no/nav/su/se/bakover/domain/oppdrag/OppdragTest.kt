package no.nav.su.se.bakover.domain.oppdrag

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.august
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.juni
import no.nav.su.se.bakover.common.mai
import no.nav.su.se.bakover.common.september
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.beregning.Utbetalingsperiode
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.time.Month
import java.time.ZoneOffset
import java.util.UUID

internal class OppdragTest {
    private val sakId = UUID.randomUUID()
    private lateinit var oppdrag: Oppdrag
    private lateinit var observer: DummyObserver
    private val fnr = Fnr("12345678910")

    @BeforeEach
    fun beforeEach() {
        oppdrag = Oppdrag(sakId = sakId).also {
            observer = DummyObserver()
            it.addObserver(observer)
        }
    }

    @Test
    fun `ingen eksisterende utbetalinger`() {
        val actual = oppdrag.generererUtbetaling(
            beregningsperioder = listOf(
                Utbetalingsperiode(
                    fom = 1.januar(2020),
                    tom = 31.desember(2020),
                    beløp = 5600,
                )
            )
        )
        observer.utbetaling shouldBe null

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
    fun `nye utbetalingslinjer skal refere til forutgående utbetalingslinjer`() {
        val forrigeUtbetalingslinjeId = UUID30.randomUUID()

        val eksisterendeOppdrag = Oppdrag(
            sakId = oppdrag.sakId,
            utbetalinger = mutableListOf(
                Utbetaling(
                    kvittering = Kvittering(Kvittering.Utbetalingsstatus.OK, ""),
                    utbetalingslinjer = listOf(
                        Utbetalingslinje(
                            id = forrigeUtbetalingslinjeId,
                            opprettet = Instant.MIN,
                            fom = 1.januar(2018),
                            tom = 1.desember(2018),
                            forrigeUtbetalingslinjeId = null,
                            beløp = 5000
                        )
                    ),
                    fnr = fnr
                )
            )
        ).also {
            it.addObserver(observer)
        }

        val nyUtbetaling = eksisterendeOppdrag.generererUtbetaling(
            beregningsperioder = listOf(
                Utbetalingsperiode(
                    fom = 1.januar(2020),
                    tom = 31.mai(2020),
                    beløp = 5600,
                ),
                Utbetalingsperiode(
                    fom = 1.juni(2020),
                    tom = 31.august(2020),
                    beløp = 5700,
                ),
                Utbetalingsperiode(
                    fom = 1.september(2020),
                    tom = 31.desember(2020),
                    beløp = 5800,
                )
            )
        )

        nyUtbetaling shouldBe Utbetaling(
            id = nyUtbetaling.id,
            opprettet = nyUtbetaling.opprettet,
            simulering = null,
            utbetalingslinjer = listOf(
                expectedUtbetalingslinje(
                    utbetalingslinjeId = nyUtbetaling.utbetalingslinjer[0].id,
                    opprettet = nyUtbetaling.utbetalingslinjer[0].opprettet,
                    fom = 1.januar(2020),
                    tom = 31.mai(2020),
                    beløp = 5600,
                    forrigeUtbetalingslinjeId = forrigeUtbetalingslinjeId
                ),
                expectedUtbetalingslinje(
                    utbetalingslinjeId = nyUtbetaling.utbetalingslinjer[1].id,
                    opprettet = nyUtbetaling.utbetalingslinjer[1].opprettet,
                    fom = 1.juni(2020),
                    tom = 31.august(2020),
                    beløp = 5700,
                    forrigeUtbetalingslinjeId = nyUtbetaling.utbetalingslinjer[0].id
                ),
                expectedUtbetalingslinje(
                    utbetalingslinjeId = nyUtbetaling.utbetalingslinjer[2].id,
                    opprettet = nyUtbetaling.utbetalingslinjer[2].opprettet,
                    fom = 1.september(2020),
                    tom = 31.desember(2020),
                    beløp = 5800,
                    forrigeUtbetalingslinjeId = nyUtbetaling.utbetalingslinjer[1].id
                )
            ),
            fnr = fnr
        )

        observer.utbetaling shouldBe null
    }

    @Test
    fun `tar utgangspunkt i nyeste utbetalte ved opprettelse av nye utbetalinger`() {
        val first = Utbetaling(
            opprettet = LocalDate.of(2020, Month.JANUARY, 1).atStartOfDay().toInstant(ZoneOffset.UTC),
            kvittering = Kvittering(Kvittering.Utbetalingsstatus.OK, ""),
            utbetalingslinjer = emptyList(),
            fnr = fnr
        )

        val second = Utbetaling(
            opprettet = LocalDate.of(2020, Month.FEBRUARY, 1).atStartOfDay().toInstant(ZoneOffset.UTC),
            kvittering = Kvittering(Kvittering.Utbetalingsstatus.FEIL, ""),
            utbetalingslinjer = emptyList(),
            fnr = fnr
        )

        val third = Utbetaling(
            opprettet = LocalDate.of(2020, Month.MARCH, 1).atStartOfDay().toInstant(ZoneOffset.UTC),
            kvittering = Kvittering(Kvittering.Utbetalingsstatus.OK_MED_VARSEL, ""),
            utbetalingslinjer = emptyList(),
            fnr = fnr
        )
        val fourth = Utbetaling(
            opprettet = LocalDate.of(2020, Month.JULY, 1).atStartOfDay().toInstant(ZoneOffset.UTC),
            kvittering = Kvittering(Kvittering.Utbetalingsstatus.FEIL, ""),
            utbetalingslinjer = emptyList(),
            fnr = fnr
        )

        val oppdrag = Oppdrag(
            sakId = sakId,
            utbetalinger = mutableListOf(first, second, third, fourth)
        )
        oppdrag.sisteUtbetaling() shouldBe third
    }

    private class DummyObserver : Oppdrag.OppdragPersistenceObserver {
        var utbetaling: Utbetaling? = null
        override fun opprettUtbetaling(oppdragId: UUID30, utbetaling: Utbetaling): Utbetaling {
            this.utbetaling = utbetaling
            return utbetaling
        }

        override fun slettUtbetaling(utbetaling: Utbetaling) {
        }

        override fun hentFnr(sakId: UUID): Fnr = Fnr("12345678910")
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
