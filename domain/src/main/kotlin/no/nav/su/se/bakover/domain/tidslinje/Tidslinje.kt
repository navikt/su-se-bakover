package no.nav.su.se.bakover.domain.tidslinje

import arrow.core.Nel
import arrow.core.NonEmptyList
import arrow.core.toNonEmptyListOrNull
import no.nav.su.se.bakover.common.application.CopyArgs
import no.nav.su.se.bakover.common.between
import no.nav.su.se.bakover.common.periode.Måned
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.periode.harOverlappende
import no.nav.su.se.bakover.common.periode.minAndMaxOf
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
        Periode.tryCreate(fraOgMed.fraOgMed, periode.tilOgMed).fold(
            { null },
            { krympTilPeriode(it) },
        )

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
            verifyOverlappendeElementerIkkeErOpprettetSamtidig(input)

            // Sorterer slik at det siste elementet (mest aktuelle) kommer først.
            return input.sortedByDescending { it.opprettet.instant }
                .fold(emptyList<T>()) { acc, element ->
                    // Vi vil alltid ha med det første elementet i sin helhet.
                    // Deretter vil vi ha med alle elementer som har en ny periode utover acc sin periode.
                    val inkluderElementer = (element.periode - acc.map { it.periode }).map { nyPeriode ->
                        // Dersom vi skal ha med elementet i sin helhet
                        if (nyPeriode == element.periode) {
                            element.copy(CopyArgs.Tidslinje.Full)
                        } else {
                            // Dersom vi skal ha med elementet kun i en del av perioden
                            element.copy(CopyArgs.Tidslinje.NyPeriode(nyPeriode))
                        }
                    }
                    acc + inkluderElementer
                }.sortedBy { it.periode }
                .toNonEmptyList().let {
                    Tidslinje(it)
                }
        }

        private fun <T : KanPlasseresPåTidslinjeMedSegSelv<T>> verifyOverlappendeElementerIkkeErOpprettetSamtidig(
            input: NonEmptyList<T>,
        ) {
            input
                .sortedBy { it.periode }
                .zipWithNext()
                .forEach { (a, b) ->
                    if (a.periode overlapper b.periode) {
                        check(a.opprettet != b.opprettet) {
                            """
                                Kan ikke lage tidslinje fordi overlappende elementer har samme opprettet tidspunkt:
                                {"periode":"${a.periode}", "opprettet":"${a.opprettet}"} vs.
                                {"periode":"${b.periode}", "opprettet":"${b.opprettet}"}
                            """.trimIndent()
                        }
                    }
                }
        }

        object Validator {
            fun <T : KanPlasseresPåTidslinjeMedSegSelv<T>> valider(elementer: Nel<T>) {
                check(
                    !elementer.map { it.periode }.harOverlappende(),
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
