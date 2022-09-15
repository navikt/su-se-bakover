import no.nav.su.se.bakover.client.person.PdlData

internal fun List<Adressetype>.finnRiktigAdresseformatOgMapTilPdlAdresse(): List<PdlData.Adresse> {
    fun tilPdlAdresse(format: Adresseformat, adressetype: String): PdlData.Adresse? {
        return when (format) {
            is Vegadresse -> PdlData.Adresse(
                adresselinje = "${format.adressenavn} ${format.husnummer ?: ""}${format.husbokstav ?: ""}",
                postnummer = format.postnummer,
                bruksenhet = format.bruksenhetsnummer,
                kommunenummer = format.kommunenummer,
                adresseformat = format.type,
                adressetype = adressetype,
            )
            is PostadresseIFrittFormat -> PdlData.Adresse(
                adresselinje = listOfNotNull(
                    format.adresselinje1,
                    format.adresselinje2,
                    format.adresselinje3,
                ).joinToString(),
                postnummer = format.postnummer,
                adresseformat = format.type,
                adressetype = adressetype,
            )
            is Postboksadresse -> PdlData.Adresse(
                adresselinje = "${format.postbokseier}, ${format.postboks}",
                postnummer = format.postnummer,
                adresseformat = format.type,
                adressetype = adressetype,
            )
            is UkjentBosted -> null
            is Matrikkeladresse -> PdlData.Adresse(
                adresselinje = format.tilleggsnavn ?: "",
                postnummer = format.postnummer,
                bruksenhet = format.bruksenhetsnummer,
                kommunenummer = format.kommunenummer,
                adresseformat = format.type,
                adressetype = adressetype,
            )
            is UtenlandskAdresse -> PdlData.Adresse(
                adresselinje = listOfNotNull(
                    format.adressenavnNummer,
                    format.bygningEtasjeLeilighet,
                    format.bySted,
                    format.regionDistriktOmraade,
                ).joinToString(),
                postnummer = format.postkode,
                landkode = format.landkode,
                adresseformat = format.type,
                adressetype = adressetype,
            )
            is UtenlandskAdresseIFrittFormat -> PdlData.Adresse(
                adresselinje = listOfNotNull(
                    format.adresselinje1,
                    format.adresselinje2,
                    format.adresselinje3,
                    format.byEllerStedsnavn,
                ).joinToString(),
                postnummer = format.postkode,
                landkode = format.landkode,
                adresseformat = format.type,
                adressetype = adressetype,
            )
        }
    }

    return this.mapNotNull { adressetype ->
        // TODO ai: Se om man kan førenkle
        when (adressetype) {
            is Bostedsadresse ->
                listOfNotNull(
                    adressetype.vegadresse,
                    adressetype.matrikkeladresse,
                    adressetype.ukjentBosted,
                ).firstOrNull()
                    ?.let { tilPdlAdresse(it, adressetype.type) }

            is Oppholdsadresse ->
                listOfNotNull(
                    adressetype.vegadresse,
                    adressetype.matrikkeladresse,
                    adressetype.utenlandskAdresse,
                ).firstOrNull()
                    ?.let { tilPdlAdresse(it, adressetype.type) }

            is Kontaktadresse -> listOfNotNull(
                adressetype.vegadresse,
                adressetype.postadresseIFrittFormat,
                adressetype.postboksadresse,
                adressetype.utenlandskAdresse,
                adressetype.utenlandskAdresseIFrittFormat,
            ).firstOrNull()?.let { tilPdlAdresse(it, adressetype.type) }
        }
    }.distinct()
}

sealed class Adressetype(val type: String)
data class Bostedsadresse(
    val vegadresse: Vegadresse?,
    val ukjentBosted: UkjentBosted?,
    val matrikkeladresse: Matrikkeladresse?,
) : Adressetype(type = "Bostedsadresse")

data class Kontaktadresse(
    val vegadresse: Vegadresse?,
    val postadresseIFrittFormat: PostadresseIFrittFormat?,
    val postboksadresse: Postboksadresse?,
    val utenlandskAdresse: UtenlandskAdresse?,
    val utenlandskAdresseIFrittFormat: UtenlandskAdresseIFrittFormat?,
) : Adressetype(type = "Kontaktadresse")

data class Oppholdsadresse(
    val vegadresse: Vegadresse?,
    val matrikkeladresse: Matrikkeladresse?,
    val utenlandskAdresse: UtenlandskAdresse?,
) : Adressetype(type = "Oppholdsadresse")

sealed class Adresseformat(val type: String)
data class Vegadresse(
    val husnummer: String?,
    val husbokstav: String?,
    val adressenavn: String?,
    val kommunenummer: String?,
    val postnummer: String?,
    val bruksenhetsnummer: String?,
) : Adresseformat(type = "Vegadresse")

data class PostadresseIFrittFormat(
    val adresselinje1: String?,
    val adresselinje2: String?,
    val adresselinje3: String?,
    val postnummer: String?,
) : Adresseformat(type = "PostadresseIFrittFormat")

data class Postboksadresse(
    val postbokseier: String?,
    val postboks: String?,
    val postnummer: String?,
) : Adresseformat(type = "Postboksadresse")

data class UkjentBosted(
    val bostedskommune: String,
) : Adresseformat(type = "UkjentBosted")

data class Matrikkeladresse(
    val matrikkelId: Long?,
    val bruksenhetsnummer: String?,
    val tilleggsnavn: String?,
    val postnummer: String?,
    val kommunenummer: String?,
) : Adresseformat(type = "Matrikkeladresse")

data class UtenlandskAdresse(
    val adressenavnNummer: String?,
    val bygningEtasjeLeilighet: String?,
    val postboksNummerNavn: String?,
    val postkode: String?,
    val bySted: String?,
    val regionDistriktOmraade: String?,
    val landkode: String,
) : Adresseformat(type = "UtenlandskAdresse")

data class UtenlandskAdresseIFrittFormat(
    val adresselinje1: String?,
    val adresselinje2: String?,
    val adresselinje3: String?,
    val postkode: String?,
    val byEllerStedsnavn: String?,
    val landkode: String,
) : Adresseformat(type = "UtenlandskAdresseIFrittFormat")
