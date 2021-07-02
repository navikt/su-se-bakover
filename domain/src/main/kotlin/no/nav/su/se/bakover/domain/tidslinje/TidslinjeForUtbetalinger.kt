package no.nav.su.se.bakover.domain.tidslinje

import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingslinje
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingslinjePåTidslinje
import java.time.Clock
import java.time.LocalDate
import java.util.LinkedList

data class TidslinjeForUtbetalinger(
    private val periode: Periode,
    private val objekter: List<Utbetalingslinje>,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val nyesteFørst = Comparator<UtbetalingslinjePåTidslinje> { o1, o2 ->
        o2.opprettet.instant.compareTo(o1.opprettet.instant)
    }

    private val nyeUtbetalingslinjer = objekter
        .map { it.forTidslinje() }
        .filterIsInstance<UtbetalingslinjePåTidslinje.Ny>()

    private val andreUtbetalingslinjer = objekter
        .map { it.forTidslinje() }
        .filterNot { it is UtbetalingslinjePåTidslinje.Ny }

    private val tidslinjeForNyeUtbetalinger = Tidslinje(
        periode = periode,
        objekter = nyeUtbetalingslinjer,
        clock = clock,
    ).tidslinje

    private val utbetalingslinjerForTidslinje = tidslinjeForNyeUtbetalinger.union(andreUtbetalingslinjer)
        .sortedWith(nyesteFørst)

    private val generertTidslinje = lagTidslinje()

    val tidslinje = generertTidslinje.tidslinje

    private fun lagTidslinje(): Tidslinje<UtbetalingslinjePåTidslinje> {
        val queue = LinkedList(utbetalingslinjerForTidslinje)

        val result = mutableListOf<UtbetalingslinjePåTidslinje>()

        while (queue.isNotEmpty()) {
            val last = queue.poll()
            if (queue.isNotEmpty()) {
                when (last) {
                    /**
                     *  Ved reaktivering av utbetalingslinjer må vi generere nye data som gjenspeiler utbetalingene
                     *  som reaktiveres. Instanser av [UtbetalingslinjePåTidslinje.Reaktivering] bærer bare med seg
                     *  informasjon om den siste utbetalingslinjen på reaktiveringstidspunktet og følgelig må alle
                     *  [UtbetalingslinjePåTidslinje.Ny] overlappende med den totale "reaktiveringsperioden" regenereres
                     *  slik at informasjon fra disse "blir synlig".
                     */
                    is UtbetalingslinjePåTidslinje.Reaktivering -> {
                        result.add(last)
                        result.addAll(
                            queue.subList(0, queue.size)
                                .filterIsInstance<UtbetalingslinjePåTidslinje.Ny>()
                                .filter { it.periode overlapper last.periode && it.beløp != last.beløp }
                                .map {
                                    UtbetalingslinjePåTidslinje.Reaktivering(
                                        /**
                                         * Setter nytt tidspunkt til å være marginalt ferskere enn reaktiveringen
                                         * slik at generert informasjon får høyere presedens ved opprettelse av
                                         * [Tidslinje]. Velger minste mulige enhet av [Tidspunkt] for å unngå
                                         * (så langt det lar seg gjøre) at generert informasjon får høyere presedens
                                         * enn eventuell informasjon som er ferskere enn reaktiveringen.
                                         */
                                        opprettet = last.opprettet.plus(1L, Tidspunkt.unit),
                                        periode = Periode.create(
                                            maxOf(it.periode.fraOgMed, last.periode.fraOgMed),
                                            minOf(it.periode.tilOgMed, last.periode.tilOgMed),
                                        ),
                                        beløp = it.beløp,
                                    )
                                },
                        )
                    }
                    else -> result.add(last)
                }
            } else {
                result.add(last)
            }
        }

        return Tidslinje(
            periode = periode,
            objekter = result,
        )
    }

    fun gjeldendeForDato(dato: LocalDate): UtbetalingslinjePåTidslinje? = generertTidslinje.gjeldendeForDato(dato)

    /**
     *  Mapper utbetalingslinjer til objekter som kan plasseres på tidslinjen.
     *  For subtyper av [no.nav.su.se.bakover.domain.oppdrag.Utbetalingslinje.Endring] erstattes
     *  [no.nav.su.se.bakover.domain.oppdrag.Utbetalingslinje.Endring.fraOgMed] med
     *  [no.nav.su.se.bakover.domain.oppdrag.Utbetalingslinje.Endring.virkningstidspunkt] da dette gjenspeiler datoen
     *  endringen effektueres hos oppdrag.
     *
     *  For typer som i praksis fører til at ingen ytelse utbetales, settes beløpet til 0.
     */
    private fun Utbetalingslinje.forTidslinje(): UtbetalingslinjePåTidslinje {
        return when (this) {
            is Utbetalingslinje.Endring.Opphør -> UtbetalingslinjePåTidslinje.Opphør(
                opprettet = opprettet,
                periode = Periode.create(virkningstidspunkt, tilOgMed),
            )
            is Utbetalingslinje.Endring.Reaktivering -> UtbetalingslinjePåTidslinje.Reaktivering(
                opprettet = opprettet,
                periode = Periode.create(virkningstidspunkt, tilOgMed),
                beløp = beløp,
            )
            is Utbetalingslinje.Endring.Stans -> UtbetalingslinjePåTidslinje.Stans(
                opprettet = opprettet,
                periode = Periode.create(virkningstidspunkt, tilOgMed),
            )
            is Utbetalingslinje.Ny -> UtbetalingslinjePåTidslinje.Ny(
                opprettet = opprettet,
                periode = Periode.create(fraOgMed, tilOgMed),
                beløp = beløp,
            )
        }
    }
}
