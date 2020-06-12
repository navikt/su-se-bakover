package no.nav.su.se.bakover

import io.ktor.application.ApplicationCall
import no.nav.su.se.bakover.Either.*

internal fun Fødselsnummer.Companion.lesParameter(call: ApplicationCall): Either<String, Fødselsnummer> =
    when {
        FNR in call.parameters -> fraString(call.parameters[FNR])
        else -> Left("$FNR må være oppgitt")
    }