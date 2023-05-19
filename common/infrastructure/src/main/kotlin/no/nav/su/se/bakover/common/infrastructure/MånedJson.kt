package no.nav.su.se.bakover.common.infrastructure

import no.nav.su.se.bakover.common.tid.periode.Måned
import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class MånedJson(
    val fraOgMed: String,
    val tilOgMed: String,
) {

    /** @throws IllegalArgumentException dersom [fraOgMed] eller [tilOgMed] ikke kan parses til [Måned] */
    fun tilMåned(): Måned {
        return Måned.fra(LocalDate.parse(fraOgMed), LocalDate.parse(tilOgMed))
    }

    companion object {
        fun Måned.toJson() = MånedJson(fraOgMed.format(DateTimeFormatter.ISO_DATE), tilOgMed.format(DateTimeFormatter.ISO_DATE))
    }
}
