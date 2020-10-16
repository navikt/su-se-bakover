package no.nav.su.se.bakover.domain.oppdrag.utbetaling

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.idag
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.juli
import no.nav.su.se.bakover.common.juni
import no.nav.su.se.bakover.common.mai
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.oppdrag.Kvittering
import no.nav.su.se.bakover.domain.oppdrag.Oppdrag
import no.nav.su.se.bakover.domain.oppdrag.Oppdrag.UtbetalingStrategy.Stans
import no.nav.su.se.bakover.domain.oppdrag.Oppdragsmelding
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingStrategyException
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingslinje
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemmingsnøkkel
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

internal class OppdragStansTest {

    private val fixedClock = Clock.fixed(15.juni(2020).atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC)
    private val fnr = Fnr("12345678910")

    @Test
    fun `stans av utbetaling`() {
        val utbetaling = createUtbetaling(
            listOf(
                Utbetalingslinje(
                    fraOgMed = 1.januar(2020),
                    tilOgMed = 31.desember(2020),
                    forrigeUtbetalingslinjeId = null,
                    beløp = 1500
                )
            ),
            type = Utbetaling.UtbetalingsType.NY
        )

        val stans = createOppdrag(mutableListOf(utbetaling)).genererUtbetaling(
            Stans(
                behandler = NavIdentBruker.Saksbehandler("Z123"),
                clock = fixedClock
            ),
            fnr = fnr
        )

        stans.utbetalingslinjer[0].assert(
            fraOgMed = 1.juli(2020),
            tilOgMed = 31.desember(2020),
            forrigeUtbetalingslinje = utbetaling.utbetalingslinjer[0].id,
            beløp = 0
        )
    }

    @Test
    fun `ingen løpende utbetalinger å stanse etter første dato i neste måned`() {
        val utbetaling = createUtbetaling(
            listOf(
                Utbetalingslinje(
                    fraOgMed = 1.januar(2020),
                    tilOgMed = 31.mai(2020),
                    forrigeUtbetalingslinjeId = null,
                    beløp = 1500
                )
            ),
            type = Utbetaling.UtbetalingsType.NY
        )

        assertThrows<UtbetalingStrategyException> {
            createOppdrag(mutableListOf(utbetaling)).genererUtbetaling(
                Stans(
                    behandler = NavIdentBruker.Saksbehandler("Z123"),
                    clock = fixedClock
                ),
                fnr = fnr
            )
        }.also {
            it.message shouldContain "${1.juli(2020)}"
        }
    }

    @Test
    fun `siste utbetaling er en 'stans utbetaling'`() {
        val utbetaling = createUtbetaling(
            listOf(
                Utbetalingslinje(
                    fraOgMed = 1.januar(2020),
                    tilOgMed = 31.desember(2020),
                    forrigeUtbetalingslinjeId = null,
                    beløp = 0
                )
            ),
            type = Utbetaling.UtbetalingsType.STANS
        )

        assertThrows<UtbetalingStrategyException> {
            createOppdrag(mutableListOf(utbetaling)).genererUtbetaling(
                Stans(
                    behandler = NavIdentBruker.Saksbehandler("Z123"),
                    clock = fixedClock
                ),
                fnr = fnr
            )
        }.also {
            it.message shouldContain "allerede er stanset"
        }
    }

    fun createOppdrag(utbetalinger: MutableList<Utbetaling>) = Oppdrag(
        id = UUID30.randomUUID(),
        opprettet = Tidspunkt.now(),
        sakId = UUID.randomUUID(),
        utbetalinger = utbetalinger
    )

    fun createUtbetaling(utbetalingslinjer: List<Utbetalingslinje>, type: Utbetaling.UtbetalingsType) =
        Utbetaling.KvittertUtbetaling(
            simulering = Simulering(
                gjelderId = fnr,
                gjelderNavn = "navn",
                datoBeregnet = idag(),
                nettoBeløp = 0,
                periodeList = listOf()
            ),
            oppdragsmelding = Oppdragsmelding(
                originalMelding = "",
                avstemmingsnøkkel = Avstemmingsnøkkel(
                    opprettet = Tidspunkt.now()
                )
            ),
            kvittering = Kvittering(Kvittering.Utbetalingsstatus.OK_MED_VARSEL, ""),
            utbetalingslinjer = utbetalingslinjer,
            fnr = fnr,
            type = type,
            oppdragId = UUID30.randomUUID(),
            behandler = NavIdentBruker.Saksbehandler("Z123")
        )
}

fun Utbetalingslinje.assert(fraOgMed: LocalDate, tilOgMed: LocalDate, forrigeUtbetalingslinje: UUID30, beløp: Int) {
    this.fraOgMed shouldBe fraOgMed
    this.tilOgMed shouldBe tilOgMed
    this.forrigeUtbetalingslinjeId shouldBe forrigeUtbetalingslinje
    this.beløp shouldBe beløp
}
