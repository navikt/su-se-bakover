package no.nav.su.se.bakover.web.routes

import io.ktor.application.call
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.routing.Route
import io.ktor.routing.get
import no.nav.su.se.bakover.client.ClientResponse

import no.nav.su.se.bakover.client.person.PersonOppslag
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.web.Resultat
import no.nav.su.se.bakover.web.audit
import no.nav.su.se.bakover.web.lesFnr
import no.nav.su.se.bakover.web.message
import no.nav.su.se.bakover.web.svar

internal const val personPath = "/person"

internal fun Route.personRoutes(
    oppslag: PersonOppslag
) {
    get("$personPath/{fnr}") {
        call.lesFnr("fnr").fold(
            ifLeft = { call.svar(BadRequest.message(it)) },
            ifRight = { fnr ->
                call.audit("Gjør oppslag på person: $fnr")
                call.svar(
                    Resultat.from(
                        oppslag.person(fnr).fold(
                            { ClientResponse(it.httpStatus, it.message) },
                            {
                                ClientResponse(
                                    200,
                                    objectMapper.writeValueAsString(
                                        PersonResponseJson(
                                            fnr = it.fnr.toString(),
                                            aktorId = it.aktørId.aktørId,
                                            fornavn = it.fornavn,
                                            mellomnavn = it.mellomnavn,
                                            etternavn = it.etternavn
                                        )
                                    )
                                )
                            }
                        )
                    )
                )
            }
        )
    }
}

data class PersonResponseJson(
    val fnr: String,
    val aktorId: String,
    val fornavn: String,
    val mellomnavn: String?,
    val etternavn: String
)
