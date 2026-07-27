package no.nav.su.se.bakover.web.kontrollsamtale

import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.deserializeList
import org.json.JSONObject

internal data class KontrollsamtaleResponseJson(
    val id: String,
    val opprettet: String,
    val innkallingsdato: String,
    val status: String,
    val frist: String,
    val dokumentId: String?,
    val journalpostIdKontrollnotat: String?,
    val hendelser: List<KontrollsamtaleHendelseResponseJson>,
    val kanOppdatereInnkallingsmåned: Boolean,
    val lovligeStatusovergangerForSaksbehandler: List<String>,
)

internal data class KontrollsamtaleHendelseResponseJson(
    val tidspunkt: String,
    val navIdent: String,
    val handling: String,
    val rolle: String,
)

fun hentKontrollsamtaleId(kontrollsamtaleResponseJson: String): String {
    return JSONObject(kontrollsamtaleResponseJson).getString("id").toString()
}

internal fun String.toKontrollsamtaleResponseJson(): KontrollsamtaleResponseJson {
    return deserialize(this)
}

internal fun String.toKontrollsamtalerResponseJson(): List<KontrollsamtaleResponseJson> {
    return deserializeList(this)
}
