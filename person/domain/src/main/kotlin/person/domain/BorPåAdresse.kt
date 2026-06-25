package person.domain

data class BorPåAdresseRequest(
    val adressenavn: String,
    val husnummer: String,
    val postnummer: String,
)

data class BorPåAdressePdlRequest(
    val query: String,
    val variables: BorPåAdresseRequest,
)

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
