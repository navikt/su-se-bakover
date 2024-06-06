package no.nav.su.se.bakover.dokument.infrastructure.client.distribuering

import dokument.domain.Distribusjonstidspunkt
import dokument.domain.Distribusjonstype
import dokument.domain.distribuering.Distribueringsadresse
import no.nav.su.se.bakover.common.journal.JournalpostId
import no.nav.su.se.bakover.common.serialize

private data class DokDistRequestJson(
    val journalpostId: String,
    val bestillendeFagsystem: String = "SUPSTONAD",
    val dokumentProdApp: String = "SU_SE_BAKOVER",
    val distribusjonstype: String,
    val distribusjonstidspunkt: String,
    val adresse: DokDistAdresseJson?,
) {
    data class DokDistAdresseJson(
        val adressetype: String = "norskPostadresse",
        val adresselinje1: String?,
        val adresselinje2: String?,
        val adresselinje3: String?,
        val postnummer: String,
        val poststed: String,
        val land: String = "NO",
    )
}

fun toDokDistRequestJson(
    journalpostId: JournalpostId,
    distribusjonstype: Distribusjonstype,
    distribusjonstidspunkt: Distribusjonstidspunkt,
    distribueringsadresse: Distribueringsadresse?,
): String {
    return DokDistRequestJson(
        journalpostId = journalpostId.toString(),
        distribusjonstype = when (distribusjonstype) {
            Distribusjonstype.VEDTAK -> "VEDTAK"
            Distribusjonstype.VIKTIG -> "VIKTIG"
            Distribusjonstype.ANNET -> "ANNET"
        },
        distribusjonstidspunkt = when (distribusjonstidspunkt) {
            Distribusjonstidspunkt.UMIDDELBART -> "UMIDDELBART"
            Distribusjonstidspunkt.KJERNETID -> "KJERNETID"
        },
        adresse = distribueringsadresse?.let {
            DokDistRequestJson.DokDistAdresseJson(
                adresselinje1 = it.adresselinje1,
                adresselinje2 = it.adresselinje2,
                adresselinje3 = it.adresselinje3,
                postnummer = it.postnummer,
                poststed = it.poststed,
            )
        },
    ).let {
        serialize(it)
    }
}
