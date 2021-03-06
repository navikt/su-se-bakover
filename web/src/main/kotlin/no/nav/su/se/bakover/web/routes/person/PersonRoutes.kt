package no.nav.su.se.bakover.web.routes.person

import arrow.core.Either
import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpStatusCode.Companion.NotFound
import io.ktor.routing.Route
import io.ktor.routing.post
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.Person
import no.nav.su.se.bakover.domain.person.KunneIkkeHentePerson.FantIkkePerson
import no.nav.su.se.bakover.domain.person.KunneIkkeHentePerson.IkkeTilgangTilPerson
import no.nav.su.se.bakover.domain.person.KunneIkkeHentePerson.Ukjent
import no.nav.su.se.bakover.service.person.PersonService
import no.nav.su.se.bakover.web.AuditLogEvent
import no.nav.su.se.bakover.web.Resultat
import no.nav.su.se.bakover.web.audit
import no.nav.su.se.bakover.web.routes.Feilresponser
import no.nav.su.se.bakover.web.routes.person.PersonResponseJson.Companion.toJson
import no.nav.su.se.bakover.web.svar
import no.nav.su.se.bakover.web.withBody
import java.time.Clock
import java.time.LocalDate

internal const val personPath = "/person"

internal fun Route.personRoutes(
    personService: PersonService,
    clock: Clock
) {
    post("$personPath/søk") {
        data class Body(
            val fnr: String
        )

        call.withBody<Body> { body ->
            Either.catch { Fnr(body.fnr) }.fold(
                ifLeft = { call.svar(Feilresponser.ikkeGyldigFødselsnummer) },
                ifRight = { fnr ->
                    call.svar(
                        personService.hentPerson(fnr).fold(
                            {
                                call.audit(fnr, AuditLogEvent.Action.SEARCH, null)
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
                            {
                                call.audit(fnr, AuditLogEvent.Action.ACCESS, null)
                                Resultat.json(HttpStatusCode.OK, objectMapper.writeValueAsString(it.toJson(clock)))
                            }
                        )
                    )
                }
            )
        }
    }
}

data class PersonResponseJson(
    val fnr: String,
    val aktorId: String,
    val navn: NavnJson,
    val telefonnummer: TelefonnummerJson?,
    val adresse: List<AdresseJson>?,
    val statsborgerskap: String?,
    val kjønn: String?,
    val fødselsdato: LocalDate?,
    val alder: Number?,
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
        fun Person.toJson(clock: Clock) = PersonResponseJson(
            fnr = this.ident.fnr.toString(),
            aktorId = this.ident.aktørId.toString(),
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
            fødselsdato = this.fødselsdato,
            alder = this.getAlder(LocalDate.now(clock)),
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
