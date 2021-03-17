package no.nav.su.se.bakover.domain.grunnlag

import arrow.core.extensions.list.foldable.exists
import no.nav.su.se.bakover.common.periode.Periode
import java.time.Clock
import java.util.LinkedList

data class Grunnlagsdatasett(
    /** Sammensmelting av vedtakene før revurderingen. Det som lå til grunn for revurderingen */
    val førBehandling: Grunnlagsdata,
    /** De endringene som er lagt til i revurderingen (denne oppdateres ved lagring) */
    val endring: Grunnlagsdata,
    /** Sammensmeltinga av førBehandling og endring - denne er ikke persistert  */
    val resultat: Grunnlagsdata,
)

data class Grunnlagsdata(
    val uføregrunnlag: List<Grunnlag.Uføregrunnlag> = emptyList()
) {
    companion object {
        val EMPTY = Grunnlagsdata()
    }
}

private val stigendeFraOgMed = Comparator<Grunnlag.Uføregrunnlag> { o1, o2 ->
    o1.periode.getFraOgMed().compareTo(o2.periode.getFraOgMed())
}

private val nyesteFørst = Comparator<Grunnlag.Uføregrunnlag> { o1, o2 ->
    o2.opprettet.instant.compareTo(o1.opprettet.instant)
}
// TODO generalize algorithm for reuse on multiple objects implementing periodisert informasjon/oppettet tidspunkt.
data class UføregrunnlagTidslinje(
    private val periode: Periode,
    private val objekter: List<Grunnlag.Uføregrunnlag>,
    private val clock: Clock
) {
    val tidslinje = lagTidslinje()

    private fun lagTidslinje(): List<Grunnlag.Uføregrunnlag> {
        if (objekter.isEmpty()) return emptyList()

        val delvisOverlappende = objekter.fjernAlleSomOverskivesFullstendigAvNyere()
            .sortedWith(stigendeFraOgMed.then(nyesteFørst))

        return periodiser(delvisOverlappende)
    }

    private fun periodiser(tempResult: List<Grunnlag.Uføregrunnlag>): List<Grunnlag.Uføregrunnlag> {
        val queue = LinkedList(tempResult)
        val result = mutableListOf<Grunnlag.Uføregrunnlag>()

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
                                        periode = Periode.create(
                                            fraOgMed = first.periode.getFraOgMed(),
                                            tilOgMed = minOf(
                                                first.periode.getTilOgMed(),
                                                second.periode.getFraOgMed().minusDays(1)
                                            )
                                        )
                                    )
                                )
                                result.add(
                                    second.copy(
                                        periode = Periode.create(
                                            fraOgMed = minOf(
                                                first.periode.getTilOgMed().plusDays(1),
                                                second.periode.getFraOgMed()
                                            ),
                                            tilOgMed = second.periode.getTilOgMed()
                                        )
                                    )
                                )
                            }
                            first.opprettet.instant > second.opprettet.instant -> {
                                result.add(
                                    first.copy(
                                        periode = Periode.create(
                                            fraOgMed = first.periode.getFraOgMed(),
                                            tilOgMed = maxOf(
                                                first.periode.getTilOgMed(),
                                                second.periode.getFraOgMed().minusDays(1)
                                            )
                                        )
                                    )
                                )
                                result.add(
                                    second.copy(
                                        periode = Periode.create(
                                            fraOgMed = maxOf(
                                                first.periode.getTilOgMed().plusDays(1),
                                                second.periode.getFraOgMed()
                                            ),
                                            tilOgMed = second.periode.getTilOgMed()
                                        )
                                    )
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
                                                periode = Periode.create(
                                                    fraOgMed = first.periode.getFraOgMed(),
                                                    tilOgMed = second.periode.getFraOgMed().minusDays(1)
                                                )
                                            )
                                        )
                                        result.add(second.copy())

                                        if (!(first.periode slutterSamtidig second.periode)) {
                                            result.add(
                                                first.copy(
                                                    periode = Periode.create(
                                                        fraOgMed = second.periode.getTilOgMed().plusDays(1),
                                                        tilOgMed = first.periode.getTilOgMed()
                                                    )
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                            first.periode starterSamtidig second.periode -> {
                                when {
                                    // TODO is this case impossible or not?
//                                    first.opprettet.instant > second.opprettet.instant -> {
//                                        result.add(
//                                            first.copy(
//                                                periode = Periode.create(
//                                                    fraOgMed = first.periode.getFraOgMed(),
//                                                    tilOgMed = second.periode.getTilOgMed()
//                                                )
//                                            )
//                                        )
//                                        result.add(
//                                            second.copy(
//                                                periode = Periode.create(
//                                                    fraOgMed = second.periode.getTilOgMed().plusDays(1),
//                                                    tilOgMed = first.periode.getTilOgMed()
//                                                )
//                                            )
//                                        )
//                                    }
                                    first.opprettet.instant < second.opprettet.instant -> {
                                        result.add(
                                            second.copy(
                                                periode = Periode.create(
                                                    fraOgMed = second.periode.getFraOgMed(),
                                                    tilOgMed = second.periode.getTilOgMed()
                                                )
                                            )
                                        )
                                        result.add(
                                            first.copy(
                                                periode = Periode.create(
                                                    fraOgMed = second.periode.getTilOgMed().plusDays(1),
                                                    tilOgMed = first.periode.getTilOgMed()
                                                )
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    result.add(first.copy())
                }
            } else {
                result.add(first.copy())
            }
        }

        return when (result.overlappMedAndreEksisterer()) {
            true -> periodiser(result.fjernAlleSomOverskivesFullstendigAvNyere())
            else -> result
        }
    }

    private fun List<Grunnlag.Uføregrunnlag>.overlappMedAndreEksisterer(): Boolean {
        return filter { t1 ->
            exists { t2 -> t1 != t2 && t1.periode overlapper t2.periode }
        }.count() > 0
    }

    private fun List<Grunnlag.Uføregrunnlag>.fjernAlleSomOverskivesFullstendigAvNyere(): List<Grunnlag.Uføregrunnlag> {
        return filterNot { t1 ->
            exists { t2 -> t1.opprettet.instant < t2.opprettet.instant && t2.periode.inneholder(t1.periode) }
        }
    }
}
