package no.nav.su.se.bakover.domain.tidslinje

import arrow.core.NonEmptyList
import arrow.core.toNonEmptyListOrNull
import no.nav.su.se.bakover.common.application.CopyArgs
import no.nav.su.se.bakover.common.between
import no.nav.su.se.bakover.common.periode.Måned
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.periode.minAndMaxOf
import no.nav.su.se.bakover.common.sikkerLogg
import no.nav.su.se.bakover.common.toNonEmptyList
import no.nav.su.se.bakover.domain.tidslinje.Tidslinje.Companion.Validator.valider
import no.nav.su.se.bakover.domain.vedtak.VedtakPåTidslinje
import java.time.LocalDate
import java.util.LinkedList

/**
 * Konstruerer en tidslinje av periodiserte opplysninger, begrenset innen for opplysningenes totale periode.
 * Periodiseringen vil ta hensyn til to hovedparametere; [KanPlasseresPåTidslinjeMedSegSelv.periode], [KanPlasseresPåTidslinjeMedSegSelv.opprettet] og [KanPlasseresPåTidslinjeMedSegSelv.copy].
 * Dersom [KanPlasseresPåTidslinjeMedSegSelv.periode] er overlappende for to elementer, vil elementet med nyeste [KanPlasseresPåTidslinjeMedSegSelv.opprettet]
 * få presedens og følgelig overskrive det første elementet for perioden som overlapper.
 * Perioder uten overlapp for elementer forblir uberørt.
 * Merk at tidslinjen kan ha hull.
 *
 * @property periode Denne perioden vil strekke seg fra første til siste utbetalingsmåned. Merk at den kan ha hull, så funksjoner som gjeldendeForDato og krymp kan gi null.
 *
 * @see KanPlasseresPåTidslinjeMedSegSelv
 * @see KanPlasseresPåTidslinje
 */
class Tidslinje<T : KanPlasseresPåTidslinjeMedSegSelv<T>> private constructor(
    private val tidslinjeperioder: NonEmptyList<T>,
) : List<T> by tidslinjeperioder {

    private val periode = this.tidslinjeperioder.map { it.periode }.minAndMaxOf()

    init {
        valider(this.tidslinjeperioder)
    }

    fun gjeldendeForDato(dato: LocalDate): T? = this.firstOrNull { dato.between(it.periode) }

    /**
     * En variant av 'copy' som kopierer innholdet i tidslinjen, men krymper på perioden
     * @return Dersom perioden som sendes inn ikke finnes i tidslinjen, så null
     */
    fun krympTilPeriode(
        periodenDetSkalKrympesTil: Periode,
    ): Tidslinje<T>? {
        return this.tidslinjeperioder.mapNotNull {
            if (periodenDetSkalKrympesTil inneholder it.periode) {
                it
            } else if (periodenDetSkalKrympesTil overlapper it.periode) {
                it.copy(CopyArgs.Tidslinje.NyPeriode(periodenDetSkalKrympesTil.snitt(it.periode)!!))
            } else {
                null
            }
        }.let {
            it.toNonEmptyListOrNull()?.let {
                Tidslinje(it)
            }
        }
    }

    /**
     * [krympTilPeriode]
     */
    fun krympTilPeriode(fraOgMed: Måned): Tidslinje<T>? =
        krympTilPeriode(Periode.create(fraOgMed.fraOgMed, periode.tilOgMed))

    companion object {

        @JvmName("lagTidslinjeNel")
        fun <T : KanPlasseresPåTidslinjeMedSegSelv<T>> NonEmptyList<T>.lagTidslinje(): Tidslinje<T> {
            return lagTidslinje(this)
        }

        fun <T : KanPlasseresPåTidslinjeMedSegSelv<T>> List<T>.lagTidslinje(): Tidslinje<T>? {
            return this.toNonEmptyListOrNull()?.let {
                lagTidslinje(it)
            }
        }

        fun <T : KanPlasseresPåTidslinjeMedSegSelv<T>> lagTidslinje(
            input: NonEmptyList<T>,
        ): Tidslinje<T> {
            val delvisOverlappende = input.filtrerVekkAlleSomOverskivesFullstendigAvNyere()

            val periodisert = periodiser(delvisOverlappende)
            return Tidslinje(
                tidslinjeperioder = periodisert.sortedWith(stigendeFraOgMed()).toNonEmptyList(),
            )
        }

        private fun <T : KanPlasseresPåTidslinjeMedSegSelv<T>> NonEmptyList<T>.filtrerVekkAlleSomOverskivesFullstendigAvNyere(): NonEmptyList<T> {
            return filterNot { t1 ->
                any { t2 -> t1.opprettet.instant < t2.opprettet.instant && t2.periode.inneholder(t1.periode) }
            }.toNonEmptyList()
        }

        private fun <T : KanPlasseresPåTidslinjeMedSegSelv<T>> stigendeFraOgMed(): Comparator<T> {
            return Comparator { o1, o2 -> o1.periode.fraOgMed.compareTo(o2.periode.fraOgMed) }
        }

        private fun <T : KanPlasseresPåTidslinjeMedSegSelv<T>> periodiser(
            tempResult: NonEmptyList<T>,
        ): NonEmptyList<T> {
            val nyesteFørst = Comparator<T> { o1, o2 -> o2.opprettet.instant.compareTo(o1.opprettet.instant) }

            val queue = LinkedList(tempResult.sortedWith(stigendeFraOgMed<T>().then(nyesteFørst)))
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
                                            sikkerLogg.error("Feil ved periodisering i Tidslinje. Objekter er i ugyldig rekkefølge. First er opprettet etter Second. First: ${elementLog(first)}, Second: ${elementLog(second)}, Queue: ${elementLog(queue)}. Result: ${elementLog(result)}")
                                            throw IllegalStateException("Feil ved periodisering i Tidslinje. Objekter er i ugyldig rekkefølge. First er opprettet etter second. Se sikkerlogg.")
                                        }

                                        first.opprettet.instant == second.opprettet.instant -> {
                                            sikkerLogg.error("Feil ved periodisering i Tidslinje. Objekter er i ugyldig rekkefølge. First er opprettet samtidig som Second. First: ${elementLog(first)}, Second: ${elementLog(second)}, Queue: ${elementLog(queue)}. Result: ${elementLog(result)}")
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
                true -> periodiser(result.toNonEmptyList().filtrerVekkAlleSomOverskivesFullstendigAvNyere())
                else -> result.toNonEmptyList()
            }
        }

        private fun <T : KanPlasseresPåTidslinjeMedSegSelv<T>> List<T>.overlappMedAndreEksisterer(): Boolean {
            return any { t1 ->
                any { t2 -> t1 != t2 && t1.periode overlapper t2.periode }
            }
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

        private fun <T : KanPlasseresPåTidslinjeMedSegSelv<T>> elementLog(elementer: List<T>): String {
            return elementer.joinToString(prefix = "[", postfix = "]") { elementLog(it) }
        }
        private fun <T : KanPlasseresPåTidslinjeMedSegSelv<T>> elementLog(element: T): String {
            val periode = element.periode
            val opprettet = element.opprettet
            val type = element::class.simpleName
            val id = when (element) {
                is VedtakPåTidslinje -> element.originaltVedtak.id
                // Legg til flere typer her etterhvert som '?' dukker opp i loggene.
                else -> "?"
            }
            return "{\"type\":\"$type\", \"periode\":\"$periode\", \"opprettet\":\"$opprettet\", \"id\":\"$id\"}"
        }
    }
}
