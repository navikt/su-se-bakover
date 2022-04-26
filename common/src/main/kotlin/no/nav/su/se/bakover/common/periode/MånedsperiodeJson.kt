package no.nav.su.se.bakover.common.periode

import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class MånedsperiodeJson(
    val fraOgMed: String,
    val tilOgMed: String
) {

    /** @throws IllegalArgumentException dersom [fraOgMed] eller [tilOgMed] ikke kan parses til [Månedsperiode] */
    fun toMånedsperiode(): Månedsperiode {
        return Månedsperiode(LocalDate.parse(fraOgMed), LocalDate.parse(tilOgMed))
    }

    companion object {
        fun Månedsperiode.toJson() = MånedsperiodeJson(fraOgMed.format(DateTimeFormatter.ISO_DATE), tilOgMed.format(DateTimeFormatter.ISO_DATE))
    }
}
