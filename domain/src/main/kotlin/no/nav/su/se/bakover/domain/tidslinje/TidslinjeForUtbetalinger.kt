package no.nav.su.se.bakover.domain.tidslinje

import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingslinje
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingslinjePåTidslinje
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.util.LinkedList

data class TidslinjeForUtbetalinger(
    private val periode: Periode,
    private val utbetalingslinjer: List<Utbetalingslinje>,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val nyesteFørst = Comparator<UtbetalingslinjePåTidslinje> { o1, o2 ->
        o2.opprettet.instant.compareTo(o1.opprettet.instant)
    }

    private val nyeUtbetalingslinjer = utbetalingslinjer
        .map { it.mapTilTidslinje() }
        .filterIsInstance<UtbetalingslinjePåTidslinje.Ny>()

    private val andreUtbetalingslinjer = utbetalingslinjer
        .map { it.mapTilTidslinje() }
        .filterNot { it is UtbetalingslinjePåTidslinje.Ny }

    private val tidslinjeForNyeUtbetalinger = Tidslinje(
        periode = periode,
        objekter = nyeUtbetalingslinjer,
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
                            queue.subList(
                                0,
                                queue.size,
                            )
                                .filterIsInstance<UtbetalingslinjePåTidslinje.Ny>()
                                .filter { it.periode overlapper last.periode && it.beløp != last.beløp }
                                .map { utbetalingslinje ->
                                    /**
                                     * Setter nytt tidspunkt til å være marginalt ferskere enn reaktiveringen
                                     * slik at regenerert informasjon får høyere presedens ved opprettelse av
                                     * [Tidslinje]. Velger minste mulige enhet av [Tidspunkt] for å unngå
                                     * (så langt det lar seg gjøre) at regenerert informasjon får høyere presedens
                                     * enn informasjon som i utgangspunktet er ferskere enn selve reaktiveringen.
                                     * Kaster [RegenerertInformasjonVilOverskriveOriginaleOpplysningerSomErFerskereException]
                                     * dersom det likevel skulle vise seg at regenerert informasjon vil interferere
                                     * med original informasjon som er ferskere.
                                     */
                                    val periode = Periode.create(
                                        maxOf(
                                            utbetalingslinje.periode.fraOgMed,
                                            last.periode.fraOgMed,
                                        ),
                                        minOf(
                                            utbetalingslinje.periode.tilOgMed,
                                            last.periode.tilOgMed,
                                        ),
                                    )
                                    val opprettet = last.opprettet.plusUnits(1)

                                    if (utbetalingslinjerForTidslinje.harOverlappendeMedOpprettetITidsintervall(
                                            fra = last.opprettet.instant,
                                            tilOgMed = opprettet.instant,
                                            periode = periode,
                                        )
                                    ) throw RegenerertInformasjonVilOverskriveOriginaleOpplysningerSomErFerskereException

                                    UtbetalingslinjePåTidslinje.Reaktivering(
                                        kopiertFraId = utbetalingslinje.kopiertFraId,
                                        opprettet = opprettet,
                                        periode = periode,
                                        beløp = utbetalingslinje.beløp,
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

    private fun List<UtbetalingslinjePåTidslinje>.harOverlappendeMedOpprettetITidsintervall(
        fra: Instant,
        tilOgMed: Instant,
        periode: Periode,
    ): Boolean {
        return this.any {
            it.periode overlapper periode &&
                it.opprettet.instant > fra &&
                it.opprettet.instant <= tilOgMed
        }
    }

    object RegenerertInformasjonVilOverskriveOriginaleOpplysningerSomErFerskereException : RuntimeException()

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
    private fun Utbetalingslinje.mapTilTidslinje(): UtbetalingslinjePåTidslinje {
        return when (this) {
            is Utbetalingslinje.Endring.Opphør -> UtbetalingslinjePåTidslinje.Opphør(
                kopiertFraId = id,
                opprettet = opprettet,
                periode = periode,
            )

            is Utbetalingslinje.Endring.Reaktivering -> UtbetalingslinjePåTidslinje.Reaktivering(
                kopiertFraId = id,
                opprettet = opprettet,
                periode = periode,
                beløp = beløp,
            )

            is Utbetalingslinje.Endring.Stans -> UtbetalingslinjePåTidslinje.Stans(
                kopiertFraId = id,
                opprettet = opprettet,
                periode = periode,
            )

            is Utbetalingslinje.Ny -> UtbetalingslinjePåTidslinje.Ny(
                kopiertFraId = id,
                opprettet = opprettet,
                periode = periode,
                beløp = beløp,
            )
        }
    }

    /**
     * Sjekker om [this] er ekvivalent med [tidslinje] for den spesifiserte perioden.
     *
     * @param periode ekvivalens skal sjekkes for, default er perioden definert av [this]
     */
    fun ekvivalentMed(
        tidslinje: TidslinjeForUtbetalinger,
        periode: Periode = this.periode
    ): Boolean {
        return periode.måneder().map {
            gjeldendeForDato(it.fraOgMed) to tidslinje.gjeldendeForDato(it.fraOgMed)
        }.map {
            when {
                it.first == null && it.second == null -> true
                (it.first != null && it.second != null) -> it.first!!.ekvivalentMed(it.second!!)
                else -> false
            }
        }.all { it }
    }
}
