package no.nav.su.se.bakover.web.routes.person

import arrow.core.Either
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import no.nav.su.se.bakover.common.audit.AuditLogEvent
import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.infrastructure.web.audit
import no.nav.su.se.bakover.common.infrastructure.web.errorJson
import no.nav.su.se.bakover.common.infrastructure.web.svar
import no.nav.su.se.bakover.common.infrastructure.web.withBody
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.web.routes.person.PersonResponseJson.Companion.toJson
import person.domain.KunneIkkeHentePerson
import person.domain.KunneIkkeHentePerson.FantIkkePerson
import person.domain.KunneIkkeHentePerson.IkkeTilgangTilPerson
import person.domain.KunneIkkeHentePerson.Ukjent
import person.domain.Person
import person.domain.PersonMedSkjermingOgKontaktinfo
import person.domain.PersonService
import java.time.Clock
import java.time.LocalDate

internal const val PERSON_PATH = "/person"

internal fun Route.personRoutes(
    personService: PersonService,
    clock: Clock,
) {
    post("$PERSON_PATH/søk") {
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
                        personService.hentPersonMedSkjermingOgKontaktinfo(fnr).fold(
                            {
                                call.audit(fnr, AuditLogEvent.Action.SEARCH, null)
                                it.tilResultat()
                            },
                            {
                                call.audit(fnr, AuditLogEvent.Action.ACCESS, null)
                                Resultat.json(HttpStatusCode.OK, serialize(it.toJson(clock)))
                            },
                        ),
                    )
                },
            )
        }
    }
}

internal fun KunneIkkeHentePerson.tilResultat(): Resultat {
    return when (this) {
        FantIkkePerson -> Feilresponser.fantIkkePerson
        IkkeTilgangTilPerson -> Feilresponser.ikkeTilgangTilPerson
        Ukjent -> Feilresponser.feilVedOppslagPåPerson
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
    val fødsel: Fødsel?,
    val adressebeskyttelse: String?,
    val skjermet: Boolean?,
    val kontaktinfo: KontaktinfoJson?,
    val vergemål: Boolean?,
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
        fun PersonMedSkjermingOgKontaktinfo.toJson(clock: Clock) = PersonResponseJson(
            fnr = this.person.ident.fnr.toString(),
            aktorId = this.person.ident.aktørId.toString(),
            navn = NavnJson(
                fornavn = this.person.navn.fornavn,
                mellomnavn = this.person.navn.mellomnavn,
                etternavn = this.person.navn.etternavn,
            ),
            telefonnummer = this.person.telefonnummer?.let { t ->
                TelefonnummerJson(
                    landskode = t.landskode,
                    nummer = t.nummer,
                )
            },
            adresse = this.person.adresse?.map {
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
            statsborgerskap = this.person.statsborgerskap,
            sivilstand = this.person.sivilstand?.let { sivilstand ->
                SivilstandJson(
                    type = sivilstand.type.toString(),
                    relatertVedSivilstand = sivilstand.relatertVedSivilstand?.toString(),
                )
            },
            fødsel = when (val fødsel = person.fødsel) {
                is Person.Fødsel.MedFødselsdato -> Fødsel(
                    dato = fødsel.dato,
                    år = fødsel.år.value,
                    alder = fødsel.getAlder(LocalDate.now(clock)),
                )

                is Person.Fødsel.MedFødselsår -> Fødsel(
                    dato = null,
                    år = fødsel.år.value,
                    alder = null,
                )

                null -> null
            },
            adressebeskyttelse = this.person.adressebeskyttelse,
            skjermet = this.skjermet,
            kontaktinfo = this.kontaktinfo?.let {
                KontaktinfoJson(
                    epostadresse = it.epostadresse,
                    mobiltelefonnummer = it.mobiltelefonnummer,
                    språk = it.språk,
                    kanKontaktesDigitalt = it.kanKontaktesDigitalt,
                )
            },
            vergemål = this.person.vergemål,
            dødsdato = this.person.dødsdato,
        )
    }
}
