package no.nav.su.se.bakover

import no.nav.su.se.bakover.Either.Left
import no.nav.su.se.bakover.Either.Right

class Fødselsnummer(private val fnr: String) {
    override fun toString(): String = fnr

    companion object {
        const val FNR = "fnr"
        private val fnrPattern = Regex("[0-9]{11}")

        fun fraString(str: String?): Either<String, Fødselsnummer> = when {
            str == null -> Left("Fødselsnummer kan ikke være tomt")
            !fnrPattern.matches(str) -> Left("Fødselsnummer må være nøyaktig 11 siffer langt")
            else -> Right(Fødselsnummer(str))
        }
    }
}
