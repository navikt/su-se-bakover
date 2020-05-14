package no.nav.su.se.bakover

import io.ktor.application.ApplicationCall
import no.nav.su.se.bakover.Either.*

internal fun Fødselsnummer.Companion.lesParameter(call: ApplicationCall): Either<String, Fødselsnummer> =
    when {
        identLabel in call.parameters -> fraString(call.parameters[identLabel])
        else -> Left("$identLabel må være oppgitt")
    }