package no.nav.su.se.bakover.client.person

import no.nav.su.se.bakover.domain.AktørId
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.Person
import no.nav.su.se.bakover.domain.Telefonnummer

internal data class PdlData(
    val ident: Ident,
    val navn: Navn,
    val telefonnummer: Telefonnummer?,
    val adresse: Adresse?,
    val statsborgerskap: String?,
    val kjønn: String?,
    val adressebeskyttelse: String?,
    val vergemaalEllerFremtidsfullmakt: Person.VergemaalEllerFremtidsfullmakt?,
) {
    internal data class Ident(
        val fnr: Fnr,
        val aktørId: AktørId
    )

    internal data class Navn(
        val fornavn: String,
        val mellomnavn: String?,
        val etternavn: String
    )

    internal data class Adresse(
        val adressenavn: String?,
        val husnummer: String?,
        val husbokstav: String?,
        val postnummer: String?,
        val bruksenhet: String?,
        val kommunenummer: String?
    )
}
