package no.nav.su.se.bakover.web.routes.person

import no.nav.su.se.bakover.domain.AktørId
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.Person
import no.nav.su.se.bakover.domain.Telefonnummer

data class PersonJson(
    val fnr: String,
    val aktorId: String,
    val navn: NavnJson,
    val telefonnummer: TelefonnummerJson?,
    val adresse: AdresseJson?,
    val statsborgerskap: String?,
    val kjønn: String?
) {
    data class NavnJson(
        val fornavn: String,
        val mellomnavn: String?,
        val etternavn: String
    ) {
        fun toNavn(): Person.Navn = Person.Navn(fornavn = fornavn, mellomnavn = mellomnavn, etternavn = etternavn)
    }

    data class TelefonnummerJson(
        val landskode: String,
        val nummer: String
    ) {
        fun toTelefonnummer(): Telefonnummer = Telefonnummer(landskode = landskode, nummer = nummer)
    }

    data class AdresseJson(
        val adressenavn: String?,
        val husnummer: String?,
        val husbokstav: String?,
        val postnummer: String?,
        val poststed: String?,
        val bruksenhet: String?,
        val kommunenummer: String?,
        val kommunenavn: String?
    ) {
        fun toAdresse(): Person.Adresse = Person.Adresse(
            adressenavn = adressenavn,
            husnummer = husnummer,
            husbokstav = husbokstav,
            postnummer = postnummer,
            poststed = poststed,
            bruksenhet = bruksenhet,
            kommunenummer = kommunenummer,
            kommunenavn = kommunenavn
        )
    }

    fun toPerson(): Person = Person(
        fnr = Fnr(fnr),
        aktørId = AktørId(aktorId),
        navn = navn.toNavn(),
        telefonnummer = telefonnummer?.toTelefonnummer(),
        adresse = adresse?.toAdresse(),
        statsborgerskap = statsborgerskap,
        kjønn = kjønn
    )

    companion object {
        fun Person.toPersonJson() = PersonJson(
            fnr = this.fnr.toString(),
            aktorId = this.aktørId.aktørId,
            navn = NavnJson(
                fornavn = this.navn.fornavn,
                mellomnavn = this.navn.mellomnavn,
                etternavn = this.navn.etternavn
            ),
            telefonnummer = this.telefonnummer?.let { t ->
                TelefonnummerJson(
                    landskode = t.landskode,
                    nummer = t.nummer
                )
            },
            adresse = this.adresse?.let {
                AdresseJson(
                    adressenavn = it.adressenavn,
                    husnummer = it.husnummer,
                    husbokstav = it.husbokstav,
                    postnummer = it.postnummer,
                    poststed = it.poststed,
                    bruksenhet = it.bruksenhet,
                    kommunenummer = it.kommunenummer,
                    kommunenavn = it.kommunenavn
                )
            },
            statsborgerskap = this.statsborgerskap,
            kjønn = this.kjønn
        )
    }
}
