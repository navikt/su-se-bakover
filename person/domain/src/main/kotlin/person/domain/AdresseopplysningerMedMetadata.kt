package person.domain

data class AdresseopplysningerMedMetadata(
    val bostedsadresser: List<Adresseopplysning>,
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
        // gateadresse = adressenavn + husnummer + husbokstav
        val gateadresse: String?,
        val postnummer: String?,
        val folkeregistermetadata: Folkeregistermetadata?,
    )
}
