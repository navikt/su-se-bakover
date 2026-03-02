package person.domain

data class AdresseopplysningerMedMetadata(
    val bostedsadresser: List<Adresseopplysning>,
    val kontaktadresser: List<Adresseopplysning>,
) {
    data class Folkeregistermetadata(
        val ajourholdstidspunkt: String?,
        val gyldighetstidspunkt: String?,
        val opphoerstidspunkt: String?,
        val kilde: String?,
        val aarsak: String?,
        val sekvens: Int?,
    )

    data class Adresseopplysning(
        val historisk: Boolean,
        val hendelseIder: List<String>,
        val gateadresse: String?,
        val postnummer: String?,
        val poststed: String?,
        val matrikkelId: Long?,
        val folkeregistermetadata: Folkeregistermetadata?,
    )
}
