package no.nav.su.se.bakover.client.person

import no.nav.su.se.bakover.common.person.AktørId
import no.nav.su.se.bakover.common.person.Fnr
import person.domain.Telefonnummer
import java.time.LocalDate

internal data class PdlData(
    val ident: Ident,
    val navn: Navn,
    val telefonnummer: Telefonnummer?,
    val adresse: List<Adresse>?,
    val sivilstand: SivilstandResponse?,
    val fødsel: Fødsel?,
    val adressebeskyttelse: String?,
    val vergemålEllerFremtidsfullmakt: Boolean,
    val dødsdato: LocalDate?,
    val dødsbo: List<Dødsbo>,
) {
    internal data class Ident(
        val fnr: Fnr,
        val aktørId: AktørId,
    )

    internal data class Navn(
        val fornavn: String,
        val mellomnavn: String?,
        val etternavn: String,
    )

    internal data class Fødsel(
        val foedselsaar: Int,
        val foedselsdato: LocalDate? = null,
    )

    internal data class Adresse(
        val adresselinje: String,
        val postnummer: String?,
        val bruksenhet: String? = null,
        val kommunenummer: String? = null,
        val landkode: String? = null,
        val adressetype: String,
        val adresseformat: String,
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Adresse

            if (adresselinje != other.adresselinje) return false
            if (postnummer != other.postnummer) return false
            if (bruksenhet != other.bruksenhet) return false
            if (landkode != other.landkode) return false

            return true
        }

        override fun hashCode(): Int {
            var result = adresselinje.hashCode()
            result = 31 * result + (postnummer?.hashCode() ?: 0)
            result = 31 * result + (bruksenhet?.hashCode() ?: 0)
            result = 31 * result + (landkode?.hashCode() ?: 0)
            return result
        }
    }
}

internal data class Dødsbo(
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
