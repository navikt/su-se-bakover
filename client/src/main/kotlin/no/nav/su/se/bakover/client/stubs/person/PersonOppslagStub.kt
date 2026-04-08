package no.nav.su.se.bakover.client.stubs.person

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.domain.tid.januar
import no.nav.su.se.bakover.common.infrastructure.config.ApplicationConfig
import no.nav.su.se.bakover.common.person.AktørId
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.person.Ident
import person.domain.AdresseopplysningerMedMetadata
import person.domain.Kontaktinfo
import person.domain.KunneIkkeHentePerson
import person.domain.Person
import person.domain.PersonMedSkjermingOgKontaktinfo
import person.domain.PersonOppslag
import person.domain.Telefonnummer
import java.time.LocalDate

data class PersonOppslagStub(
    val dødsdato: LocalDate? = foedselsdatoForUføre,
    val fødselsdato: LocalDate = 1.januar(1990),
    val fødselsdatoOver67: LocalDate = 1.januar(1958),
) : PersonOppslag {

    companion object {
        val foedselsdatoForAlder = 1.januar(1954)
        val foedselsdatoForUføre = 1.januar(1990)
    }

    fun nyTestPerson(
        fnr: Fnr,
        sakstype: Sakstype,
    ) = Person(
        ident = Ident(fnr, AktørId("2437280977705")),
        navn = Person.Navn(
            fornavn = "Tore",
            mellomnavn = "Johnas",
            etternavn = "Strømøy",
        ),
        telefonnummer = Telefonnummer(landskode = "+47", nummer = "12345678"),
        adresse = listOf(
            Person.Adresse(
                adresselinje = "Oslogata 12",
                bruksenhet = "U1H20",
                poststed = Person.Poststed(postnummer = "0050", poststed = "OSLO"),
                kommune = Person.Kommune(kommunenummer = "0301", kommunenavn = "OSLO"),
                adressetype = "Bostedsadresse",
                adresseformat = "Vegadresse",
            ),
        ),
        sivilstand = null,
        fødsel = Person.Fødsel.MedFødselsdato(
            dato = when (sakstype) {
                Sakstype.ALDER -> fødselsdatoOver67
                Sakstype.UFØRE -> fødselsdato
            },
        ),
        adressebeskyttelse = if (fnr.toString() == ApplicationConfig.fnrKode6()) "STRENGT_FORTROLIG_ADRESSE" else null,
        vergemål = null,
        dødsdato = null,
    )

    override fun person(fnr: Fnr, sakstype: Sakstype): Either<KunneIkkeHentePerson, Person> =
        if (fnr.toString() == ApplicationConfig.fnrKode6()) {
            KunneIkkeHentePerson.IkkeTilgangTilPerson.left()
        } else {
            nyTestPerson(fnr, sakstype).right()
        }

    override fun personMedSystembruker(fnr: Fnr, sakstype: Sakstype): Either<KunneIkkeHentePerson, Person> = nyTestPerson(fnr, sakstype).right()

    override fun bostedsadresseMedMetadataForSystembruker(fnr: Fnr): Either<KunneIkkeHentePerson, AdresseopplysningerMedMetadata> =
        KunneIkkeHentePerson.Ukjent.left()

    override fun personMedSkjermingOgKontaktinfo(
        fnr: Fnr,
        sakstype: Sakstype,
    ): Either<KunneIkkeHentePerson, PersonMedSkjermingOgKontaktinfo> =
        nyTestPerson(fnr, sakstype).let {
            PersonMedSkjermingOgKontaktinfo(
                person = it,
                skjermet = false,
                kontaktinfo = Kontaktinfo(
                    epostadresse = "mail@epost.com",
                    mobiltelefonnummer = "90909090",
                    språk = "nb",
                    kanKontaktesDigitalt = true,
                ),
            ).right()
        }
    override fun aktørIdMedSystembruker(fnr: Fnr, sakstype: Sakstype): Either<KunneIkkeHentePerson, AktørId> =
        AktørId("2437280977705").right()

    override fun sjekkTilgangTilPerson(fnr: Fnr, sakstype: Sakstype): Either<KunneIkkeHentePerson, Unit> =
        if (fnr.toString() == ApplicationConfig.fnrKode6()) {
            KunneIkkeHentePerson.IkkeTilgangTilPerson.left()
        } else {
            Unit.right()
        }
}
