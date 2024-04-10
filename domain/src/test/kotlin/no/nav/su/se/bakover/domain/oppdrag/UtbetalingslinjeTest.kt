package no.nav.su.se.bakover.domain.oppdrag

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import no.nav.su.se.bakover.common.Rekkefølge
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.domain.tid.januar
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.utbetaling.utbetalingslinjeNy
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import vilkår.uføre.domain.Uføregrad
import økonomi.domain.utbetaling.UtbetalingsinstruksjonForEtterbetalinger
import økonomi.domain.utbetaling.Utbetalingslinje
import økonomi.domain.utbetaling.sjekkAlleNyeLinjerHarForskjelligIdOgForrigeReferanse
import økonomi.domain.utbetaling.sjekkIngenNyeOverlapper
import økonomi.domain.utbetaling.sjekkSortering
import java.time.temporal.ChronoUnit

internal class UtbetalingslinjeTest {

    @Test
    fun `sortering i stigende rekkefølge - nyeste sist`() {
        val førsteUtbetalingslinjeId = UUID30.randomUUID()
        assertThrows<IllegalStateException> {
            listOf(
                Utbetalingslinje.Ny(
                    id = førsteUtbetalingslinjeId,
                    opprettet = fixedTidspunkt,
                    rekkefølge = Rekkefølge.skip(0),
                    fraOgMed = 1.januar(2020),
                    tilOgMed = 31.januar(2020),
                    forrigeUtbetalingslinjeId = null,
                    beløp = 5000,
                    uføregrad = Uføregrad.parse(100),
                    utbetalingsinstruksjonForEtterbetalinger = UtbetalingsinstruksjonForEtterbetalinger.SåFortSomMulig,
                ),
                Utbetalingslinje.Ny(
                    id = UUID30.randomUUID(),
                    opprettet = fixedTidspunkt.minus(1, ChronoUnit.DAYS),
                    fraOgMed = 1.januar(2020),
                    tilOgMed = 31.januar(2020),
                    forrigeUtbetalingslinjeId = førsteUtbetalingslinjeId,
                    beløp = 5000,
                    uføregrad = Uføregrad.parse(100),
                    utbetalingsinstruksjonForEtterbetalinger = UtbetalingsinstruksjonForEtterbetalinger.SåFortSomMulig,
                    rekkefølge = Rekkefølge.start(),
                ),
            ).sjekkSortering()
        }.also {
            it.message shouldBe "Utbetalingslinjer er ikke sortert i stigende rekkefølge"
        }
    }

    @Test
    fun `nye linjer har forskjellig forrige referanse`() {
        val rekkefølge = Rekkefølge.generator()
        assertThrows<IllegalStateException> {
            listOf(
                Utbetalingslinje.Ny(
                    id = UUID30.randomUUID(),
                    opprettet = fixedTidspunkt,
                    rekkefølge = rekkefølge.neste(),
                    fraOgMed = 1.januar(2020),
                    tilOgMed = 31.januar(2020),
                    forrigeUtbetalingslinjeId = UUID30.fromString("268e62fb-3079-4e8d-ab32-ff9fb9"),
                    beløp = 5000,
                    uføregrad = Uføregrad.parse(100),
                    utbetalingsinstruksjonForEtterbetalinger = UtbetalingsinstruksjonForEtterbetalinger.SåFortSomMulig,
                ),
                Utbetalingslinje.Ny(
                    id = UUID30.randomUUID(),
                    opprettet = fixedTidspunkt,
                    rekkefølge = rekkefølge.neste(),
                    fraOgMed = 1.januar(2020),
                    tilOgMed = 31.januar(2020),
                    forrigeUtbetalingslinjeId = UUID30.fromString("268e62fb-3079-4e8d-ab32-ff9fb9"),
                    beløp = 5000,
                    uføregrad = Uføregrad.parse(100),
                    utbetalingsinstruksjonForEtterbetalinger = UtbetalingsinstruksjonForEtterbetalinger.SåFortSomMulig,
                ),
            ).sjekkAlleNyeLinjerHarForskjelligIdOgForrigeReferanse()
        }.also {
            it.message.shouldContain("Alle nye utbetalingslinjer skal referere til forskjellig forrige utbetalingid")
        }
    }

    @Test
    fun `nye linjer kan ikke overlappe`() {
        val rekkefølge = Rekkefølge.generator()
        assertThrows<IllegalStateException> {
            listOf(
                Utbetalingslinje.Ny(
                    id = UUID30.randomUUID(),
                    opprettet = fixedTidspunkt,
                    rekkefølge = rekkefølge.neste(),
                    fraOgMed = 1.januar(2020),
                    tilOgMed = 31.januar(2020),
                    forrigeUtbetalingslinjeId = UUID30.fromString("268e62fb-3079-4e8d-ab32-ff9fb9"),
                    beløp = 5000,
                    uføregrad = Uføregrad.parse(100),
                    utbetalingsinstruksjonForEtterbetalinger = UtbetalingsinstruksjonForEtterbetalinger.SåFortSomMulig,
                ),
                Utbetalingslinje.Ny(
                    id = UUID30.randomUUID(),
                    opprettet = fixedTidspunkt,
                    rekkefølge = rekkefølge.neste(),
                    fraOgMed = 1.januar(2020),
                    tilOgMed = 31.januar(2020),
                    forrigeUtbetalingslinjeId = UUID30.fromString("268e62fb-3079-4e8d-ab32-ff9fb9"),
                    beløp = 5000,
                    uføregrad = Uføregrad.parse(100),
                    utbetalingsinstruksjonForEtterbetalinger = UtbetalingsinstruksjonForEtterbetalinger.SåFortSomMulig,
                ),
            ).sjekkIngenNyeOverlapper()
        }.also {
            it.message shouldBe "Nye linjer kan ikke overlappe"
        }
    }

    @Nested
    inner class compareTo {
        @Test
        fun `Nye linjer med samme tidspunkt`() {
            val u1 = utbetalingslinjeNy()
            val u2 = utbetalingslinjeNy(forrigeUtbetalingslinjeId = u1.id, rekkefølge = Rekkefølge.skip(0))
            val u3 = utbetalingslinjeNy(forrigeUtbetalingslinjeId = u2.id, rekkefølge = Rekkefølge.skip(1))
            val u4 = utbetalingslinjeNy(forrigeUtbetalingslinjeId = u3.id, rekkefølge = Rekkefølge.skip(2))
            listOf(u4, u3, u2, u1).sorted() shouldBe listOf(u1, u2, u3, u4)
        }

        @Test
        fun `Nye linjer med samme tidspunkt 2`() {
            val u1 = utbetalingslinjeNy()
            val u2 = utbetalingslinjeNy(forrigeUtbetalingslinjeId = u1.id, rekkefølge = Rekkefølge.skip(0))
            val u3 = utbetalingslinjeNy(forrigeUtbetalingslinjeId = u2.id, rekkefølge = Rekkefølge.skip(1))
            val u4 = utbetalingslinjeNy(forrigeUtbetalingslinjeId = u3.id, rekkefølge = Rekkefølge.skip(2))
            listOf(u4, u1, u3, u2).sorted() shouldBe listOf(u1, u2, u3, u4)
        }
    }
}
