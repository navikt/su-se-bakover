package no.nav.su.se.bakover.client.person

import no.nav.su.se.bakover.domain.AktørId
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.Telefonnummer

data class PdlData(
    val ident: Ident,
    val navn: Navn,
    val telefonnummer: Telefonnummer?,
    val adresse: Adresse?,
    val statsborgerskap: String?,
    val kjønn: String?
) {
    data class Ident(
        val fnr: Fnr,
        val aktørId: AktørId
    )

    data class Navn(
        val fornavn: String,
        val mellomnavn: String?,
        val etternavn: String
    )

    data class Adresse(
        val adressenavn: String?,
        val husnummer: String?,
        val husbokstav: String?,
        val postnummer: String?,
        val bruksenhet: String?,
        val kommunenummer: String?
    )
}

/*
{
    fun toDto() = PersonDto.AdresseDto(
        adressenavn = adressenavn,
        husnummer = husnummer,
        husbokstav = husbokstav,
        poststed = postnummer?.let {
            PersonDto.PoststedDto(
                postnummer = it,
                poststed = kodeverk.hentPoststed(it)
            )
        }
            bruksenhet = bruksenhet,
        kommunenummer = adresse.kommunenummer,
        kommunenavn = null // TODO: Oppslag kommunenummer -> kommunenavn
    )
}*/
