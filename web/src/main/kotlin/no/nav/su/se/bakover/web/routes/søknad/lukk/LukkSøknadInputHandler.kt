package no.nav.su.se.bakover.web.routes.søknad.lukk

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.service.søknad.LukkSøknadRequest
import java.time.LocalDate
import java.util.UUID

sealed class LukketJson {
    abstract val type: Søknad.LukketType

    data class TrukketJson(
        override val type: Søknad.LukketType,
        val datoSøkerTrakkSøknad: LocalDate
    ) : LukketJson() {
        init {
            require(type == Søknad.LukketType.TRUKKET)
        }
    }

    data class BortfaltJson(
        override val type: Søknad.LukketType
    ) : LukketJson() {
        init {
            require(type == Søknad.LukketType.BORTFALT)
        }
    }
}

object LukkSøknadInputHandler {
    suspend fun handle(
        body: String?,
        søknadId: UUID,
        saksbehandler: NavIdentBruker.Saksbehandler
    ): Either<UgyldigLukkSøknadRequest, LukkSøknadRequest> {
        if (body == null) {
            return UgyldigLukkSøknadRequest.left()
        }

        val bodyAsJson = deserializeBody(body)?.fold(
            { return UgyldigLukkSøknadRequest.left() },
            { it }
        ) ?: return UgyldigLukkSøknadRequest.left()

        return when (bodyAsJson) {
            is LukketJson.TrukketJson -> LukkSøknadRequest.TrekkSøknad(
                søknadId = søknadId,
                saksbehandler = saksbehandler,
                trukketDato = bodyAsJson.datoSøkerTrakkSøknad
            ).right()

            is LukketJson.BortfaltJson -> LukkSøknadRequest.BortfaltSøknad(
                søknadId = søknadId,
                saksbehandler = saksbehandler
            ).right()
        }
    }
}

suspend fun deserializeBody(body: String): Either<UgyldigLukkSøknadRequest, LukketJson>? {
    val trukketJson = deserializeLukketSøknadRequest<LukketJson.TrukketJson>(body)
    val bortfalt = deserializeLukketSøknadRequest<LukketJson.BortfaltJson>(body)
    return listOf(trukketJson, bortfalt).singleOrNull { it.isRight() }
}

suspend inline fun <reified T> deserializeLukketSøknadRequest(body: String): Either<UgyldigLukkSøknadRequest, T> =
    Either.catch { deserialize<T>(body) }
        .mapLeft { UgyldigLukkSøknadRequest }

object UgyldigLukkSøknadRequest
