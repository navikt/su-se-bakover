package no.nav.su.se.bakover.client.person

import arrow.core.orNull
import no.nav.su.se.bakover.client.kodeverk.Kodeverk
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.Ident
import no.nav.su.se.bakover.domain.Person

class PersonFactory(
    private val personOppslag: PersonOppslag,
    private val kodeverk: Kodeverk
) {
    fun forFnr(fnr: Fnr) = personOppslag.person(fnr).map {
        toPerson(it)
    }

    fun toPerson(pdlData: PdlData) =
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
                    poststed = it.postnummer?.let {
                        toPoststed(it)
                    },
                    bruksenhet = it.bruksenhet,
                    kommune = it.kommunenummer?.let {
                        toKommune(it)
                    }
                )
            },
            statsborgerskap = pdlData.statsborgerskap,
            kjønn = pdlData.kjønn
        )

    fun toPoststed(postnummer: String) = Person.Poststed(
        postnummer = postnummer,
        poststed = kodeverk.hentPoststed(postnummer).orNull()
    )

    fun toKommune(kommunenummer: String) = Person.Kommune(
        kommunenummer = kommunenummer,
        kommunenavn = kodeverk.hentKommunenavn(kommunenummer).orNull()
    )
}
