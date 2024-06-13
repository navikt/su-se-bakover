package no.nav.su.se.bakover.dokument.infrastructure.database

import dokument.domain.distribuering.Distribueringsadresse
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.serialize

internal fun Distribueringsadresse.toDbJson(): String = serialize(
    DistribueringsAdresseJson(
        adresselinje1 = adresselinje1,
        adresselinje2 = adresselinje2,
        adresselinje3 = adresselinje3,
        postnummer = postnummer,
        poststed = poststed,
    ),
)

internal fun deserializeDistribueringsadresse(json: String): Distribueringsadresse =
    deserialize<DistribueringsAdresseJson>(json).let {
        Distribueringsadresse(
            adresselinje1 = it.adresselinje1,
            adresselinje2 = it.adresselinje2,
            adresselinje3 = it.adresselinje3,
            postnummer = it.postnummer,
            poststed = it.poststed,
        )
    }

private data class DistribueringsAdresseJson(
    val adresselinje1: String?,
    val adresselinje2: String?,
    val adresselinje3: String?,
    val postnummer: String,
    val poststed: String,
)
