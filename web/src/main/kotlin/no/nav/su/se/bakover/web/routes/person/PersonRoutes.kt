package no.nav.su.se.bakover.web.routes.person

import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.NotFound
import io.ktor.routing.Route
import io.ktor.routing.get
import no.nav.su.se.bakover.common.Config
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.domain.Person
import no.nav.su.se.bakover.domain.person.PersonOppslag
import no.nav.su.se.bakover.domain.person.PersonOppslag.KunneIkkeHentePerson.FantIkkePerson
import no.nav.su.se.bakover.domain.person.PersonOppslag.KunneIkkeHentePerson.IkkeTilgangTilPerson
import no.nav.su.se.bakover.domain.person.PersonOppslag.KunneIkkeHentePerson.Ukjent
import no.nav.su.se.bakover.web.Resultat
import no.nav.su.se.bakover.web.audit
import no.nav.su.se.bakover.web.lesFnr
import no.nav.su.se.bakover.web.message
import no.nav.su.se.bakover.web.routes.person.PersonResponseJson.Companion.toJson
import no.nav.su.se.bakover.web.svar

internal const val personPath = "/person"

internal fun Route.personRoutes(
    personOppslag: PersonOppslag
) {
    get("$personPath/{fnr}") {
        call.lesFnr("fnr").fold(
            ifLeft = { call.svar(BadRequest.message(it)) },
            ifRight = { fnr ->
                BrenteFnrIOppdragPreprodValidator(Config).assertUbrentFødselsnummerIOppdragPreprod(fnr)

                call.audit("Gjør oppslag på person: $fnr")
                call.svar(
                    personOppslag.person(fnr).fold(
                        {
                            when (it) {
                                FantIkkePerson -> Resultat.message(NotFound, "Fant ikke person")
                                IkkeTilgangTilPerson -> Resultat.message(
                                    HttpStatusCode.Forbidden,
                                    "Ikke tilgang til å se person"
                                )
                                Ukjent -> Resultat.message(
                                    HttpStatusCode.InternalServerError,
                                    "Feil ved oppslag på person"
                                )
                            }
                        },
                        { Resultat.json(HttpStatusCode.OK, objectMapper.writeValueAsString(it.toJson())) }
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
    val adresse: List<AdresseJson>?,
    val statsborgerskap: String?,
    val kjønn: String?,
    val adressebeskyttelse: String?,
    val skjermet: Boolean?,
    val kontaktinfo: KontaktinfoJson?,
    val vergemål: Boolean?,
    val fullmakt: Boolean?
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
        val adresselinje: String?,
        val postnummer: String?,
        val poststed: String?,
        val bruksenhet: String?,
        val kommunenummer: String?,
        val kommunenavn: String?,
        val adressetype: String,
        val adresseformat: String,
    )

    data class KontaktinfoJson(
        val epostadresse: String?,
        val mobiltelefonnummer: String?,
        val reservert: Boolean,
        val kanVarsles: Boolean,
        val språk: String?
    )

    companion object {
        fun Person.toJson() = PersonResponseJson(
            fnr = this.ident.fnr.toString(),
            aktorId = this.ident.aktørId.toString(),
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
            adresse = this.adresse?.map {
                AdresseJson(
                    adresselinje = it.adresselinje,
                    postnummer = it.poststed?.postnummer,
                    poststed = it.poststed?.poststed,
                    bruksenhet = it.bruksenhet,
                    kommunenummer = it.kommune?.kommunenummer,
                    kommunenavn = it.kommune?.kommunenavn,
                    adressetype = it.adressetype,
                    adresseformat = it.adresseformat
                )
            },
            statsborgerskap = this.statsborgerskap,
            kjønn = this.kjønn,
            adressebeskyttelse = this.adressebeskyttelse,
            skjermet = this.skjermet,
            kontaktinfo = this.kontaktinfo?.let {
                KontaktinfoJson(
                    epostadresse = it.epostadresse,
                    mobiltelefonnummer = it.mobiltelefonnummer,
                    reservert = it.reservert,
                    kanVarsles = it.kanVarsles,
                    språk = it.språk
                )
            },
            vergemål = this.vergemål,
            fullmakt = this.fullmakt
        )
    }
}
