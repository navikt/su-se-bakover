package no.nav.su.se.bakover.test.utbetaling

import no.nav.su.se.bakover.common.Rekkefølge
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.periode.år
import no.nav.su.se.bakover.domain.grunnlag.Uføregrad
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingsinstruksjonForEtterbetalinger
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingslinje
import no.nav.su.se.bakover.test.fixedClock
import java.time.Clock
import java.time.LocalDate

fun utbetalingslinjeNy(
    id: UUID30 = UUID30.randomUUID(),
    periode: Periode = år(2021),
    clock: Clock = fixedClock,
    rekkefølge: Rekkefølge = Rekkefølge.start(),
    beløp: Int = 15000,
    forrigeUtbetalingslinjeId: UUID30? = null,
    uføregrad: Int = 50,
    kjøreplan: UtbetalingsinstruksjonForEtterbetalinger = UtbetalingsinstruksjonForEtterbetalinger.SåFortSomMulig,
): Utbetalingslinje.Ny {
    return utbetalingslinjeNy(
        id = id,
        periode = periode,
        rekkefølge = rekkefølge,
        opprettet = Tidspunkt.now(clock),
        beløp = beløp,
        forrigeUtbetalingslinjeId = forrigeUtbetalingslinjeId,
        uføregrad = uføregrad,
        kjøreplan = kjøreplan,
    )
}

/**
 * Syr sammen en liste med utbetalingslinjer slik at de peker på hverandre.
 * TODO jah: Bør returnere Utbetalingslinjer istedenfor List<Utbetalingslinje>
 */
fun utbetalingslinjer(
    første: Utbetalingslinje,
    andre: Utbetalingslinje,
    vararg utbetalingslinjer: Utbetalingslinje,
): List<Utbetalingslinje> {
    return listOf(første) + (listOf(første) + andre + utbetalingslinjer).zipWithNext { a, b ->
        if (b is Utbetalingslinje.Ny) {
            b.oppdaterReferanseTilForrigeUtbetalingslinje(a.id)
        } else {
            b
        }
    }
}

fun utbetalingslinjeNy(
    id: UUID30 = UUID30.randomUUID(),
    periode: Periode = år(2021),
    opprettet: Tidspunkt,
    rekkefølge: Rekkefølge = Rekkefølge.start(),
    beløp: Int = 15000,
    forrigeUtbetalingslinjeId: UUID30? = null,
    uføregrad: Int = 50,
    kjøreplan: UtbetalingsinstruksjonForEtterbetalinger = UtbetalingsinstruksjonForEtterbetalinger.SåFortSomMulig,
): Utbetalingslinje.Ny {
    return Utbetalingslinje.Ny(
        id = id,
        opprettet = opprettet,
        rekkefølge = rekkefølge,
        fraOgMed = periode.fraOgMed,
        tilOgMed = periode.tilOgMed,
        forrigeUtbetalingslinjeId = forrigeUtbetalingslinjeId,
        beløp = beløp,
        uføregrad = Uføregrad.parse(uføregrad),
        utbetalingsinstruksjonForEtterbetalinger = kjøreplan,
    )
}

fun utbetalingslinjeOpphørt(
    utbetalingslinjeSomSkalEndres: Utbetalingslinje,
    virkningsperiode: Periode = utbetalingslinjeSomSkalEndres.periode,
    clock: Clock,
    rekkefølge: Rekkefølge = utbetalingslinjeSomSkalEndres.rekkefølge.neste(),
): Utbetalingslinje.Endring.Opphør {
    return Utbetalingslinje.Endring.Opphør(
        utbetalingslinjeSomSkalEndres = utbetalingslinjeSomSkalEndres,
        virkningsperiode = virkningsperiode,
        clock = clock,
        rekkefølge = rekkefølge,
    )
}

@Suppress("unused")
fun utbetalingslinjeStans(
    utbetalingslinjeSomSkalEndres: Utbetalingslinje,
    virkningstidspunkt: LocalDate = utbetalingslinjeSomSkalEndres.originalFraOgMed(),
    clock: Clock,
    rekkefølge: Rekkefølge = utbetalingslinjeSomSkalEndres.rekkefølge.neste(),
): Utbetalingslinje.Endring.Stans {
    return Utbetalingslinje.Endring.Stans(
        utbetalingslinjeSomSkalEndres = utbetalingslinjeSomSkalEndres,
        virkningstidspunkt = virkningstidspunkt,
        clock = clock,
        rekkefølge = rekkefølge,
    )
}

@Suppress("unused")
fun utbetalingslinjeReaktivering(
    utbetalingslinjeSomSkalEndres: Utbetalingslinje,
    virkningstidspunkt: LocalDate = utbetalingslinjeSomSkalEndres.originalFraOgMed(),
    clock: Clock,
    rekkefølge: Rekkefølge = utbetalingslinjeSomSkalEndres.rekkefølge.neste(),
): Utbetalingslinje.Endring.Reaktivering {
    return Utbetalingslinje.Endring.Reaktivering(
        utbetalingslinjeSomSkalEndres = utbetalingslinjeSomSkalEndres,
        virkningstidspunkt = virkningstidspunkt,
        clock = clock,
        rekkefølge = rekkefølge,
    )
}
