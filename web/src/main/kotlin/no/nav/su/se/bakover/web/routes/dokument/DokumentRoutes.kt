package no.nav.su.se.bakover.web.routes.dokument

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import dokument.domain.brev.BrevService
import dokument.domain.brev.HentDokumenterForIdType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.infrastructure.web.authorize
import no.nav.su.se.bakover.common.infrastructure.web.errorJson
import no.nav.su.se.bakover.common.infrastructure.web.parameter
import no.nav.su.se.bakover.common.infrastructure.web.svar
import no.nav.su.se.bakover.common.infrastructure.web.toUUID
import no.nav.su.se.bakover.common.serialize
import java.util.UUID

private const val ID_PARAMETER = "id"
private const val ID_TYPE_PARAMETER = "idType"

internal fun Route.dokumentRoutes(
    brevService: BrevService,
) {
    get("/dokumenter") {
        authorize(Brukerrolle.Saksbehandler) {
            val id = call.parameter(ID_PARAMETER)
                .getOrElse {
                    return@authorize call.svar(
                        HttpStatusCode.BadRequest.errorJson(
                            "Parameter '$ID_PARAMETER' mangler",
                            "mangler_$ID_PARAMETER",
                        ),
                    )
                }
            val type = call.parameter(ID_TYPE_PARAMETER)
                .getOrElse {
                    return@authorize call.svar(
                        HttpStatusCode.BadRequest.errorJson(
                            "Parameter '$ID_TYPE_PARAMETER' mangler",
                            "mangler_$ID_TYPE_PARAMETER",
                        ),
                    )
                }

            val parameters = HentDokumentParameters.tryCreate(id, type)
                .getOrElse { error ->
                    return@authorize when (error) {
                        HentDokumentParameters.Companion.UgyldigParameter.UgyldigType -> {
                            call.svar(
                                HttpStatusCode.BadRequest.errorJson(
                                    "Ugyldig parameter '$ID_TYPE_PARAMETER'",
                                    "ugyldig_parameter_$ID_TYPE_PARAMETER",
                                ),
                            )
                        }

                        HentDokumentParameters.Companion.UgyldigParameter.UgyldigUUID -> {
                            call.svar(
                                HttpStatusCode.BadRequest.errorJson(
                                    "Ugyldig parameter '$ID_PARAMETER'",
                                    "ugyldig_parameter_$ID_PARAMETER",
                                ),
                            )
                        }
                    }
                }

            brevService.hentDokumenterFor(parameters.toDomain())
                .let { dokumenter ->
                    call.svar(
                        Resultat.json(
                            httpCode = HttpStatusCode.OK,
                            json = serialize(dokumenter.toJson()),
                        ),
                    )
                }
        }
    }
}

private data class HentDokumentParameters(
    val id: UUID,
    val idType: IdType,
) {
    companion object {
        fun tryCreate(id: String, type: String): Either<UgyldigParameter, HentDokumentParameters> {
            return HentDokumentParameters(
                id = id.toUUID()
                    .getOrElse { return UgyldigParameter.UgyldigUUID.left() },
                idType = Either.catch { IdType.valueOf(type.uppercase()) }
                    .getOrElse { return UgyldigParameter.UgyldigType.left() },
            ).right()
        }

        sealed class UgyldigParameter {
            data object UgyldigUUID : UgyldigParameter()
            data object UgyldigType : UgyldigParameter()
        }
    }

    fun toDomain(): HentDokumenterForIdType {
        return when (idType) {
            IdType.SAK -> HentDokumenterForIdType.HentDokumenterForSak(id)
            IdType.SØKNAD -> HentDokumenterForIdType.HentDokumenterForSøknad(id)
            IdType.VEDTAK -> HentDokumenterForIdType.HentDokumenterForVedtak(id)
            IdType.REVURDERING -> HentDokumenterForIdType.HentDokumenterForRevurdering(id)
            IdType.KLAGE -> HentDokumenterForIdType.HentDokumenterForKlage(id)
        }
    }
}

private enum class IdType {
    SAK,
    SØKNAD,
    VEDTAK,
    REVURDERING,
    KLAGE,
}
