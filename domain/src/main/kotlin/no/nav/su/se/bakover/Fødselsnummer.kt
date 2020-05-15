package no.nav.su.se.bakover

import no.nav.su.se.bakover.Either.Left
import no.nav.su.se.bakover.Either.Right

class Fødselsnummer(private val fnr: String) {
    override fun toString(): String = fnr

    companion object {
        const val identLabel = "ident"

        fun fraString(str: String?): Either<String, Fødselsnummer> = when {
            str == null -> Left("Fødselsnummer kan ikke være tomt")
            str.length != 11 -> Left("Fødselsnummer må være nøyaktig 11 siffer langt")
            else -> Right(Fødselsnummer(str))
        }
    }
}
