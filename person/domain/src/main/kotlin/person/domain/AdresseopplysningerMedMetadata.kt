package person.domain

data class AdresseopplysningerMedMetadata(
    val bostedsadresser: List<Adresseopplysning>,
    val kontaktadresser: List<Adresseopplysning>,
) {
    data class Adresseopplysning(
        val historisk: Boolean,
        val hendelseIder: List<String>,
        val gateadresse: String?,
        val postnummer: String?,
        val poststed: String?,
        val matrikkelId: Long?,
    )
}
