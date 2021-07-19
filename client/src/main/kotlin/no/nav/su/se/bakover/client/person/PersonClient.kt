package no.nav.su.se.bakover.client.person

import arrow.core.Either
import no.nav.su.se.bakover.client.dkif.DigitalKontaktinformasjon
import no.nav.su.se.bakover.client.dkif.Kontaktinformasjon
import no.nav.su.se.bakover.client.kodeverk.Kodeverk
import no.nav.su.se.bakover.client.skjerming.Skjerming
import no.nav.su.se.bakover.domain.AktørId
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.Ident
import no.nav.su.se.bakover.domain.Person
import no.nav.su.se.bakover.domain.person.KunneIkkeHentePerson
import no.nav.su.se.bakover.domain.person.PersonOppslag

internal data class PersonClientConfig(
    val kodeverk: Kodeverk,
    val skjerming: Skjerming,
    val digitalKontaktinformasjon: DigitalKontaktinformasjon,
    val pdlClientConfig: PdlClientConfig,
)

internal class PersonClient(
    val config: PersonClientConfig,
) : PersonOppslag {
    private val pdlClient = PdlClient(config.pdlClientConfig)

    override fun person(fnr: Fnr): Either<KunneIkkeHentePerson, Person> = pdlClient.person(fnr).map { toPerson(it) }
    override fun personMedSystembruker(fnr: Fnr): Either<KunneIkkeHentePerson, Person> =
        pdlClient.personForSystembruker(fnr).map { toPerson(it) }

    override fun aktørId(fnr: Fnr): Either<KunneIkkeHentePerson, AktørId> = pdlClient.aktørId(fnr)
    override fun sjekkTilgangTilPerson(fnr: Fnr): Either<KunneIkkeHentePerson, Unit> = pdlClient.person(fnr).map {}

    private fun toPerson(pdlData: PdlData) =
        Person(
            ident = Ident(pdlData.ident.fnr, pdlData.ident.aktørId),
            navn = pdlData.navn.let {
                Person.Navn(
                    fornavn = it.fornavn,
                    mellomnavn = it.mellomnavn,
                    etternavn = it.etternavn,
                )
            },
            telefonnummer = pdlData.telefonnummer,
            adresse = pdlData.adresse?.map {
                Person.Adresse(
                    adresselinje = it.adresselinje,
                    poststed = it.postnummer?.let { postnummer ->
                        toPoststed(postnummer)
                    },
                    bruksenhet = it.bruksenhet,
                    kommune = it.kommunenummer?.let { kommunenummer ->
                        toKommune(kommunenummer)
                    },
                    landkode = it.landkode,
                    adressetype = it.adressetype,
                    adresseformat = it.adresseformat,
                )
            },
            statsborgerskap = pdlData.statsborgerskap,
            sivilstand = pdlData.sivilstand?.let {
                Person.Sivilstand(
                    type = it.type,
                    relatertVedSivilstand = it.relatertVedSivilstand?.let { fnrString -> Fnr(fnrString) },
                )
            },
            kjønn = pdlData.kjønn,
            fødselsdato = pdlData.fødselsdato,
            adressebeskyttelse = pdlData.adressebeskyttelse,
            skjermet = config.skjerming.erSkjermet(pdlData.ident.fnr),
            kontaktinfo = kontaktinfo(pdlData.ident.fnr),
            vergemål = pdlData.vergemålEllerFremtidsfullmakt,
            fullmakt = pdlData.fullmakt,
        )

    private fun toPoststed(postnummer: String) = Person.Poststed(
        postnummer = postnummer,
        poststed = config.kodeverk.hentPoststed(postnummer).orNull(),
    )

    private fun toKommune(kommunenummer: String) = Person.Kommune(
        kommunenummer = kommunenummer,
        kommunenavn = config.kodeverk.hentKommunenavn(kommunenummer).orNull(),
    )

    private fun kontaktinfo(fnr: Fnr): Person.Kontaktinfo? {
        val dkifInfo: Kontaktinformasjon? = config.digitalKontaktinformasjon.hentKontaktinformasjon(fnr).orNull()
        return dkifInfo?.let {
            Person.Kontaktinfo(
                it.epostadresse,
                it.mobiltelefonnummer,
                it.reservert,
                it.kanVarsles,
                it.språk,
            )
        }
    }
}
