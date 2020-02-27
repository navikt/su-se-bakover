package no.nav.su.se.bakover

import io.ktor.application.ApplicationCall
import no.nav.su.se.bakover.Either.*

internal class Fødselsnummer(private val fnr: String) {
    override fun toString(): String = fnr
    companion object {
        const val identLabel = "ident"
        fun lesParameter(call: ApplicationCall): Either<String, Fødselsnummer> =
            when {
                identLabel in call.parameters -> fraString(call.parameters[identLabel])
                else -> Left("$identLabel må være oppgitt")
            }
        fun fraString(str: String?): Either<String, Fødselsnummer> = when {
            str == null -> Left("Fødselsnummer kan ikke være tomt")
            str.length != 11 -> Left("Fødselsnummer må være nøyaktig 11 siffer langt")
            else -> Right(Fødselsnummer(str))
        }
    }
}