package person.domain

data class PersonMedSkjermingOgKontaktinfo(
    val person: Person,
    val skjermet: Boolean,
    val kontaktinfo: Kontaktinfo?,
    val dødsbo: List<KontaktInfoDødsbo>,
)

data class KontaktInfoDødsbo(
    val kontaktPerson: Kontaktinformasjon?,
    val kontaktAdvokat: Kontaktinformasjon?,
    val kontaktOrganisasjon: Kontaktinformasjon?,
    val adresselinje1: String?,
    val adresselinje2: String?,
    val poststedsnavn: String?,
    val postnummer: String?,
    val landkode: String?,
) {
    data class Kontaktinformasjon(
        val fornavn: String?,
        val mellomnavn: String?,
        val etternavn: String?,
        val identifikasjonsnummer: String?,
        val organisasjonsnavn: String?,
        val organisasjonsnummer: String?,
    )
}
