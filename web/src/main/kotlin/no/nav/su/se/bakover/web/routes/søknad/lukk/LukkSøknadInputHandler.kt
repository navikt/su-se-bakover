package no.nav.su.se.bakover.web.routes.søknad.lukk

import arrow.core.Either
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.brev.BrevConfig
import no.nav.su.se.bakover.domain.søknad.LukkSøknadRequest
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
        val brevConfig: BrevConfigJson? = null
    ) : LukketJson() {
        init {
            require(type == Søknad.LukketType.AVVIST)
        }

        data class BrevConfigJson(
            val brevtype: BrevType,
            val fritekst: String?
        )
    }

    enum class BrevType {
        VEDTAK,
        FRITEKST,
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

        val bodyAsJson = deserializeBody(body).getOrHandle {
            return it.left()
        }

        return when (bodyAsJson) {
            is LukketJson.TrukketJson -> LukkSøknadRequest.MedBrev.TrekkSøknad(
                søknadId = søknadId,
                saksbehandler = saksbehandler,
                trukketDato = bodyAsJson.datoSøkerTrakkSøknad
            )

            is LukketJson.BortfaltJson -> LukkSøknadRequest.UtenBrev.BortfaltSøknad(
                søknadId = søknadId,
                saksbehandler = saksbehandler
            )
            is LukketJson.AvvistJson -> when (bodyAsJson.brevConfig) {
                null -> LukkSøknadRequest.UtenBrev.AvvistSøknad(
                    søknadId = søknadId,
                    saksbehandler = saksbehandler
                )
                else -> LukkSøknadRequest.MedBrev.AvvistSøknad(
                    søknadId = søknadId,
                    saksbehandler = saksbehandler,
                    brevConfig = configForType(bodyAsJson.brevConfig)
                )
            }
        }.right()
    }

    private fun configForType(brevConfig: LukketJson.AvvistJson.BrevConfigJson): BrevConfig {
        return when (brevConfig.fritekst) {
            null -> BrevConfig.Vedtak
            else -> BrevConfig.Fritekst(brevConfig.fritekst)
        }
    }
}

internal suspend fun deserializeBody(body: String): Either<UgyldigLukkSøknadRequest, LukketJson> {
    val trukketJson = deserializeLukketSøknadRequest<LukketJson.TrukketJson>(body)
    val bortfalt = deserializeLukketSøknadRequest<LukketJson.BortfaltJson>(body)
    val avvist = deserializeLukketSøknadRequest<LukketJson.AvvistJson>(body)
    return listOf(trukketJson, bortfalt, avvist).filter {
        it.isRight()
    }.let {
        if (it.size != 1) UgyldigLukkSøknadRequest.left() else it.first()
    }
}

internal suspend inline fun <reified T> deserializeLukketSøknadRequest(body: String): Either<UgyldigLukkSøknadRequest, T> =
    Either.catch { deserialize<T>(body) }
        .mapLeft { UgyldigLukkSøknadRequest }

internal object UgyldigLukkSøknadRequest
