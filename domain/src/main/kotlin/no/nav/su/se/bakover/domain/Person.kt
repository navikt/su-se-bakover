package no.nav.su.se.bakover.domain

data class Person(
    val ident: Ident,
    val navn: Navn,
    val telefonnummer: Telefonnummer?,
    val adresse: Adresse?,
    val statsborgerskap: String?,
    val kjønn: String?,
    val adressebeskyttelse: String?,
    val skjermet: Boolean?,
    val kontaktinfo: Kontaktinfo?,
    val vergemaalEllerFremtidsfullmakt: VergemaalEllerFremtidsfullmakt?
) {
    data class Navn(
        val fornavn: String,
        val mellomnavn: String?,
        val etternavn: String
    )

    data class Adresse(
        val adressenavn: String?,
        val husnummer: String?,
        val husbokstav: String?,
        val poststed: Poststed?,
        val bruksenhet: String?,
        val kommune: Kommune?
    )

    data class Kommune(
        val kommunenummer: String,
        val kommunenavn: String?
    )

    data class Poststed(
        val postnummer: String,
        val poststed: String?
    )

    data class Kontaktinfo(
        val epostadresse: String?,
        val mobiltelefonnummer: String?,
        val reservert: Boolean,
        val kanVarsles: Boolean,
        val språk: String?,
    )

    data class VergemaalEllerFremtidsfullmakt(
        val type: String?,
        val vergeEllerFullmektig: VergeEllerFullmektig
    ) {

        data class VergeEllerFullmektig(
            val motpartsPersonident: String
        )
    }
}
