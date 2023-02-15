package no.nav.su.se.bakover.web.routes.person

import arrow.core.Either
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import no.nav.su.se.bakover.common.Fnr
import no.nav.su.se.bakover.common.audit.application.AuditLogEvent
import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.infrastructure.web.audit
import no.nav.su.se.bakover.common.infrastructure.web.errorJson
import no.nav.su.se.bakover.common.infrastructure.web.svar
import no.nav.su.se.bakover.common.infrastructure.web.withBody
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.domain.person.KunneIkkeHentePerson.FantIkkePerson
import no.nav.su.se.bakover.domain.person.KunneIkkeHentePerson.IkkeTilgangTilPerson
import no.nav.su.se.bakover.domain.person.KunneIkkeHentePerson.Ukjent
import no.nav.su.se.bakover.domain.person.Person
import no.nav.su.se.bakover.domain.person.PersonService
import no.nav.su.se.bakover.web.routes.person.PersonResponseJson.Companion.toJson
import java.time.Clock
import java.time.LocalDate

internal const val personPath = "/person"

internal fun Route.personRoutes(
    personService: PersonService,
    clock: Clock,
) {
    post("$personPath/søk") {
        data class Body(
            val fnr: String,
        )

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
                    call.svar(
                        personService.hentPerson(fnr).fold(
                            {
                                call.audit(fnr, AuditLogEvent.Action.SEARCH, null)
                                when (it) {
                                    FantIkkePerson -> Feilresponser.fantIkkePerson
                                    IkkeTilgangTilPerson -> Feilresponser.ikkeTilgangTilPerson
                                    Ukjent -> Feilresponser.feilVedOppslagPåPerson
                                }
                            },
                            {
                                call.audit(fnr, AuditLogEvent.Action.ACCESS, null)
                                Resultat.json(HttpStatusCode.OK, objectMapper.writeValueAsString(it.toJson(clock)))
                            },
                        ),
                    )
                },
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
    val sivilstand: SivilstandJson?,
    val kjønn: String?,
    val fødsel: Fødsel?,
    val adressebeskyttelse: String?,
    val skjermet: Boolean?,
    val kontaktinfo: KontaktinfoJson?,
    val vergemål: Boolean?,
    val fullmakt: Boolean?,
    val dødsdato: LocalDate?,
) {
    data class NavnJson(
        val fornavn: String,
        val mellomnavn: String?,
        val etternavn: String,
    )

    data class TelefonnummerJson(
        val landskode: String,
        val nummer: String,
    )

    data class Fødsel(
        val dato: LocalDate?,
        val år: Int,
        val alder: Int?,
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
        val språk: String?,
        val kanKontaktesDigitalt: Boolean,
    )

    data class SivilstandJson(
        val type: String,
        val relatertVedSivilstand: String?,
    )

    companion object {
        fun Person.toJson(clock: Clock) = PersonResponseJson(
            fnr = this.ident.fnr.toString(),
            aktorId = this.ident.aktørId.toString(),
            navn = NavnJson(
                fornavn = this.navn.fornavn,
                mellomnavn = this.navn.mellomnavn,
                etternavn = this.navn.etternavn,
            ),
            telefonnummer = this.telefonnummer?.let { t ->
                TelefonnummerJson(
                    landskode = t.landskode,
                    nummer = t.nummer,
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
                    adresseformat = it.adresseformat,
                )
            },
            statsborgerskap = this.statsborgerskap,
            kjønn = this.kjønn,
            fødsel = when (fødsel) {
                is Person.Fødsel.MedFødselsdato -> Fødsel(
                    dato = (fødsel as Person.Fødsel.MedFødselsdato).dato,
                    år = (fødsel as Person.Fødsel.MedFødselsdato).år.value,
                    alder = (fødsel as Person.Fødsel.MedFødselsdato).getAlder(LocalDate.now(clock)),
                )

                is Person.Fødsel.MedFødselsår -> Fødsel(
                    dato = null,
                    år = (fødsel as Person.Fødsel.MedFødselsår).år.value,
                    alder = null,
                )

                null -> null
            },
            adressebeskyttelse = this.adressebeskyttelse,
            sivilstand = this.sivilstand?.let { sivilstand ->
                SivilstandJson(
                    type = sivilstand.type.toString(),
                    relatertVedSivilstand = sivilstand.relatertVedSivilstand?.toString(),
                )
            },
            skjermet = this.skjermet,
            kontaktinfo = this.kontaktinfo?.let {
                KontaktinfoJson(
                    epostadresse = it.epostadresse,
                    mobiltelefonnummer = it.mobiltelefonnummer,
                    språk = it.språk,
                    kanKontaktesDigitalt = it.kanKontaktesDigitalt,
                )
            },
            vergemål = this.vergemål,
            fullmakt = this.fullmakt,
            dødsdato = this.dødsdato,
        )
    }
}
