package no.nav.su.se.bakover.domain.tidslinje

import no.nav.su.se.bakover.common.CopyArgs
import no.nav.su.se.bakover.common.KopierbarForTidslinje
import no.nav.su.se.bakover.common.tid.OriginaltTidsstempel
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.PeriodisertInformasjon
import no.nav.su.se.bakover.common.tid.periode.minsteAntallSammenhengendePerioder

/**
 * Egenskaper som kreves for at et element skal kunne periodiseres av [Tidslinje].
 * Som et minimum må elementet være stand til å kunne plasseres på en [Tidslinje] med bare seg selv og utføre re-periodisering
 * vha. [Tidslinje.periode]
 */
interface KanPlasseresPåTidslinjeMedSegSelv<out Type> :
    OriginaltTidsstempel,
    PeriodisertInformasjon,
    KopierbarForTidslinje<Type>

/**
 * Et syntetisk supersett av [KanPlasseresPåTidslinjeMedSegSelv] hvis intensjon er å markere at elementer av typen [Type] er ment å
 * plasseres på en tidslinje sammen med andre elementer enn seg selv. I praksis betyr dette at det må/bør være meningen
 * at elementer av [Type] med nyere [opprettet] skal overskrive eldre elementer med overlappende [periode].
 * TODO jah: Fjern denne?
 */
interface KanPlasseresPåTidslinje<out Type> : KanPlasseresPåTidslinjeMedSegSelv<Type>

/**
 * Fjerner angitte perioder fra dette objektet
 * @param perioder Periodene som skal fjernes fra dette elementet
 *
 * TODO jah: Her trenger vi egentlig ikke å assosiere med tidslinje. Det hadde holdt med KopierbarForTidslinje og PeriodisertInformasjon
 */
@Suppress("UNCHECKED_CAST")
fun <T> KanPlasseresPåTidslinjeMedSegSelv<T>.fjernPerioder(perioder: List<Periode>): List<T> {
    return when {
        this.periode overlapper perioder -> {
            this.periode.måneder()
                .filterNot { it overlapper perioder }
                .minsteAntallSammenhengendePerioder()
                .map {
                    this.copy(CopyArgs.Tidslinje.NyPeriode(it))
                }
        }

        else -> listOf(this as T)
    }
}
