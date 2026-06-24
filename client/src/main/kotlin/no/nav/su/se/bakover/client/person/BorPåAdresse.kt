package no.nav.su.se.bakover.client.person

data class BorPåAdresse(
    val treff: List<PersonPåAdresse>,
)

data class PersonPåAdresse(
    val etternavn: String,
    val fornavn: String,
    val mellomnavn: String?,

    val husnummer: String?,
    val husbokstav: String?,
    val adressenavn: String?,
    val kommunenummer: String?,
    val postnummer: String?,
    val bruksenhetsnummer: String?,
)
