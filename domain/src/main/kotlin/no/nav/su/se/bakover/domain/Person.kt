package no.nav.su.se.bakover.domain

import no.nav.su.se.bakover.domain.person.SivilstandTyper
import java.time.LocalDate
import java.time.Period

data class Person(
    val ident: Ident,
    val navn: Navn,
    val telefonnummer: Telefonnummer? = null,
    val adresse: List<Adresse>? = null,
    val statsborgerskap: String? = null,
    val sivilstand: Sivilstand? = null,
    val kjønn: String? = null,
    val fødselsdato: LocalDate? = null,
    val adressebeskyttelse: String? = null,
    val skjermet: Boolean? = null,
    val kontaktinfo: Kontaktinfo? = null,
    val vergemål: Boolean? = null,
    val fullmakt: Boolean? = null,
    val dødsdato: LocalDate? = null,
) {
    fun getAlder(påDato: LocalDate): Int? = fødselsdato?.let { Period.between(it, påDato).years }
    fun er67EllerEldre(påDato: LocalDate): Boolean? = getAlder(påDato)?.let { it >= 67 }

    data class Navn(
        val fornavn: String,
        val mellomnavn: String?,
        val etternavn: String,
    )

    data class Adresse(
        val adresselinje: String?,
        val poststed: Poststed?,
        val bruksenhet: String?,
        val kommune: Kommune?,
        val landkode: String? = null,
        val adressetype: String,
        val adresseformat: String,
    )

    data class Kommune(
        val kommunenummer: String,
        val kommunenavn: String?,
    )

    data class Poststed(
        val postnummer: String,
        val poststed: String?,
    )

    data class Kontaktinfo(
        val epostadresse: String?,
        val mobiltelefonnummer: String?,
        val reservert: Boolean,
        val kanVarsles: Boolean,
        val språk: String?,
    )

    data class Sivilstand(
        val type: SivilstandTyper,
        val relatertVedSivilstand: Fnr?,
    )
}
