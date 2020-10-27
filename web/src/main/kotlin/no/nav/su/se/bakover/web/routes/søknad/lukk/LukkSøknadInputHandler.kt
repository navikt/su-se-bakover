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

internal sealed class LukketJson {
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

    data class AvvistJson(
        override val type: Søknad.LukketType,
        val brevInfo: BrevInfoJson? = null
    ) : LukketJson() {
        init {
            require(type == Søknad.LukketType.AVVIST)
        }

        data class BrevInfoJson(
            val typeBrev: LukkSøknadRequest.AvvistSøknad.BrevType,
            val fritekst: String?,
        )
    }
}

internal object LukkSøknadInputHandler {
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
            )

            is LukketJson.BortfaltJson -> LukkSøknadRequest.BortfaltSøknad(
                søknadId = søknadId,
                saksbehandler = saksbehandler
            )
            is LukketJson.AvvistJson -> when (bodyAsJson.brevInfo) {
                null -> LukkSøknadRequest.AvvistSøknad.UtenBrev(
                    søknadId = søknadId,
                    saksbehandler = saksbehandler
                )
                else -> LukkSøknadRequest.AvvistSøknad.MedBrev(
                    søknadId = søknadId,
                    saksbehandler = saksbehandler,
                    brevInfo = LukkSøknadRequest.AvvistSøknad.BrevInfo(
                        typeBrev = bodyAsJson.brevInfo.typeBrev,
                        fritekst = bodyAsJson.brevInfo.fritekst
                    )
                )
            }
        }.right()
    }
}

internal suspend fun deserializeBody(body: String): Either<UgyldigLukkSøknadRequest, LukketJson>? {
    val trukketJson = deserializeLukketSøknadRequest<LukketJson.TrukketJson>(body)
    val bortfalt = deserializeLukketSøknadRequest<LukketJson.BortfaltJson>(body)
    val avvist = deserializeLukketSøknadRequest<LukketJson.AvvistJson>(body)
    return listOf(trukketJson, bortfalt, avvist).singleOrNull { it.isRight() }
}

internal suspend inline fun <reified T> deserializeLukketSøknadRequest(body: String): Either<UgyldigLukkSøknadRequest, T> =
    Either.catch { deserialize<T>(body) }
        .mapLeft { UgyldigLukkSøknadRequest }

internal object UgyldigLukkSøknadRequest
