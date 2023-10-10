package no.nav.su.se.bakover.domain.søknadsbehandling.stønadsperiode

import no.nav.su.se.bakover.common.tid.Tidspunkt
import person.domain.Person
import java.time.Clock
import java.time.LocalDate
import java.time.Year

data class Aldersinformasjon private constructor(
    val alder: Int?,
    val alderSøkerFyllerIÅr: Int?,
    val alderPåTidspunkt: Tidspunkt?,
) {
    companion object {
        fun createAldersinformasjon(
            person: Person,
            clock: Clock,
        ): Aldersinformasjon {
            val alderSøkerFyllerIÅr = person.alderSomFylles(Year.now(clock))
            return Aldersinformasjon(
                alder = person.getAlder(LocalDate.now(clock)),
                alderSøkerFyllerIÅr = alderSøkerFyllerIÅr,
                alderPåTidspunkt = alderSøkerFyllerIÅr?.let { Tidspunkt.now(clock) },
            )
        }

        fun createFromExisting(
            alder: Int?,
            alderSøkerFyllerIÅr: Int?,
            alderPåTidspunkt: Tidspunkt?,
        ): Aldersinformasjon = Aldersinformasjon(alder, alderSøkerFyllerIÅr, alderPåTidspunkt)
    }
}
