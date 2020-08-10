package no.nav.su.se.bakover.domain

data class Person(
    val fnr: Fnr,
    val aktørId: AktørId,
    val navn: Navn,
    val telefonnummer: Telefonnummer?,
    val adresse: Adresse?,
    val statsborgerskap: String?
) {
    data class Navn(
        val fornavn: String,
        val mellomnavn: String?,
        val etternavn: String
    )

    data class Adresse(
        val adressenavn: String?,
        val postnummer: String?,
        val poststed: String?,
        val bruksenhet: String?,
        val kommunenavn: String?,
        val kommunenummer: String?
    )
}
