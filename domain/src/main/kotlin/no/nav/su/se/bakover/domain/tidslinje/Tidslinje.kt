package no.nav.su.se.bakover.domain.tidslinje

import no.nav.su.se.bakover.common.application.CopyArgs
import no.nav.su.se.bakover.common.between
import no.nav.su.se.bakover.common.periode.Måned
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.periode.minAndMaxOf
import no.nav.su.se.bakover.common.sikkerLogg
import no.nav.su.se.bakover.domain.tidslinje.Tidslinje.Companion.Validator.valider
import java.time.LocalDate
import java.util.LinkedList

/**
 * Konstruerer en tidslinje av periodiserte opplysninger innenfor angitt [periode].
 * Periodiseringen vil ta hensyn til to hovedparametere; [KanPlasseresPåTidslinjeMedSegSelv.periode] og [KanPlasseresPåTidslinjeMedSegSelv.opprettet].
 * Dersom [KanPlasseresPåTidslinjeMedSegSelv.periode] er overlappende for to elementer, vil elementet med nyeste [KanPlasseresPåTidslinjeMedSegSelv.opprettet]
 * få presedens og følgelig overskrive det første elementet for perioden som overlapper. Perioder uten overlapp for elementer forblir uberørt.
 *
 * @param periode "utsnittet" tidslinjen skal konstrueres for.
 *
 * @see KanPlasseresPåTidslinjeMedSegSelv
 * @see KanPlasseresPåTidslinje
 */
class Tidslinje<T : KanPlasseresPåTidslinjeMedSegSelv<T>> private constructor(
    private val objekter: List<KanPlasseresPåTidslinjeMedSegSelv<T>>,
    // TODO jah: Endre objekter til NEL og fjerne else-casen
    private val periode: Periode = if (objekter.isNotEmpty()) objekter.map { it.periode }.minAndMaxOf() else Periode.create(LocalDate.MIN, LocalDate.MAX),
) {
    companion object {
        @JvmName("intern")
        operator fun <T : KanPlasseresPåTidslinjeMedSegSelv<T>> invoke(
            periode: Periode,
            objekter: List<KanPlasseresPåTidslinjeMedSegSelv<T>>,
        ): Tidslinje<T> {
            return Tidslinje(
                objekter = objekter,
                periode = periode,
            )
        }

        @JvmName("tidslinjeFraPeriode")
        operator fun <T : KanPlasseresPåTidslinje<T>> invoke(
            periode: Periode,
            objekter: List<KanPlasseresPåTidslinje<T>>,
        ): Tidslinje<T> {
            return Tidslinje(
                objekter = objekter,
                periode = periode,
            )
        }

        @JvmName("tidslinjeFraMåned")
        operator fun <T : KanPlasseresPåTidslinje<T>> invoke(
            fraOgMed: Måned,
            objekter: List<KanPlasseresPåTidslinje<T>>,
        ): Tidslinje<T> {
            return Tidslinje(
                objekter = objekter,
                periode = Periode.create(
                    fraOgMed = fraOgMed.fraOgMed,
                    tilOgMed = LocalDate.MAX,
                ),
            )
        }

        @JvmName("tidslinje")
        operator fun <T : KanPlasseresPåTidslinje<T>> invoke(
            objekter: List<KanPlasseresPåTidslinje<T>>,
        ): Tidslinje<T> {
            return Tidslinje(objekter)
        }

        object Validator {
            fun <T : KanPlasseresPåTidslinjeMedSegSelv<T>> valider(elementer: List<T>) {
                check(
                    elementer.all { t1 ->
                        elementer.minus(t1).none { t2 -> t1.periode.fraOgMed == t2.periode.fraOgMed }
                    },
                ) { "Tidslinje har flere elementer med samme fraOgMed dato!" }
                check(
                    elementer.all { t1 ->
                        elementer.minus(t1).none { t2 -> t1.periode.tilOgMed == t2.periode.tilOgMed }
                    },
                ) { "Tidslinje har flere elementer med samme tilOgMed dato!" }
                check(
                    elementer.all { t1 ->
                        elementer.minus(t1).none { t2 -> t1.periode overlapper t2.periode }
                    },
                ) { "Tidslinje har elementer med overlappende perioder!" }
            }
        }
    }

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
                                    first.opprettet.instant > second.opprettet.instant -> {
                                        sikkerLogg.error("Feil ved periodisering i Tidslinje. Objekter er i ugyldig rekkefølge. First: $first er opprettet etter Second: $second. Queue: $queue. Result: $result")
                                        throw IllegalStateException("Feil ved periodisering i Tidslinje. Objekter er i ugyldig rekkefølge. First er opprettet etter second. Se sikkerlogg.")
                                    }
                                    first.opprettet.instant == second.opprettet.instant -> {
                                        sikkerLogg.error("Feil ved periodisering i Tidslinje. Objekter er i ugyldig rekkefølge. First: $first er opprettet samtidig som Second: $second. Queue: $queue. Result: $result")
                                        throw IllegalStateException("Feil ved periodisering i Tidslinje. Objekter er i ugyldig rekkefølge. First er opprettet samtidig som second. Se sikkerlogg.")
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

    private fun List<KanPlasseresPåTidslinjeMedSegSelv<T>>.justerFraOgMedForElementerDelvisUtenforPeriode(): List<T> {
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

    private fun List<KanPlasseresPåTidslinjeMedSegSelv<T>>.justerTilOgMedForElementerDelvisUtenforPeriode(): List<T> {
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

    private fun List<KanPlasseresPåTidslinjeMedSegSelv<T>>.overlappMedAndreEksisterer(): Boolean {
        return filter { t1 ->
            any { t2 -> t1 != t2 && t1.periode overlapper t2.periode }
        }.isNotEmpty()
    }

    private fun List<T>.filtrerVekkAlleSomOverskivesFullstendigAvNyere(): List<T> {
        return filterNot { t1 ->
            any { t2 -> t1.opprettet.instant < t2.opprettet.instant && t2.periode.inneholder(t1.periode) }
        }
    }

    fun gjeldendeForDato(dato: LocalDate): T? = tidslinje.firstOrNull { dato.between(it.periode) }
}
