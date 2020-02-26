package no.nav.su.se.bakover

import io.ktor.application.ApplicationCall

internal class Fødselsnummer(private val fnr: String) {
    override fun toString(): String = fnr
    companion object {
        const val identLabel = "ident"
        //ident
        fun extract(call: ApplicationCall): Either<String, Fødselsnummer> {
            val maybeFnr = call.parameters[identLabel]
            if (maybeFnr == null) return Either.Error("$identLabel må være oppgitt")
            else return fraString(maybeFnr)
        }
        fun fraString(str: String?): Either<String, Fødselsnummer> {
            if (str == null) return Either.Error("Fødselsnummer kan ikke være tomt")
            else if (str.length != 11) return Either.Error("Fødselsnummer må være nøyaktig 11 siffer langt")
            else return Either.Value(Fødselsnummer(str))
        }
    }
}