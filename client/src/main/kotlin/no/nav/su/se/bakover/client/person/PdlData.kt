package no.nav.su.se.bakover.client.person

import no.nav.su.se.bakover.common.person.AktørId
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.domain.person.Telefonnummer
import java.time.LocalDate

internal data class PdlData(
    val ident: Ident,
    val navn: Navn,
    val telefonnummer: Telefonnummer?,
    val adresse: List<Adresse>?,
    val statsborgerskap: String?,
    val sivilstand: SivilstandResponse?,
    val kjønn: String?,
    val fødsel: Fødsel?,
    val adressebeskyttelse: String?,
    val vergemålEllerFremtidsfullmakt: Boolean,
    val fullmakt: Boolean,
    val dødsdato: LocalDate?,
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
