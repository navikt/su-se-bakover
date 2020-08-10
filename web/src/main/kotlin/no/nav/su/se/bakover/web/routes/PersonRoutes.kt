package no.nav.su.se.bakover.web.routes

import io.ktor.application.call
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.routing.Route
import io.ktor.routing.get
import no.nav.su.se.bakover.client.ClientResponse
import no.nav.su.se.bakover.client.person.PersonOppslag
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.domain.Person
import no.nav.su.se.bakover.web.Resultat
import no.nav.su.se.bakover.web.audit
import no.nav.su.se.bakover.web.lesFnr
import no.nav.su.se.bakover.web.message
import no.nav.su.se.bakover.web.routes.PersonResponseJson.Companion.toJson
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
                                        it.toJson()
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
    val fornavn: String, // deprecated
    val mellomnavn: String?, // deprecated
    val etternavn: String, // deprecated
    val navn: NavnJson,
    val telefonnummer: TelefonnummerJson?,
    val adresse: AdresseJson?,
    val statsborgerskap: String?,
    val kjønn: String?
) {
    data class NavnJson(
        val fornavn: String,
        val mellomnavn: String?,
        val etternavn: String
    )

    data class TelefonnummerJson(
        val landskode: String,
        val nummer: String
    )

    data class AdresseJson(
        val adressenavn: String?,
        val husnummer: String?,
        val husbokstav: String?,
        val postnummer: String?,
        val poststed: String?,
        val bruksenhet: String?,
        val kommunenummer: String?,
        val kommunenavn: String?
    )

    companion object {
        fun Person.toJson() = PersonResponseJson(
            fnr = this.fnr.toString(),
            aktorId = this.aktørId.aktørId,
            fornavn = this.navn.fornavn,
            mellomnavn = this.navn.mellomnavn,
            etternavn = this.navn.etternavn,
            navn = NavnJson(
                fornavn = this.navn.fornavn,
                mellomnavn = this.navn.mellomnavn,
                etternavn = this.navn.etternavn
            ),
            telefonnummer = this.telefonnummer?.let { t ->
                TelefonnummerJson(
                    landskode = t.landskode,
                    nummer = t.nummer
                )
            },
            adresse = this.adresse?.let {
                AdresseJson(
                    adressenavn = it.adressenavn,
                    husnummer = it.husnummer,
                    husbokstav = it.husbokstav,
                    postnummer = it.postnummer,
                    poststed = it.poststed,
                    bruksenhet = it.bruksenhet,
                    kommunenummer = it.kommunenummer,
                    kommunenavn = it.kommunenavn
                )
            },
            statsborgerskap = this.statsborgerskap,
            kjønn = this.kjønn
        )
    }
}
