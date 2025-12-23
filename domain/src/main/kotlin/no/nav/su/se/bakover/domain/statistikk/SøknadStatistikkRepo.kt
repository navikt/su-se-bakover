package no.nav.su.se.bakover.domain.statistikk

import no.nav.su.se.bakover.common.tid.Tidspunkt
import java.time.LocalDate
import java.util.UUID

interface SøknadStatistikkRepo {
    fun hentSøknaderAvType(): List<DatapakkeSøknad>
}

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
