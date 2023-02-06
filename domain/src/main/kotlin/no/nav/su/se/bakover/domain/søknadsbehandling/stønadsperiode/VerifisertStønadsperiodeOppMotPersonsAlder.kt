package no.nav.su.se.bakover.domain.søknadsbehandling.stønadsperiode

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.person.Person
import org.jetbrains.annotations.TestOnly
import java.time.Clock
import java.time.LocalDate
import java.time.Year

data class VerifisertStønadsperiodeOppMotPersonsAlder private constructor(
    val stønadsperiode: Stønadsperiode,
    val verifiseringsMelding: VerifiseringsMelding,
) {
    companion object {

        @TestOnly
        fun create(stønadsperiode: Stønadsperiode, verifiseringsMelding: VerifiseringsMelding) =
            VerifisertStønadsperiodeOppMotPersonsAlder(stønadsperiode, verifiseringsMelding)

        fun verifiser(
            stønadsperiode: Stønadsperiode,
            person: Person?,
            clock: Clock,
        ): Either<Valideringsfeil, VerifisertStønadsperiodeOppMotPersonsAlder> {
            return when (person) {
                null -> VerifisertStønadsperiodeOppMotPersonsAlder(
                    stønadsperiode,
                    VerifiseringsMelding.KunneIkkeVerifisereMotPerson,
                ).right()

                else -> verifiserMotPerson(stønadsperiode, person, clock)
            }
        }

        private fun verifiserMotPerson(
            stønadsperiode: Stønadsperiode,
            person: Person,
            clock: Clock,
        ): Either<Valideringsfeil, VerifisertStønadsperiodeOppMotPersonsAlder> {
            if (person.harFødselsInformasjon()) {
                return verifiserMotFødselsinformasjon(person, stønadsperiode, clock)
            }
            return VerifisertStønadsperiodeOppMotPersonsAlder(
                stønadsperiode,
                VerifiseringsMelding.KunneIkkeVerifisereStønadsperiodeMotFødselsinformasjon,
            ).right()
        }

        private fun verifiserMotFødselsinformasjon(
            person: Person,
            stønadsperiode: Stønadsperiode,
            clock: Clock,
        ): Either<Valideringsfeil, VerifisertStønadsperiodeOppMotPersonsAlder> {
            val detteÅret = Year.now(clock)
            if (person.eldreEnn67(detteÅret) == true) {
                return Valideringsfeil.PersonEr67EllerEldre.left()
            }
            if (person.blir67(detteÅret) == true) {
                if (person.harFødselsdato()) {
                    return begrensStønadsperiodeVedBrukAvFødselsdato(person.fødsel?.dato!!, stønadsperiode).right()
                }

                return VerifisertStønadsperiodeOppMotPersonsAlder(
                    stønadsperiode,
                    VerifiseringsMelding.KunneIkkeVerifisereMotDetaljertFødselsinformasjon,
                ).right()
            }
            return VerifisertStønadsperiodeOppMotPersonsAlder(
                stønadsperiode,
                VerifiseringsMelding.VerifisertOkPersonFyllerIkke67Plus,
            ).right()
        }

        private fun begrensStønadsperiodeVedBrukAvFødselsdato(
            dato: LocalDate,
            stønadsperiode: Stønadsperiode,
        ): VerifisertStønadsperiodeOppMotPersonsAlder {
            val nyTilOgMed =
                LocalDate.of(stønadsperiode.periode.tilOgMed.year, dato.month, dato.month.length(dato.isLeapYear))
            return VerifisertStønadsperiodeOppMotPersonsAlder(
                Stønadsperiode.create(Periode.create(stønadsperiode.periode.fraOgMed, nyTilOgMed)),
                VerifiseringsMelding.HarBegrensetStønadsperiode,
            )
        }
    }
}

sealed interface Valideringsfeil {
    object PersonEr67EllerEldre : Valideringsfeil
}

sealed interface VerifiseringsMelding {
    object KunneIkkeVerifisereMotPerson : VerifiseringsMelding
    object HarBegrensetStønadsperiode : VerifiseringsMelding
    object KunneIkkeVerifisereMotDetaljertFødselsinformasjon : VerifiseringsMelding
    object KunneIkkeVerifisereStønadsperiodeMotFødselsinformasjon : VerifiseringsMelding
    object VerifisertOkPersonFyllerIkke67Plus : VerifiseringsMelding
}
