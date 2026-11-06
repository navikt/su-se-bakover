package no.nav.su.se.bakover.web.routes.fritekst

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.merge
import arrow.core.raise.either
import arrow.core.right
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.infrastructure.web.authorize
import no.nav.su.se.bakover.common.infrastructure.web.errorJson
import no.nav.su.se.bakover.common.infrastructure.web.svar
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.fritekst.FritekstDomain
import no.nav.su.se.bakover.domain.fritekst.FritekstHentDomain
import no.nav.su.se.bakover.domain.fritekst.FritekstService
import no.nav.su.se.bakover.domain.fritekst.FritekstType
import no.nav.su.se.bakover.web.inputvalidation.withValidertBody
import org.slf4j.LoggerFactory
import java.util.UUID

internal const val FRITEKST_PATH = "fritekst"

data class FritekstRequestLagre(
    val referanseId: String,
    val sakId: String,
    val type: String,
    val fritekst: String,
) {
    fun toDomain(): Either<FeilDatatype, FritekstDomain> {
        val typeEnum = FritekstType.entries.find { it.name == type }
            ?: return FeilDatatype("Friteksttype", type).left()

        val referanseUUID = Either.catch { UUID.fromString(referanseId) }
            .getOrElse { return FeilDatatype("referanseId", referanseId).left() }

        val sakUUID = Either.catch { UUID.fromString(sakId) }
            .getOrElse { return FeilDatatype("sakId", sakId).left() }

        return FritekstDomain(
            referanseId = referanseUUID,
            sakId = sakUUID,
            type = typeEnum,
            fritekst = fritekst,
        ).right()
    }
}

data class FeilDatatype(val field: String, val invalidValue: String) {
    fun asMessage(): String = "Ugyldig verdi for felt '$field': '$invalidValue'"
}

data class FritekstRequestHent(
    val referanseId: String,
    val sakId: String,
    val type: String,
) {
    fun toDomain(): Either<KunneIkkeHenteFritekst, FritekstHentDomain> =
        Either.catch {
            FritekstHentDomain(
                referanseId = UUID.fromString(referanseId),
                sakId = UUID.fromString(sakId),
                type = FritekstType.valueOf(type),
            )
        }.mapLeft {
            KunneIkkeHenteFritekst("Ugyldig input. objekt $it")
        }
}

data class KunneIkkeHenteFritekst(val årsak: String)

internal fun Route.fritekstRoutes(
    fritekstService: FritekstService,
) {
    val log = LoggerFactory.getLogger(this::class.java)

    post(FRITEKST_PATH) {
        authorize(Brukerrolle.Saksbehandler) {
            call.withValidertBody<FritekstRequestHent>(log) { body ->
                either {
                    val hentDomain = body.toDomain()
                        .mapLeft { kunneIkkeHente -> FeilResponser.ugyldigBody(kunneIkkeHente.årsak) }
                        .bind()

                    val fritekst = fritekstService.hentFritekst(hentDomain)
                        .mapLeft { FeilResponser.fantIkkeFritekst }
                        .bind()

                    Resultat.json(HttpStatusCode.OK, serialize(fritekst))
                }
                    .merge()
                    .let { resultat -> call.svar(resultat) }
            }
        }
    }

    post("$FRITEKST_PATH/lagre") {
        authorize(Brukerrolle.Saksbehandler) {
            call.withValidertBody<FritekstRequestLagre>(log) { body ->
                either {
                    val fritekstDomain = body.toDomain()
                        .mapLeft { mappingFeil -> FeilResponser.ugyldigBody(mappingFeil.asMessage()) }
                        .bind()

                    fritekstService.lagreFritekst(fritekstDomain)
                    Resultat.okJson()
                }
                    .merge()
                    .let { resultat -> call.svar(resultat) }
            }
        }
    }
}

data object FeilResponser {
    val fantIkkeFritekst = HttpStatusCode.NotFound.errorJson("Fant ikke fritekst", "fant_ikke_fritekst")
    fun ugyldigBody(message: String): Resultat =
        HttpStatusCode.BadRequest.errorJson(
            message = message,
            code = "ugyldig_body",
        )
}
