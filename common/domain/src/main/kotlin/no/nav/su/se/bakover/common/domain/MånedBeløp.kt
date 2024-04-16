package no.nav.su.se.bakover.common

import no.nav.su.se.bakover.common.domain.norwegianLocale
import no.nav.su.se.bakover.common.domain.tid.periode.IkkeOverlappendePerioder
import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.common.tid.periode.måneder
import java.text.NumberFormat
import java.time.LocalDate
import kotlin.math.abs

/**
 * En sortert liste av månedsbeløp, hvor det kun kan eksistere 1 element for hver måned. Støtter i teorien hull, men usikker på om det er ønskelig.
 * TODO jah: Undersøk om det er ønskelig å støtte hull.
 * @param månedbeløp Månedene kan ikke overlappe og må være sortert.
 */
data class Månedsbeløp(
    val månedbeløp: List<MånedBeløp>,
) : List<MånedBeløp> by månedbeløp {

    /** Periodene i rekkefølgen de er innsendt. Ikke sammenslått. Denne vil gjøre validering av overlapp og sortering. Kan i teorien inneholde hull. */
    val perioder = IkkeOverlappendePerioder.create(månedbeløp.map { it.periode })

    val fraOgMed: LocalDate? = perioder.fraOgMed
    val tilOgMed: LocalDate? = perioder.tilOgMed

    fun sum(): Int {
        return månedbeløp.sumOf { it.beløp.sum() }
    }

    fun måneder(): List<Måned> {
        return perioder.måneder()
    }

    operator fun plus(other: Månedsbeløp): Månedsbeløp {
        return Månedsbeløp(månedbeløp + other.månedbeløp)
    }
}

data class MånedBeløp(
    /** TODO jah; Burde vært renamet til 'måned', men usikker på om den serialiseres til json i database etc. */
    val periode: Måned,
    val beløp: Beløp,
) {
    fun sum(): Int {
        return beløp.sum()
    }
}

@JvmInline
value class Beløp private constructor(
    private val value: Int,
) {
    companion object {
        operator fun invoke(int: Int): Beløp {
            return Beløp(abs(int))
        }

        fun zero(): Beløp {
            return invoke(0)
        }
    }

    operator fun plus(other: Beløp): Beløp {
        return invoke(value + other.value)
    }

    fun sum(): Int {
        return value
    }

    fun tusenseparert(): String {
        return NumberFormat.getNumberInstance(norwegianLocale).format(value)
    }
}
