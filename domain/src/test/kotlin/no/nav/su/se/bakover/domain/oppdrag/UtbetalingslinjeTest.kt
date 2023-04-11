package no.nav.su.se.bakover.domain.oppdrag

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import no.nav.su.se.bakover.common.Rekkefølge
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.domain.grunnlag.Uføregrad
import no.nav.su.se.bakover.test.fixedTidspunkt
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.temporal.ChronoUnit

internal class UtbetalingslinjeTest {

    @Test
    fun `sortering i stigende rekkefølge - nyeste sist`() {
        val rekkefølge = Rekkefølge.generator()
        assertThrows<IllegalStateException> {
            listOf(
                Utbetalingslinje.Ny(
                    id = UUID30.randomUUID(),
                    opprettet = fixedTidspunkt,
                    rekkefølge = rekkefølge.neste(),
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
                    rekkefølge = rekkefølge.neste(),
                    fraOgMed = 1.januar(2020),
                    tilOgMed = 31.januar(2020),
                    forrigeUtbetalingslinjeId = null,
                    beløp = 5000,
                    uføregrad = Uføregrad.parse(100),
                    utbetalingsinstruksjonForEtterbetalinger = UtbetalingsinstruksjonForEtterbetalinger.SåFortSomMulig,
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
            ).sjekkAlleNyeLinjerHarForskjelligForrigeReferanse()
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
}
