package no.nav.su.se.bakover.web.routes.søknad.lukk

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.NavIdentBruker
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.domain.brev.Brevvalg
import no.nav.su.se.bakover.domain.søknad.LukkSøknadCommand
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

internal sealed class LukketJson {
    abstract val type: LukketType

    enum class LukketType {
        AVVIST,
        BORTFALT,
        TRUKKET,
    }

    data class TrukketJson(
        override val type: LukketType,
        val datoSøkerTrakkSøknad: LocalDate,
    ) : LukketJson() {
        init {
            require(type == LukketType.TRUKKET)
        }
    }

    data class BortfaltJson(
        override val type: LukketType,
    ) : LukketJson() {
        init {
            require(type == LukketType.BORTFALT)
        }
    }

    data class AvvistJson(
        override val type: LukketType,
        val brevConfig: BrevConfigJson? = null,
    ) : LukketJson() {
        init {
            require(type == LukketType.AVVIST)
        }

        data class BrevConfigJson(
            val brevtype: BrevType,
            val fritekst: String?,
        )
    }

    enum class BrevType {
        VEDTAK,
        FRITEKST,
    }
}

internal object LukkSøknadInputHandler {
    fun handle(
        body: String?,
        søknadId: UUID,
        saksbehandler: NavIdentBruker.Saksbehandler,
        clock: Clock,
    ): Either<UgyldigLukkSøknadRequest, LukkSøknadCommand> {
        if (body == null) {
            return UgyldigLukkSøknadRequest.left()
        }

        val bodyAsJson = deserializeBody(body).getOrElse {
            return it.left()
        }

        return when (bodyAsJson) {
            is LukketJson.TrukketJson -> LukkSøknadCommand.MedBrev.TrekkSøknad(
                søknadId = søknadId,
                saksbehandler = saksbehandler,
                trukketDato = bodyAsJson.datoSøkerTrakkSøknad,
                lukketTidspunkt = Tidspunkt.now(clock),
            ).right()

            is LukketJson.BortfaltJson -> LukkSøknadCommand.UtenBrev.BortfaltSøknad(
                søknadId = søknadId,
                saksbehandler = saksbehandler,
                lukketTidspunkt = Tidspunkt.now(clock),
            ).right()

            is LukketJson.AvvistJson -> when (bodyAsJson.brevConfig) {
                null -> LukkSøknadCommand.UtenBrev.AvvistSøknad(
                    søknadId = søknadId,
                    saksbehandler = saksbehandler,
                    lukketTidspunkt = Tidspunkt.now(clock),
                ).right()

                else -> {
                    if (bodyAsJson.brevConfig.brevtype == LukketJson.BrevType.FRITEKST && bodyAsJson.brevConfig.fritekst == null) {
                        UgyldigLukkSøknadRequest.left()
                    } else {
                        LukkSøknadCommand.MedBrev.AvvistSøknad(
                            søknadId = søknadId,
                            saksbehandler = saksbehandler,
                            lukketTidspunkt = Tidspunkt.now(clock),
                            brevvalg = toBrevvalg(bodyAsJson.brevConfig),
                        ).right()
                    }
                }
            }
        }
    }

    private fun toBrevvalg(brevConfig: LukketJson.AvvistJson.BrevConfigJson): Brevvalg.SaksbehandlersValg {
        return when (brevConfig.brevtype) {
            LukketJson.BrevType.VEDTAK -> Brevvalg.SaksbehandlersValg.SkalSendeBrev.VedtaksbrevUtenFritekst()
            // Vi sjekker for null hvis brevet er av typen fritekst på steget over
            LukketJson.BrevType.FRITEKST -> Brevvalg.SaksbehandlersValg.SkalSendeBrev.InformasjonsbrevMedFritekst(
                brevConfig.fritekst!!,
            )
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
