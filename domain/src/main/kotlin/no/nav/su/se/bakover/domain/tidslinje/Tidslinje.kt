package no.nav.su.se.bakover.domain.tidslinje

import arrow.core.NonEmptyList
import arrow.core.toNonEmptyListOrNull
import no.nav.su.se.bakover.common.application.CopyArgs
import no.nav.su.se.bakover.common.between
import no.nav.su.se.bakover.common.periode.Måned
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.periode.minAndMaxOf
import no.nav.su.se.bakover.common.periode.minsteAntallSammenhengendePerioder
import no.nav.su.se.bakover.common.toNonEmptyList
import no.nav.su.se.bakover.domain.tidslinje.Tidslinje.Companion.Validator.valider
import no.nav.su.se.bakover.domain.vedtak.VedtakPåTidslinje
import java.time.LocalDate

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
            val sortedByOpprettetSynkende = input.sortedByDescending { it.opprettet.instant }
            val sortedByPeriode = input.sortedBy { it.periode }

            val elementTilMåneder: Map<T, List<Måned>> = sortedByPeriode
                .flatMap { it.periode.måneder() }
                .distinct()
                .groupBy { måned ->
                    sortedByOpprettetSynkende.first { it.periode inneholder måned }
                }

            return sortedByPeriode.flatMap { element ->
                elementTilMåneder[element]
                    ?.minsteAntallSammenhengendePerioder()
                    ?.map { periode ->
                        if (periode == element.periode) {
                            element.copy(CopyArgs.Tidslinje.Full)
                        } else {
                            element.copy(
                                CopyArgs.Tidslinje.NyPeriode(
                                    periode = periode,
                                ),
                            )
                        }
                    } ?: emptyList()
            }.sortedBy { it.periode }.toNonEmptyList().let {
                Tidslinje(it)
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
