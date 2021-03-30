package no.nav.su.se.bakover.domain.oppdrag.utbetaling

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.april
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.februar
import no.nav.su.se.bakover.common.idag
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.mai
import no.nav.su.se.bakover.common.mars
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.startOfDay
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.beregning.BeregningFactory
import no.nav.su.se.bakover.domain.beregning.Sats
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragFactory
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragStrategy
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.domain.oppdrag.Kvittering
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling.Companion.hentOversendteUtbetalingerUtenFeil
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingslinje
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingsrequest
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingsstrategi
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemmingsnøkkel
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.Month
import java.time.ZoneOffset
import java.util.UUID

internal class UtbetalingsstrategiNyTest {
    private val sakId = UUID.randomUUID()
    private val saksnummer = Saksnummer(1234)

    private val fnr = Fnr("12345678910")

    private val fixedClock = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC)

    @Test
    fun `ingen eksisterende utbetalinger`() {
        val actual = Utbetalingsstrategi.Ny(
            sakId = sakId,
            saksnummer = saksnummer,
            fnr = fnr,
            behandler = NavIdentBruker.Saksbehandler("Z123"),
            beregning = createBeregning(1.januar(2020), 30.april(2020)),
            utbetalinger = listOf(),
            clock = fixedClock,
        ).generate()

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

        val eksisterendeUtbetalinger = listOf(
            Utbetaling.OversendtUtbetaling.MedKvittering(
                sakId = sakId,
                saksnummer = saksnummer,
                simulering = Simulering(
                    gjelderId = fnr,
                    gjelderNavn = "navn",
                    datoBeregnet = idag(),
                    nettoBeløp = 0,
                    periodeList = listOf()
                ),
                kvittering = Kvittering(Kvittering.Utbetalingsstatus.OK, ""),
                utbetalingsrequest = Utbetalingsrequest(
                    value = ""
                ),
                utbetalingslinjer = listOf(
                    Utbetalingslinje.Ny(
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
                behandler = NavIdentBruker.Saksbehandler("Z123")
            )
        )

        val nyUtbetaling = Utbetalingsstrategi.Ny(
            sakId = sakId,
            saksnummer = saksnummer,
            fnr = fnr,
            utbetalinger = eksisterendeUtbetalinger,
            behandler = NavIdentBruker.Saksbehandler("Z123"),
            beregning = createBeregning(
                fraOgMed = 1.januar(2020),
                tilOgMed = 31.desember(2020)
            ),
            clock = fixedClock
        ).generate()

        nyUtbetaling shouldBe Utbetaling.UtbetalingForSimulering(
            id = nyUtbetaling.id,
            opprettet = nyUtbetaling.opprettet,
            sakId = sakId,
            saksnummer = saksnummer,
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
            behandler = NavIdentBruker.Saksbehandler("Z123"),
            avstemmingsnøkkel = Avstemmingsnøkkel(Tidspunkt.EPOCH)
        )
    }

    @Test
    fun `tar utgangspunkt i nyeste utbetalte ved opprettelse av nye utbetalinger`() {
        val first = Utbetaling.OversendtUtbetaling.MedKvittering(
            id = UUID30.randomUUID(),
            opprettet = LocalDate.of(2020, Month.JANUARY, 1).startOfDay(),
            sakId = sakId,
            saksnummer = saksnummer,
            utbetalingsrequest = Utbetalingsrequest(""),
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
            behandler = NavIdentBruker.Saksbehandler("Z123")
        )

        val second = Utbetaling.OversendtUtbetaling.MedKvittering(
            id = UUID30.randomUUID(),
            opprettet = LocalDate.of(2020, Month.FEBRUARY, 1).startOfDay(),
            sakId = sakId,
            saksnummer = saksnummer,
            utbetalingsrequest = Utbetalingsrequest(""),
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
            behandler = NavIdentBruker.Saksbehandler("Z123")
        )

        val third = Utbetaling.OversendtUtbetaling.MedKvittering(
            id = UUID30.randomUUID(),
            opprettet = LocalDate.of(2020, Month.MARCH, 1).startOfDay(),
            sakId = sakId,
            saksnummer = saksnummer,
            utbetalingsrequest = Utbetalingsrequest(""),
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
            behandler = NavIdentBruker.Saksbehandler("Z123")
        )
        val fourth = Utbetaling.OversendtUtbetaling.MedKvittering(
            id = UUID30.randomUUID(),
            opprettet = LocalDate.of(2020, Month.JULY, 1).startOfDay(),
            sakId = sakId,
            saksnummer = saksnummer,
            utbetalingsrequest = Utbetalingsrequest(""),
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
            behandler = NavIdentBruker.Saksbehandler("Z123")
        )
        val utbetalinger = listOf(first, second, third, fourth)
        utbetalinger.hentOversendteUtbetalingerUtenFeil()[1] shouldBe third
    }

    @Test
    fun `konverterer tilstøtende beregningsperioder med forskjellig beløp til separate utbetalingsperioder`() {
        val actualUtbetaling = Utbetalingsstrategi.Ny(
            sakId = sakId,
            saksnummer = saksnummer,
            fnr = fnr,
            utbetalinger = emptyList(),
            behandler = NavIdentBruker.Saksbehandler("Z123"),
            beregning = createBeregning(fraOgMed = 1.januar(2020), tilOgMed = 31.desember(2020)),
            clock = fixedClock
        ).generate()
        actualUtbetaling shouldBe Utbetaling.UtbetalingForSimulering(
            id = actualUtbetaling.id,
            opprettet = actualUtbetaling.opprettet,
            sakId = sakId,
            saksnummer = saksnummer,
            utbetalingslinjer = listOf(
                Utbetalingslinje.Ny(
                    id = actualUtbetaling.utbetalingslinjer[0].id,
                    opprettet = actualUtbetaling.utbetalingslinjer[0].opprettet,
                    fraOgMed = 1.januar(2020),
                    tilOgMed = 30.april(2020),
                    forrigeUtbetalingslinjeId = null,
                    beløp = 20637
                ),
                Utbetalingslinje.Ny(
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
            behandler = NavIdentBruker.Saksbehandler("Z123"),
            avstemmingsnøkkel = Avstemmingsnøkkel(Tidspunkt.EPOCH)
        )
    }

    @Test
    fun `perioder som har likt beløp, men ikke tilstøter hverandre får separate utbetalingsperioder`() {
        val actualUtbetaling = Utbetalingsstrategi.Ny(
            sakId = sakId,
            saksnummer = saksnummer,
            fnr = fnr,
            utbetalinger = emptyList(),
            behandler = NavIdentBruker.Saksbehandler("Z123"),
            beregning = BeregningFactory.ny(
                periode = Periode.create(1.januar(2020), 30.april(2020)),
                sats = Sats.HØY,
                fradrag = listOf(
                    FradragFactory.ny(
                        type = Fradragstype.ForventetInntekt,
                        månedsbeløp = 1000.0,
                        periode = Periode.create(fraOgMed = 1.januar(2020), tilOgMed = 30.april(2020)),
                        utenlandskInntekt = null,
                        tilhører = FradragTilhører.BRUKER
                    ),
                    FradragFactory.ny(
                        type = Fradragstype.Arbeidsinntekt,
                        månedsbeløp = 4000.0,
                        periode = Periode.create(fraOgMed = 1.februar(2020), tilOgMed = 29.februar(2020)),
                        utenlandskInntekt = null,
                        tilhører = FradragTilhører.BRUKER
                    )
                ),
                fradragStrategy = FradragStrategy.Enslig
            ),
            clock = fixedClock
        ).generate()
        actualUtbetaling shouldBe Utbetaling.UtbetalingForSimulering(
            id = actualUtbetaling.id,
            opprettet = actualUtbetaling.opprettet,
            sakId = sakId,
            saksnummer = saksnummer,
            utbetalingslinjer = listOf(
                Utbetalingslinje.Ny(
                    id = actualUtbetaling.utbetalingslinjer[0].id,
                    opprettet = actualUtbetaling.utbetalingslinjer[0].opprettet,
                    fraOgMed = 1.januar(2020),
                    tilOgMed = 31.januar(2020),
                    forrigeUtbetalingslinjeId = null,
                    beløp = 19637
                ),
                Utbetalingslinje.Ny(
                    id = actualUtbetaling.utbetalingslinjer[1].id,
                    opprettet = actualUtbetaling.utbetalingslinjer[1].opprettet,
                    fraOgMed = 1.februar(2020),
                    tilOgMed = 29.februar(2020),
                    forrigeUtbetalingslinjeId = actualUtbetaling.utbetalingslinjer[0].id,
                    beløp = 16637
                ),
                Utbetalingslinje.Ny(
                    id = actualUtbetaling.utbetalingslinjer[2].id,
                    opprettet = actualUtbetaling.utbetalingslinjer[2].opprettet,
                    fraOgMed = 1.mars(2020),
                    tilOgMed = 30.april(2020),
                    forrigeUtbetalingslinjeId = actualUtbetaling.utbetalingslinjer[1].id,
                    beløp = actualUtbetaling.utbetalingslinjer[0].beløp
                )
            ),
            fnr = fnr,
            type = Utbetaling.UtbetalingsType.NY,
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
            sakId = sakId,
            saksnummer = saksnummer,
            utbetalingslinjer = oppdragslinjer,
            fnr = fnr,
            type = Utbetaling.UtbetalingsType.NY,
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
        return Utbetalingslinje.Ny(
            id = utbetalingslinjeId,
            opprettet = opprettet,
            fraOgMed = fraOgMed,
            tilOgMed = tilOgMed,
            forrigeUtbetalingslinjeId = forrigeUtbetalingslinjeId,
            beløp = beløp
        )
    }

    private fun createBeregning(fraOgMed: LocalDate, tilOgMed: LocalDate) = BeregningFactory.ny(
        periode = Periode.create(fraOgMed, tilOgMed),
        sats = Sats.HØY,
        fradrag = listOf(
            FradragFactory.ny(
                type = Fradragstype.ForventetInntekt,
                månedsbeløp = 0.0,
                periode = Periode.create(fraOgMed = fraOgMed, tilOgMed = tilOgMed),
                utenlandskInntekt = null,
                tilhører = FradragTilhører.BRUKER
            )
        ),
        fradragStrategy = FradragStrategy.Enslig
    )
}
