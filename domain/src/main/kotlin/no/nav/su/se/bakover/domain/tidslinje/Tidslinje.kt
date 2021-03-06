package no.nav.su.se.bakover.domain.tidslinje

import no.nav.su.se.bakover.common.between
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.CopyArgs
import java.time.Clock
import java.time.LocalDate
import java.util.LinkedList

data class Tidslinje<T : KanPlasseresPåTidslinje<T>>(
    private val periode: Periode,
    private val objekter: List<T>,
    private val clock: Clock = Clock.systemUTC(),
) {

    private val stigendeFraOgMed = Comparator<T> { o1, o2 ->
        o1.periode.fraOgMed.compareTo(o2.periode.fraOgMed)
    }

    private val nyesteFørst = Comparator<T> { o1, o2 ->
        o2.opprettet.instant.compareTo(o1.opprettet.instant)
    }

    val tidslinje: List<T> = lagTidslinje()
        .sortedWith(stigendeFraOgMed)

    init {
        valider(this.tidslinje)
    }

    companion object Validator {
        fun <T : KanPlasseresPåTidslinje<T>> valider(elementer: List<T>) {
            require(elementer.all { t1 -> elementer.minus(t1).none { t2 -> t1.periode.fraOgMed == t2.periode.fraOgMed } }) { "Tidslinje har flere elementer med samme fraOgMed dato!" }
            require(elementer.all { t1 -> elementer.minus(t1).none { t2 -> t1.periode.tilOgMed == t2.periode.tilOgMed } }) { "Tidslinje har flere elementer med samme tilOgMed dato!" }
            require(elementer.all { t1 -> elementer.minus(t1).none { t2 -> t1.periode overlapper t2.periode } }) { "Tidslinje har elementer med overlappende perioder!" }
        }
    }

    private fun lagTidslinje(): List<T> {
        if (objekter.isEmpty()) return emptyList()

        val overlappMedPeriode = objekter
            .filter { it.periode overlapper periode }

        val innenforPeriode = overlappMedPeriode
            .justerFraOgMedForElementerDelvisUtenforPeriode()
            .justerTilOgMedForElementerDelvisUtenforPeriode()

        val delvisOverlappende = innenforPeriode
            .filtrerVekkAlleSomOverskivesFullstendigAvNyere()

        return periodiser(delvisOverlappende)
    }

    private fun periodiser(tempResult: List<T>): List<T> {
        val queue = LinkedList(tempResult.sortedWith(stigendeFraOgMed.then(nyesteFørst)))
        val result = mutableListOf<T>()

        while (queue.isNotEmpty()) {
            val first = queue.poll()
            if (queue.isNotEmpty()) {
                val peek = queue.peek()
                if (first.periode overlapper peek.periode) {
                    val second = queue.poll()
                    if (first.periode slutterTidligere second.periode) {
                        when {
                            first.opprettet.instant < second.opprettet.instant -> {
                                result.add(
                                    first.copy(
                                        CopyArgs.Tidslinje.NyPeriode(
                                            periode = Periode.create(
                                                fraOgMed = first.periode.fraOgMed,
                                                tilOgMed = minOf(
                                                    first.periode.tilOgMed,
                                                    second.periode.fraOgMed.minusDays(1),
                                                ),
                                            ),
                                        ),
                                    ),
                                )
                                result.add(
                                    second.copy(
                                        CopyArgs.Tidslinje.NyPeriode(
                                            periode = Periode.create(
                                                fraOgMed = minOf(
                                                    first.periode.tilOgMed.plusDays(1),
                                                    second.periode.fraOgMed,
                                                ),
                                                tilOgMed = second.periode.tilOgMed,
                                            ),
                                        ),
                                    ),
                                )
                            }
                            first.opprettet.instant > second.opprettet.instant -> {
                                result.add(
                                    first.copy(
                                        CopyArgs.Tidslinje.NyPeriode(
                                            periode = Periode.create(
                                                fraOgMed = first.periode.fraOgMed,
                                                tilOgMed = maxOf(
                                                    first.periode.tilOgMed,
                                                    second.periode.fraOgMed.minusDays(1),
                                                ),
                                            ),
                                        ),
                                    ),
                                )
                                result.add(
                                    second.copy(
                                        CopyArgs.Tidslinje.NyPeriode(
                                            periode = Periode.create(
                                                fraOgMed = maxOf(
                                                    first.periode.tilOgMed.plusDays(1),
                                                    second.periode.fraOgMed,
                                                ),
                                                tilOgMed = second.periode.tilOgMed,
                                            ),
                                        ),
                                    ),
                                )
                            }
                        }
                    }

                    if (first.periode inneholder second.periode) {
                        when {
                            !(first.periode starterSamtidig second.periode) -> {
                                when {
                                    first.periode starterTidligere second.periode -> {
                                        result.add(
                                            first.copy(
                                                CopyArgs.Tidslinje.NyPeriode(
                                                    periode = Periode.create(
                                                        fraOgMed = first.periode.fraOgMed,
                                                        tilOgMed = second.periode.fraOgMed.minusDays(1),
                                                    ),
                                                ),
                                            ),
                                        )
                                        result.add(second.copy(CopyArgs.Tidslinje.Full))

                                        if (!(first.periode slutterSamtidig second.periode)) {
                                            result.add(
                                                first.copy(
                                                    CopyArgs.Tidslinje.NyPeriode(
                                                        periode = Periode.create(
                                                            fraOgMed = second.periode.tilOgMed.plusDays(1),
                                                            tilOgMed = first.periode.tilOgMed,
                                                        ),
                                                    ),
                                                ),
                                            )
                                        }
                                    }
                                }
                            }
                            first.periode starterSamtidig second.periode -> {
                                when {
                                    first.opprettet.instant > second.opprettet.instant ||
                                        first.opprettet.instant == second.opprettet.instant -> {
                                        throw RuntimeException("Feil ved periodisering i Tidslinje. Objekter er i ugyldig rekkefølge.")
                                    }
                                    first.opprettet.instant < second.opprettet.instant -> {
                                        result.add(
                                            second.copy(
                                                CopyArgs.Tidslinje.NyPeriode(
                                                    periode = Periode.create(
                                                        fraOgMed = second.periode.fraOgMed,
                                                        tilOgMed = second.periode.tilOgMed,
                                                    ),
                                                ),
                                            ),
                                        )
                                        result.add(
                                            first.copy(
                                                CopyArgs.Tidslinje.NyPeriode(
                                                    periode = Periode.create(
                                                        fraOgMed = second.periode.tilOgMed.plusDays(1),
                                                        tilOgMed = first.periode.tilOgMed,
                                                    ),
                                                ),
                                            ),
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    result.add(first.copy(CopyArgs.Tidslinje.Full))
                }
            } else {
                result.add(first.copy(CopyArgs.Tidslinje.Full))
            }
        }

        return when (result.overlappMedAndreEksisterer()) {
            true -> periodiser(result.filtrerVekkAlleSomOverskivesFullstendigAvNyere())
            else -> result
        }
    }

    private fun List<T>.justerFraOgMedForElementerDelvisUtenforPeriode(): List<T> {
        return map {
            if (it.periode starterTidligere periode) {
                it.copy(
                    CopyArgs.Tidslinje.NyPeriode(
                        periode = Periode.create(
                            fraOgMed = periode.fraOgMed,
                            tilOgMed = it.periode.tilOgMed,
                        ),
                    ),
                )
            } else {
                it.copy(CopyArgs.Tidslinje.Full)
            }
        }
    }

    private fun List<T>.justerTilOgMedForElementerDelvisUtenforPeriode(): List<T> {
        return map {
            if (it.periode slutterEtter periode) {
                it.copy(
                    CopyArgs.Tidslinje.NyPeriode(
                        periode = Periode.create(
                            fraOgMed = it.periode.fraOgMed,
                            tilOgMed = periode.tilOgMed,
                        ),
                    ),
                )
            } else {
                it.copy(CopyArgs.Tidslinje.Full)
            }
        }
    }

    private fun List<T>.overlappMedAndreEksisterer(): Boolean {
        return filter { t1 ->
            any { t2 -> t1 != t2 && t1.periode overlapper t2.periode }
        }.count() > 0
    }

    private fun List<T>.filtrerVekkAlleSomOverskivesFullstendigAvNyere(): List<T> {
        return filterNot { t1 ->
            any { t2 -> t1.opprettet.instant < t2.opprettet.instant && t2.periode.inneholder(t1.periode) }
        }
    }

    fun gjeldendeForDato(dato: LocalDate): T? = tidslinje.firstOrNull { dato.between(it.periode) }
}
