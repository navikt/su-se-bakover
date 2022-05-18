package no.nav.su.se.bakover.common.periode

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnore
import java.time.LocalDate
import java.time.YearMonth

data class Månedsperiode(
    // Vi ønsker ikke ha denne i json enda, men holder oss til Periode sin fraOgMed og tilOgMed
    @get:JsonIgnore
    val måned: YearMonth,
) : Periode(måned) {
    /** Brukes for å deserialisere fra json */
    @JsonCreator
    constructor(fraOgMed: LocalDate, tilOgMed: LocalDate) : this(YearMonth.of(fraOgMed.year, fraOgMed.month)) {
        require(fraOgMed.year == tilOgMed.year) {
            "fraOgMed og tilOgMed må være innenfor samme år"
        }
        require(fraOgMed.month == tilOgMed.month) {
            "fraOgMed og tilOgMed må være innenfor samme måned"
        }
        validateOrThrow(fraOgMed, tilOgMed)
    }

    operator fun rangeTo(that: Månedsperiode): Periode {
        if (this == that) return this
        return create(this.fraOgMed, that.tilOgMed).also {
            require(this.før(that))
        }
    }

    override fun equals(other: Any?) = super.equals(other)
    override fun hashCode() = super.hashCode()
}

/**
 * @throws IllegalArgumentException dersom denne perioden er lengre enn 1 måned.
 */
fun Periode.toMånedsperiode(): Månedsperiode {
    require(this.getAntallMåneder() == 1)
    return Månedsperiode(YearMonth.of(this.fraOgMed.year, this.fraOgMed.month))
}
