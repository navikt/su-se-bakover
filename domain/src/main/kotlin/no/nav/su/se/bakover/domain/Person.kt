package no.nav.su.se.bakover.domain

import java.time.LocalDate
import java.time.Period

data class Person(
    val ident: Ident,
    val navn: Navn,
    val telefonnummer: Telefonnummer?,
    val adresse: List<Adresse>?,
    val statsborgerskap: String?,
    val kjønn: String?,
    val fødselsdato: LocalDate?,
    val adressebeskyttelse: String?,
    val skjermet: Boolean?,
    val kontaktinfo: Kontaktinfo?,
    val vergemål: Boolean?,
    val fullmakt: Boolean?
) {
    fun getAlder() = fødselsdato?.let { Period.between(it, LocalDate.now()).years }

    data class Navn(
        val fornavn: String,
        val mellomnavn: String?,
        val etternavn: String
    )

    data class Adresse(
        val adresselinje: String?,
        val poststed: Poststed?,
        val bruksenhet: String?,
        val kommune: Kommune?,
        val landkode: String? = null,
        val adressetype: String,
        val adresseformat: String
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
}
