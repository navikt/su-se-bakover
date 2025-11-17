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
    val statsborgerskap: String? = null,
    val sivilstand: Sivilstand? = null,
    val fødsel: Fødsel? = null,
    val adressebeskyttelse: String? = null,
    val skjermet: Boolean? = null,
    val kontaktinfo: () -> Kontaktinfo?,
    val vergemål: Boolean? = null,
    val dødsdato: LocalDate? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Person) return false

        return ident == other.ident &&
            navn == other.navn &&
            telefonnummer == other.telefonnummer &&
            adresse == other.adresse &&
            statsborgerskap == other.statsborgerskap &&
            sivilstand == other.sivilstand &&
            fødsel == other.fødsel &&
            adressebeskyttelse == other.adressebeskyttelse &&
            skjermet == other.skjermet &&
            vergemål == other.vergemål &&
            dødsdato == other.dødsdato
    }

    override fun hashCode(): Int {
        return listOf(
            ident,
            navn,
            telefonnummer,
            adresse,
            statsborgerskap,
            sivilstand,
            fødsel,
            adressebeskyttelse,
            skjermet,
            vergemål,
            dødsdato,
        ).fold(0) { acc, e -> 31 * acc + (e?.hashCode() ?: 0) }
    }

    fun getAlder(påDato: LocalDate): Int? = fødsel?.getAlder(påDato)
    fun alderSomFylles(påÅr: Year): Int? = fødsel?.alderSomFylles(påÅr)

    fun er67EllerEldre(påDato: LocalDate): Boolean? = getAlder(påDato)?.let { it >= 67 }
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
        val språk: String?,
        val kanKontaktesDigitalt: Boolean,
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
