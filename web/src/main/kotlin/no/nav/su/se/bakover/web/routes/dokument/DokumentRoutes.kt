package no.nav.su.se.bakover.web.routes.dokument

import arrow.core.Either
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.right
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.service.brev.BrevService
import no.nav.su.se.bakover.service.brev.HentDokumenterForIdType
import no.nav.su.se.bakover.web.Resultat
import no.nav.su.se.bakover.web.errorJson
import no.nav.su.se.bakover.web.features.authorize
import no.nav.su.se.bakover.web.parameter
import no.nav.su.se.bakover.web.svar
import no.nav.su.se.bakover.web.toUUID
import java.util.UUID

private const val idParameter = "id"
private const val idTypeParameter = "idType"

internal fun Route.dokumentRoutes(
    brevService: BrevService,
) {
    get("/dokumenter") {
        authorize(Brukerrolle.Saksbehandler) {
            val id = call.parameter(idParameter)
                .getOrHandle {
                    return@authorize call.svar(
                        HttpStatusCode.BadRequest.errorJson(
                            "Parameter '$idParameter' mangler",
                            "mangler_$idParameter",
                        ),
                    )
                }
            val type = call.parameter(idTypeParameter)
                .getOrHandle {
                    return@authorize call.svar(
                        HttpStatusCode.BadRequest.errorJson(
                            "Parameter '$idTypeParameter' mangler",
                            "mangler_$idTypeParameter",
                        ),
                    )
                }

            val parameters = HentDokumentParameters.tryCreate(id, type)
                .getOrHandle { error ->
                    return@authorize when (error) {
                        HentDokumentParameters.Companion.UgyldigParameter.UgyldigType -> {
                            call.svar(
                                HttpStatusCode.BadRequest.errorJson(
                                    "Ugyldig parameter '$idTypeParameter'",
                                    "ugyldig_parameter_$idTypeParameter",
                                ),
                            )
                        }

                        HentDokumentParameters.Companion.UgyldigParameter.UgyldigUUID -> {
                            call.svar(
                                HttpStatusCode.BadRequest.errorJson(
                                    "Ugyldig parameter '$idParameter'",
                                    "ugyldig_parameter_$idParameter",
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
                    .getOrHandle { return UgyldigParameter.UgyldigUUID.left() },
                idType = Either.catch { IdType.valueOf(type.uppercase()) }
                    .getOrHandle { return UgyldigParameter.UgyldigType.left() },
            ).right()
        }

        sealed class UgyldigParameter {
            object UgyldigUUID : UgyldigParameter()
            object UgyldigType : UgyldigParameter()
        }
    }

    fun toDomain(): HentDokumenterForIdType {
        return when (idType) {
            IdType.SAK -> HentDokumenterForIdType.Sak(id)
            IdType.SØKNAD -> HentDokumenterForIdType.Søknad(id)
            IdType.VEDTAK -> HentDokumenterForIdType.Vedtak(id)
            IdType.REVURDERING -> HentDokumenterForIdType.Revurdering(id)
            IdType.KLAGE -> HentDokumenterForIdType.Klage(id)
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
