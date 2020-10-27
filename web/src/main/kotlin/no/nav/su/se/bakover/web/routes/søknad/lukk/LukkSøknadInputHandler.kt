package no.nav.su.se.bakover.web.routes.søknad.lukk

import arrow.core.Either
import arrow.core.left
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.service.søknad.LukkSøknadRequest
import java.time.LocalDate
import java.util.UUID

enum class LukketType {
    TRUKKET
}

data class TrukketJson(
    val datoSøkerTrakkSøknad: LocalDate
)

object LukkSøknadInputHandler {
    suspend fun handle(
        type: String?,
        body: String?,
        søknadId: UUID,
        saksbehandler: NavIdentBruker.Saksbehandler
    ): Either<UgyldigLukkSøknadRequest, LukkSøknadRequest> {
        if (type == null || body == null) {
            return UgyldigLukkSøknadRequest.left()
        }
        return Either.catch { LukketType.valueOf(type.toUpperCase()) }.fold(
            { UgyldigLukkSøknadRequest.left() },
            {
                when (it) {
                    LukketType.TRUKKET ->
                        deserializeLukketSøknadRequest<TrukketJson>(body)
                            .map { trukketJson ->
                                LukkSøknadRequest.TrekkSøknad(
                                    søknadId = søknadId,
                                    saksbehandler = saksbehandler,
                                    trukketDato = trukketJson.datoSøkerTrakkSøknad
                                )
                            }
                }
            }
        )
    }
}

suspend inline fun <reified T> deserializeLukketSøknadRequest(body: String): Either<UgyldigLukkSøknadRequest, T> =
    Either.catch { deserialize<T>(body) }
        .mapLeft { UgyldigLukkSøknadRequest }

object UgyldigLukkSøknadRequest
