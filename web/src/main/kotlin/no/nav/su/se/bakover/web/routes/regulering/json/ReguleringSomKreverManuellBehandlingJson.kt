package no.nav.su.se.bakover.web.routes.regulering.json

import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.regulering.ReguleringSomKreverManuellBehandling

internal data class ReguleringSomKreverManuellBehandlingJson(
    val saksnummer: Long,
    val fnr: String,
    val reguleringId: String,
    val fradragsKategori: List<String>,
)

internal fun List<ReguleringSomKreverManuellBehandling>.toJson(): String {
    return serialize(this.map { it.toJson() })
}

private fun ReguleringSomKreverManuellBehandling.toJson(): ReguleringSomKreverManuellBehandlingJson {
    return ReguleringSomKreverManuellBehandlingJson(
        saksnummer = this.saksnummer.nummer,
        fnr = this.fnr.toString(),
        reguleringId = this.reguleringId.toString(),
        fradragsKategori = fradragsKategori.map { it.toString() },
    )
}
