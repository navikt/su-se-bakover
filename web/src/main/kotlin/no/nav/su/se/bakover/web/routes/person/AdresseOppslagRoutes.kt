package no.nav.su.se.bakover.web.routes.person

import arrow.core.Either
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import no.nav.su.se.bakover.client.regoppslag.RegoppslagFeil
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.infrastructure.web.errorJson
import no.nav.su.se.bakover.common.infrastructure.web.svar
import no.nav.su.se.bakover.common.infrastructure.web.withBody
import no.nav.su.se.bakover.common.infrastructure.web.withSakId
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.sak.SakService
import no.nav.su.se.bakover.service.regoppslag.RegoppslagService

internal const val ADRESSE_OPPSLAG_PATH = "/adresse-oppslag"

internal fun Route.adresseOppslagRoutes(
    regoppslagService: RegoppslagService,
    sakService: SakService,
) {
    post("$ADRESSE_OPPSLAG_PATH/{sakId}/sjekkAdresse") {
        data class Body(
            val fnr: String,
        )
        call.withSakId { sakId ->
            call.withBody<Body> { body ->
                Either.catch { Fnr(body.fnr) }.fold(
                    ifLeft = {
                        call.svar(
                            HttpStatusCode.BadRequest.errorJson(
                                "Inneholder ikke et gyldig fødselsnummer",
                                "ikke_gyldig_fødselsnummer",
                            ),
                        )
                    },
                    ifRight = { fnr ->

                        sakService.hentSakInfo(sakId).fold(
                            ifLeft = { feil ->
                                call.svar(
                                    Resultat.json(
                                        HttpStatusCode.NotFound,
                                        serialize(
                                            mapOf(
                                                "status" to HttpStatusCode.NotFound.value,
                                                "code" to "FANT_IKKE_SAK",
                                                "detail" to "Fant ikke sak",
                                            ),
                                        ),
                                    ),
                                )
                            },
                            ifRight = { sakInfo ->
                                call.svar(
                                    regoppslagService.hentMottakerAdresse(sakInfo.type, fnr).fold(
                                        ifLeft = { feil -> feil.tilResultat() },
                                        ifRight = { response -> Resultat.json(HttpStatusCode.OK, serialize(response)) },
                                    ),
                                )
                            },
                        )
                    },
                )
            }
        }
    }
}

internal fun RegoppslagFeil.tilResultat(): Resultat {
    return when (this) {
        RegoppslagFeil.IkkeFunnet -> Resultat.json(
            HttpStatusCode.NotFound,
            serialize(
                mapOf(
                    "status" to HttpStatusCode.NotFound.value,
                    "code" to "BRUKER_HAR_UKJENT_ADRESSE",
                    "detail" to "Bruker har ukjent adresse",
                ),
            ),
        )
        RegoppslagFeil.PersonErDød -> Resultat.json(
            HttpStatusCode.Gone,
            serialize(
                mapOf(
                    "status" to HttpStatusCode.Gone.value,
                    "code" to "PERSON_ER_DØD",
                    "detail" to "Person er død og har ukjent adresse",
                ),
            ),
        )
        is RegoppslagFeil.UkjentFeil -> Resultat.json(
            HttpStatusCode.InternalServerError,
            serialize(
                mapOf(
                    "status" to this.statusCode,
                    "code" to "UKJENT_FEIL_REGOPPSLAG",
                    "detail" to this.detail,
                ),
            ),
        )
    }
}
