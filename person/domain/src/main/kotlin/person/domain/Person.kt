package person.domain

import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.person.Ident
import java.time.LocalDate
import java.time.Period
import java.time.Year

data class Person(
    val ident: Ident,
    val navn: Navn,
    val telefonnummer: Telefonnummer? = null,
    val adresse: List<Adresse>? = null,
    val sivilstand: Sivilstand? = null,
    val fødsel: Fødsel? = null,
    val adressebeskyttelse: String? = null,
    val vergemål: Boolean? = null,
    val dødsdato: LocalDate? = null,
) {
    fun getAlder(påDato: LocalDate): Int? = fødsel?.getAlder(påDato)
    fun alderSomFylles(påÅr: Year): Int? = fødsel?.alderSomFylles(påÅr)

    fun er67EllerEldre(påDato: LocalDate): Boolean? = getAlder(påDato)?.let { it >= 67 }

    /*
     * TODO: SOS – døde personer uten dødsdato regnes som levende.
     * PDL kan returnere et dødsfall uten dødsdato. Dødsdato er ikke obligatorisk, og ca. 1700 personer
     * fra Folkeregisteret mangler den. PdlClient mapper da dødsdato til null (og logger error), slik at
     * erDød() returnerer false. Alle dødssjekker (kontrollsamtale, påminnelse ny stønadsperiode,
     * tilbakekreving) behandler dermed personen som levende: brev sendes, ingen oppgave opprettes.
     * Løsning: modeller dødsfall eksplisitt (f.eks. et eget flagg for registrert dødsfall med valgfri dato)
     * og la erDød() bruke dette, mens dødsdato-avhengig logikk håndterer manglende dato separat.
     */
    fun erDød(): Boolean {
        return dødsdato != null
    }

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

        val adressenavn: String?,
        val husnummer: String?,
        val husbokstav: String?,
    )

    data class Kommune(
        val kommunenummer: String,
        val kommunenavn: String?,
    )

    data class Poststed(
        val postnummer: String,
        val poststed: String?,
    )

    data class Sivilstand(
        val type: SivilstandTyper,
        val relatertVedSivilstand: Fnr?,
    )

    sealed interface Fødsel {
        val år: Year

        /**
         * Dersom fødselsdato eksisterer, vil alderen på person regnes ut basert på [påDato].
         * Hvis ikke, returneres null
         */
        fun getAlder(påDato: LocalDate): Int?

        fun alderSomFylles(påÅr: Year): Int = påÅr.minusYears(år.value.toLong()).value

        data class MedFødselsdato(val dato: LocalDate) : Fødsel {
            override val år: Year = Year.of(dato.year)
            override fun getAlder(påDato: LocalDate) = dato.let { Period.between(it, påDato).years }
        }

        data class MedFødselsår(override val år: Year) : Fødsel {
            override fun getAlder(påDato: LocalDate): Int? = null
        }
    }
}
