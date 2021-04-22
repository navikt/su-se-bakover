package no.nav.su.se.bakover.domain.tidslinje

import arrow.core.extensions.list.foldable.exists
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.CopyArgs
import java.time.Clock
import java.util.LinkedList

data class Tidslinje<T : KanPlasseresPåTidslinje<T>>(
    private val periode: Periode,
    private val objekter: List<T>,
    private val clock: Clock
) {

    private val stigendeFraOgMed = Comparator<T> { o1, o2 ->
        o1.getPeriode().fraOgMed.compareTo(o2.getPeriode().fraOgMed)
    }

    private val nyesteFørst = Comparator<T> { o1, o2 ->
        o2.opprettet.instant.compareTo(o1.opprettet.instant)
    }

    val tidslinje = lagTidslinje()

    private fun lagTidslinje(): List<T> {
        if (objekter.isEmpty()) return emptyList()

        val overlappMedPeriode = objekter
            .filter { it.getPeriode() overlapper periode }

        val innenforPeriode = overlappMedPeriode
            .justerFraOgMedForElementerDelvisUtenforPeriode()
            .justerTilOgMedForElementerDelvisUtenforPeriode()

        val delvisOverlappende = innenforPeriode
            .filtrerVekkAlleSomOverskivesFullstendigAvNyere()

        val sortert = delvisOverlappende
            .sortedWith(stigendeFraOgMed.then(nyesteFørst))

        return periodiser(sortert)
    }

    private fun periodiser(tempResult: List<T>): List<T> {
        val queue = LinkedList(tempResult)
        val result = mutableListOf<T>()

        while (queue.isNotEmpty()) {
            val first = queue.poll()
            if (queue.isNotEmpty()) {
                val peek = queue.peek()
                if (first.getPeriode() overlapper peek.getPeriode()) {
                    val second = queue.poll()
                    if (first.getPeriode() slutterTidligere second.getPeriode()) {
                        when {
                            first.opprettet.instant < second.opprettet.instant -> {
                                result.add(
                                    first.copy(
                                        CopyArgs.Tidslinje.NyPeriode(
                                            periode = Periode.create(
                                                fraOgMed = first.getPeriode().fraOgMed,
                                                tilOgMed = minOf(
                                                    first.getPeriode().tilOgMed,
                                                    second.getPeriode().fraOgMed.minusDays(1)
                                                )
                                            )
                                        )
                                    )
                                )
                                result.add(
                                    second.copy(
                                        CopyArgs.Tidslinje.NyPeriode(
                                            periode = Periode.create(
                                                fraOgMed = minOf(
                                                    first.getPeriode().tilOgMed.plusDays(1),
                                                    second.getPeriode().fraOgMed
                                                ),
                                                tilOgMed = second.getPeriode().tilOgMed
                                            )
                                        )
                                    )
                                )
                            }
                            first.opprettet.instant > second.opprettet.instant -> {
                                result.add(
                                    first.copy(
                                        CopyArgs.Tidslinje.NyPeriode(
                                            periode = Periode.create(
                                                fraOgMed = first.getPeriode().fraOgMed,
                                                tilOgMed = maxOf(
                                                    first.getPeriode().tilOgMed,
                                                    second.getPeriode().fraOgMed.minusDays(1)
                                                )
                                            )
                                        )
                                    )
                                )
                                result.add(
                                    second.copy(
                                        CopyArgs.Tidslinje.NyPeriode(
                                            periode = Periode.create(
                                                fraOgMed = maxOf(
                                                    first.getPeriode().tilOgMed.plusDays(1),
                                                    second.getPeriode().fraOgMed
                                                ),
                                                tilOgMed = second.getPeriode().tilOgMed
                                            )
                                        )
                                    )
                                )
                            }
                        }
                    }

                    if (first.getPeriode() inneholder second.getPeriode()) {
                        when {
                            !(first.getPeriode() starterSamtidig second.getPeriode()) -> {
                                when {
                                    first.getPeriode() starterTidligere second.getPeriode() -> {
                                        result.add(
                                            first.copy(
                                                CopyArgs.Tidslinje.NyPeriode(
                                                    periode = Periode.create(
                                                        fraOgMed = first.getPeriode().fraOgMed,
                                                        tilOgMed = second.getPeriode().fraOgMed.minusDays(1)
                                                    )
                                                )
                                            )
                                        )
                                        result.add(second.copy(CopyArgs.Tidslinje.Full))

                                        if (!(first.getPeriode() slutterSamtidig second.getPeriode())) {
                                            result.add(
                                                first.copy(
                                                    CopyArgs.Tidslinje.NyPeriode(
                                                        periode = Periode.create(
                                                            fraOgMed = second.getPeriode().tilOgMed.plusDays(1),
                                                            tilOgMed = first.getPeriode().tilOgMed
                                                        )
                                                    )
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                            first.getPeriode() starterSamtidig second.getPeriode() -> {
                                when {
                                    // TODO is this case impossible or not?
//                                    first.opprettet.instant > second.opprettet.instant -> {
//                                        result.add(
//                                            first.copy(
//                                                periode = Periode.create(
//                                                    fraOgMed = first.getPeriode().getFraOgMed(),
//                                                    tilOgMed = second.getPeriode().getTilOgMed()
//                                                )
//                                            )
//                                        )
//                                        result.add(
//                                            second.copy(
//                                                periode = Periode.create(
//                                                    fraOgMed = second.getPeriode().getTilOgMed().plusDays(1),
//                                                    tilOgMed = first.getPeriode().getTilOgMed()
//                                                )
//                                            )
//                                        )
//                                    }
                                    first.opprettet.instant < second.opprettet.instant -> {
                                        result.add(
                                            second.copy(
                                                CopyArgs.Tidslinje.NyPeriode(
                                                    periode = Periode.create(
                                                        fraOgMed = second.getPeriode().fraOgMed,
                                                        tilOgMed = second.getPeriode().tilOgMed
                                                    )
                                                )
                                            )
                                        )
                                        result.add(
                                            first.copy(
                                                CopyArgs.Tidslinje.NyPeriode(
                                                    periode = Periode.create(
                                                        fraOgMed = second.getPeriode().tilOgMed.plusDays(1),
                                                        tilOgMed = first.getPeriode().tilOgMed
                                                    )
                                                )
                                            )
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
            if (it.getPeriode() starterTidligere periode) {
                it.copy(
                    CopyArgs.Tidslinje.NyPeriode(
                        periode = Periode.create(
                            fraOgMed = periode.fraOgMed,
                            tilOgMed = it.getPeriode().tilOgMed
                        )
                    )
                )
            } else {
                it.copy(CopyArgs.Tidslinje.Full)
            }
        }
    }

    private fun List<T>.justerTilOgMedForElementerDelvisUtenforPeriode(): List<T> {
        return map {
            if (it.getPeriode() slutterEtter periode) {
                it.copy(
                    CopyArgs.Tidslinje.NyPeriode(
                        periode = Periode.create(
                            fraOgMed = it.getPeriode().fraOgMed,
                            tilOgMed = periode.tilOgMed
                        )
                    )
                )
            } else {
                it.copy(CopyArgs.Tidslinje.Full)
            }
        }
    }

    private fun List<T>.overlappMedAndreEksisterer(): Boolean {
        return filter { t1 ->
            exists { t2 -> t1 != t2 && t1.getPeriode() overlapper t2.getPeriode() }
        }.count() > 0
    }

    private fun List<T>.filtrerVekkAlleSomOverskivesFullstendigAvNyere(): List<T> {
        return filterNot { t1 ->
            exists { t2 -> t1.opprettet.instant < t2.opprettet.instant && t2.getPeriode().inneholder(t1.getPeriode()) }
        }
    }
}
