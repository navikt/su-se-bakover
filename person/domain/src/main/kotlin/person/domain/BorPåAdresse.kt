package person.domain

import java.time.LocalDate

data class BorPåAdresseRequest(
    val adressenavn: String,
    val husnummer: String,
    val postnummer: String,
    val bruksenhetsnummer: String,
)

data class BorPåAdressePdlRequest(
    val query: String,
    val variables: Variables,
) {
    data class Variables(
        val adressenavn: String,
        val husnummer: String,
        val postnummer: String,
        val pageNumer: Int,
    )
}

data class BorPåAdresse(
    val søktAdresse: String,
    val treff: List<PersonPåAdresse>,
)

data class PersonPåAdresse(
    val etternavn: String,
    val fornavn: String,
    val mellomnavn: String,

    val adressenavn: String,
    val husnummer: String,
    val husbokstav: String,
    val postnummer: String,
    val bruksenhetsnummer: String,
    val gyldigFraOgMed: LocalDate?,
    val gyldigTilOgMed: LocalDate?,
    val folkeregisteridentifikator: List<Identifikator>,
)

data class Identifikator(
    val ident: String,
    val type: String,
)
