package no.nav.su.se.bakover.datapakker.soknad

import no.nav.su.se.bakover.common.tid.Tidspunkt
import java.time.LocalDate
import java.util.UUID

data class DatapakkeSøknad(
    val id: UUID,
    val opprettet: Tidspunkt,
    val type: DatapakkeSøknadstype,
    val mottaksdato: LocalDate,
)

enum class DatapakkeSøknadstype {
    Papirsøknad,
    DigitalSøknad,
    ;

    companion object {
        fun stringToSøknadstype(s: String): DatapakkeSøknadstype {
            return when (s) {
                "Papirsøknad" -> Papirsøknad
                "DigitalSøknad" -> DigitalSøknad
                else -> throw IllegalArgumentException("ukjent type: $s")
            }
        }
    }
}

fun List<DatapakkeSøknad>.toCSV(): String {
    return this.joinToString(separator = "\n") {
        "${it.id},${it.opprettet},${it.type},${it.mottaksdato}"
    }
}
