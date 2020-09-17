package no.nav.su.se.bakover.client.person

import arrow.core.Either
import arrow.core.orNull
import no.nav.su.se.bakover.client.kodeverk.Kodeverk
import no.nav.su.se.bakover.client.sts.TokenOppslag
import no.nav.su.se.bakover.domain.AktørId
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.Ident
import no.nav.su.se.bakover.domain.Person

class PersonClient(
    private val kodeverk: Kodeverk,
    pdlUrl: String,
    tokenOppslag: TokenOppslag,
) : PersonOppslag {
    private val pdlClient = PdlClient(pdlUrl, tokenOppslag)

    override fun person(fnr: Fnr): Either<PdlFeil, Person> = pdlClient.person(fnr).map { toPerson(it) }
    override fun aktørId(fnr: Fnr): Either<PdlFeil, AktørId> = pdlClient.aktørId(fnr)

    private fun toPerson(pdlData: PdlData) =
        Person(
            ident = Ident(pdlData.ident.fnr, pdlData.ident.aktørId),
            navn = pdlData.navn.let {
                Person.Navn(
                    fornavn = it.fornavn,
                    mellomnavn = it.mellomnavn,
                    etternavn = it.etternavn
                )
            },
            telefonnummer = pdlData.telefonnummer,
            adresse = pdlData.adresse?.let {
                Person.Adresse(
                    adressenavn = it.adressenavn,
                    husnummer = it.husnummer,
                    husbokstav = it.husbokstav,
                    poststed = it.postnummer?.let { postnummer ->
                        toPoststed(postnummer)
                    },
                    bruksenhet = it.bruksenhet,
                    kommune = it.kommunenummer?.let { kommunenummer ->
                        toKommune(kommunenummer)
                    }
                )
            },
            statsborgerskap = pdlData.statsborgerskap,
            kjønn = pdlData.kjønn,
            adressebeskyttelse = pdlData.adressebeskyttelse
        )

    private fun toPoststed(postnummer: String) = Person.Poststed(
        postnummer = postnummer,
        poststed = kodeverk.hentPoststed(postnummer).orNull()
    )

    private fun toKommune(kommunenummer: String) = Person.Kommune(
        kommunenummer = kommunenummer,
        kommunenavn = kodeverk.hentKommunenavn(kommunenummer).orNull()
    )
}
