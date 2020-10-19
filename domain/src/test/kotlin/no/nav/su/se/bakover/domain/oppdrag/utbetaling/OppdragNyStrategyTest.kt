package no.nav.su.se.bakover.domain.oppdrag.utbetaling

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.april
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.idag
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.mai
import no.nav.su.se.bakover.common.toTidspunkt
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.beregning.Sats
import no.nav.su.se.bakover.domain.oppdrag.Kvittering
import no.nav.su.se.bakover.domain.oppdrag.Oppdrag
import no.nav.su.se.bakover.domain.oppdrag.Oppdrag.UtbetalingStrategy.Ny
import no.nav.su.se.bakover.domain.oppdrag.Oppdragsmelding
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingslinje
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemmingsnøkkel
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.Month
import java.time.ZoneOffset
import java.util.UUID

internal class OppdragNyStrategyTest {
    private val sakId = UUID.randomUUID()
    private lateinit var oppdrag: Oppdrag
    private val fnr = Fnr("12345678910")

    private val fixedClock = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC)

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
            Ny(
                NavIdentBruker.Saksbehandler("Z123"),
                createBeregning(1.januar(2020), 30.april(2020)),
                fixedClock
            ),
            fnr = fnr
        )

        val first = actual.utbetalingslinjer.first()
        actual shouldBe expectedUtbetaling(
            actual,
            listOf(
                expectedUtbetalingslinje(
                    utbetalingslinjeId = first.id,
                    opprettet = first.opprettet,
                    fraOgMed = 1.januar(2020),
                    tilOgMed = 30.april(2020),
                    beløp = 20637,
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
                Utbetaling.KvittertUtbetaling(
                    simulering = Simulering(
                        gjelderId = fnr,
                        gjelderNavn = "navn",
                        datoBeregnet = idag(),
                        nettoBeløp = 0,
                        periodeList = listOf()
                    ),
                    kvittering = Kvittering(Kvittering.Utbetalingsstatus.OK, ""),
                    oppdragsmelding = Oppdragsmelding(
                        originalMelding = "",
                        avstemmingsnøkkel = Avstemmingsnøkkel()
                    ),
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
                    fnr = fnr,
                    type = Utbetaling.UtbetalingsType.NY,
                    oppdragId = UUID30.randomUUID(),
                    behandler = NavIdentBruker.Saksbehandler("Z123")
                )
            )
        )

        val nyUtbetaling = eksisterendeOppdrag.genererUtbetaling(
            Ny(
                NavIdentBruker.Saksbehandler("Z123"),
                createBeregning(
                    fraOgMed = 1.januar(2020),
                    tilOgMed = 31.desember(2020)
                ),
                fixedClock
            ),
            fnr = fnr
        )

        nyUtbetaling shouldBe Utbetaling.UtbetalingForSimulering(
            id = nyUtbetaling.id,
            opprettet = nyUtbetaling.opprettet,
            utbetalingslinjer = listOf(
                expectedUtbetalingslinje(
                    utbetalingslinjeId = nyUtbetaling.utbetalingslinjer[0].id,
                    opprettet = nyUtbetaling.utbetalingslinjer[0].opprettet,
                    fraOgMed = 1.januar(2020),
                    tilOgMed = 30.april(2020),
                    beløp = 20637,
                    forrigeUtbetalingslinjeId = forrigeUtbetalingslinjeId
                ),
                expectedUtbetalingslinje(
                    utbetalingslinjeId = nyUtbetaling.utbetalingslinjer[1].id,
                    opprettet = nyUtbetaling.utbetalingslinjer[1].opprettet,
                    fraOgMed = 1.mai(2020),
                    tilOgMed = 31.desember(2020),
                    beløp = 20946,
                    forrigeUtbetalingslinjeId = nyUtbetaling.utbetalingslinjer[0].id
                )
            ),
            fnr = fnr,
            type = Utbetaling.UtbetalingsType.NY,
            oppdragId = eksisterendeOppdrag.id,
            behandler = NavIdentBruker.Saksbehandler("Z123"),
            avstemmingsnøkkel = Avstemmingsnøkkel(Tidspunkt.EPOCH)
        )
    }

    @Test
    fun `tar utgangspunkt i nyeste utbetalte ved opprettelse av nye utbetalinger`() {
        val first = Utbetaling.KvittertUtbetaling(
            opprettet = LocalDate.of(2020, Month.JANUARY, 1).atStartOfDay().toTidspunkt(),
            oppdragsmelding = Oppdragsmelding("", Avstemmingsnøkkel(Tidspunkt.EPOCH)),
            kvittering = Kvittering(Kvittering.Utbetalingsstatus.OK, ""),
            utbetalingslinjer = emptyList(),
            fnr = fnr,
            simulering = Simulering(
                gjelderId = fnr,
                gjelderNavn = "navn",
                datoBeregnet = idag(),
                nettoBeløp = 0,
                periodeList = listOf()
            ),
            type = Utbetaling.UtbetalingsType.NY,
            oppdragId = UUID30.randomUUID(),
            behandler = NavIdentBruker.Saksbehandler("Z123")
        )

        val second = Utbetaling.KvittertUtbetaling(
            opprettet = LocalDate.of(2020, Month.FEBRUARY, 1).atStartOfDay().toTidspunkt(),
            oppdragsmelding = Oppdragsmelding("", Avstemmingsnøkkel(Tidspunkt.EPOCH)),
            kvittering = Kvittering(Kvittering.Utbetalingsstatus.FEIL, ""),
            utbetalingslinjer = emptyList(),
            fnr = fnr,
            simulering = Simulering(
                gjelderId = fnr,
                gjelderNavn = "navn",
                datoBeregnet = idag(),
                nettoBeløp = 0,
                periodeList = listOf()
            ),
            type = Utbetaling.UtbetalingsType.NY,
            oppdragId = UUID30.randomUUID(),
            behandler = NavIdentBruker.Saksbehandler("Z123")
        )

        val third = Utbetaling.KvittertUtbetaling(
            opprettet = LocalDate.of(2020, Month.MARCH, 1).atStartOfDay().toTidspunkt(),
            oppdragsmelding = Oppdragsmelding("", Avstemmingsnøkkel()),
            kvittering = Kvittering(Kvittering.Utbetalingsstatus.OK_MED_VARSEL, ""),
            utbetalingslinjer = emptyList(),
            fnr = fnr,
            simulering = Simulering(
                gjelderId = fnr,
                gjelderNavn = "navn",
                datoBeregnet = idag(),
                nettoBeløp = 0,
                periodeList = listOf()
            ),
            type = Utbetaling.UtbetalingsType.NY,
            oppdragId = UUID30.randomUUID(),
            behandler = NavIdentBruker.Saksbehandler("Z123")
        )
        val fourth = Utbetaling.KvittertUtbetaling(
            opprettet = LocalDate.of(2020, Month.JULY, 1).atStartOfDay().toTidspunkt(),
            oppdragsmelding = Oppdragsmelding("", Avstemmingsnøkkel()),
            kvittering = Kvittering(Kvittering.Utbetalingsstatus.FEIL, ""),
            utbetalingslinjer = emptyList(),
            fnr = fnr,
            simulering = Simulering(
                gjelderId = fnr,
                gjelderNavn = "navn",
                datoBeregnet = idag(),
                nettoBeløp = 0,
                periodeList = listOf()
            ),
            type = Utbetaling.UtbetalingsType.NY,
            oppdragId = UUID30.randomUUID(),
            behandler = NavIdentBruker.Saksbehandler("Z123")
        )
        val oppdrag = Oppdrag(
            id = UUID30.randomUUID(),
            opprettet = Tidspunkt.EPOCH,
            sakId = sakId,
            utbetalinger = mutableListOf(first, second, third, fourth)
        )
        oppdrag.sisteOversendteUtbetaling() shouldBe third
    }

    @Test
    fun `konverterer beregning til utbetalingsperioder`() {
        val actualUtbetaling = oppdrag.genererUtbetaling(
            strategy = Ny(
                NavIdentBruker.Saksbehandler("Z123"),
                beregning = createBeregning(fraOgMed = 1.januar(2020), tilOgMed = 31.desember(2020)),
                fixedClock
            ),
            fnr = fnr
        )
        actualUtbetaling shouldBe Utbetaling.UtbetalingForSimulering(
            id = actualUtbetaling.id,
            opprettet = actualUtbetaling.opprettet,
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
            type = Utbetaling.UtbetalingsType.NY,
            oppdragId = oppdrag.id,
            behandler = NavIdentBruker.Saksbehandler("Z123"),
            avstemmingsnøkkel = Avstemmingsnøkkel(Tidspunkt.EPOCH)
        )
    }

    private fun expectedUtbetaling(
        actual: Utbetaling.UtbetalingForSimulering,
        oppdragslinjer: List<Utbetalingslinje>
    ): Utbetaling.UtbetalingForSimulering {
        return Utbetaling.UtbetalingForSimulering(
            id = actual.id,
            opprettet = actual.opprettet,
            utbetalingslinjer = oppdragslinjer,
            fnr = fnr,
            type = Utbetaling.UtbetalingsType.NY,
            oppdragId = oppdrag.id,
            behandler = NavIdentBruker.Saksbehandler("Z123"),
            avstemmingsnøkkel = Avstemmingsnøkkel(Tidspunkt.EPOCH)
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

    private fun createBeregning(fraOgMed: LocalDate, tilOgMed: LocalDate) = Beregning(
        fraOgMed = fraOgMed,
        tilOgMed = tilOgMed,
        sats = Sats.HØY,
        fradrag = emptyList()
    )
}
