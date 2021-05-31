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
    abstract val type: Søknad.Lukket.LukketType

    data class TrukketJson(
        override val type: Søknad.Lukket.LukketType,
        val datoSøkerTrakkSøknad: LocalDate
    ) : LukketJson() {
        init {
            require(type == Søknad.Lukket.LukketType.TRUKKET)
        }
    }

    data class BortfaltJson(
        override val type: Søknad.Lukket.LukketType
    ) : LukketJson() {
        init {
            require(type == Søknad.Lukket.LukketType.BORTFALT)
        }
    }

    data class AvvistJson(
        override val type: Søknad.Lukket.LukketType,
        val brevConfig: BrevConfigJson? = null
    ) : LukketJson() {
        init {
            require(type == Søknad.Lukket.LukketType.AVVIST)
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
            ).right()

            is LukketJson.BortfaltJson -> LukkSøknadRequest.UtenBrev.BortfaltSøknad(
                søknadId = søknadId,
                saksbehandler = saksbehandler
            ).right()
            is LukketJson.AvvistJson -> when (bodyAsJson.brevConfig) {
                null -> LukkSøknadRequest.UtenBrev.AvvistSøknad(
                    søknadId = søknadId,
                    saksbehandler = saksbehandler
                ).right()
                else -> {
                    if (bodyAsJson.brevConfig.brevtype == LukketJson.BrevType.FRITEKST && bodyAsJson.brevConfig.fritekst == null) {
                        UgyldigLukkSøknadRequest.left()
                    } else {
                        LukkSøknadRequest.MedBrev.AvvistSøknad(
                            søknadId = søknadId,
                            saksbehandler = saksbehandler,
                            brevConfig = configForType(bodyAsJson.brevConfig)
                        ).right()
                    }
                }
            }
        }
    }

    private fun configForType(brevConfig: LukketJson.AvvistJson.BrevConfigJson): BrevConfig {
        return when (brevConfig.brevtype) {
            LukketJson.BrevType.VEDTAK -> BrevConfig.Vedtak(brevConfig.fritekst)
            // Vi sjekker for null hvis brevet er av typen fritekst på steget over
            LukketJson.BrevType.FRITEKST -> BrevConfig.Fritekst(brevConfig.fritekst!!)
        }
    }
}

internal fun deserializeBody(body: String): Either<UgyldigLukkSøknadRequest, LukketJson> {
    val trukketJson = deserializeLukketSøknadRequest<LukketJson.TrukketJson>(body)
    val bortfalt = deserializeLukketSøknadRequest<LukketJson.BortfaltJson>(body)
    val avvist = deserializeLukketSøknadRequest<LukketJson.AvvistJson>(body)
    return listOf(trukketJson, bortfalt, avvist).filter {
        it.isRight()
    }.let {
        if (it.size != 1) UgyldigLukkSøknadRequest.left() else it.first()
    }
}

internal inline fun <reified T> deserializeLukketSøknadRequest(body: String): Either<UgyldigLukkSøknadRequest, T> =
    Either.catch { deserialize<T>(body) }
        .mapLeft { UgyldigLukkSøknadRequest }

internal object UgyldigLukkSøknadRequest
