package no.nav.su.se.bakover.client.person

import no.nav.su.se.bakover.domain.AktørId
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.Telefonnummer

internal data class PdlData(
    val ident: Ident,
    val navn: Navn,
    val telefonnummer: Telefonnummer?,
    val adresse: List<Adresse>?,
    val statsborgerskap: String?,
    val kjønn: String?,
    val adressebeskyttelse: String?,
    val vergemålEllerFremtidsfullmakt: Boolean,
    val fullmakt: Boolean,
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
        val adresselinje: String?,
        val postnummer: String?,
        val bruksenhet: String? = null,
        val kommunenummer: String? = null,
        val landkode: String? = null,
    )
}
