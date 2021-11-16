package no.nav.su.se.bakover.web.routes.klage

import no.nav.su.se.bakover.domain.klage.Klage
import no.nav.su.se.bakover.domain.klage.OpprettetKlage
import no.nav.su.se.bakover.domain.klage.VilkårsvurdertKlage
import no.nav.su.se.bakover.web.routes.klage.Typer.Companion.frontendStatus

data class KlageJson(
    val id: String,
    val sakid: String,
    val opprettet: String,
    val journalpostId: String,
    val status: String,
    val vedtakId: String?,
    val innenforFristen: Boolean?,
    val klagesDetPåKonkreteElementerIVedtaket: Boolean?,
    val erUnderskrevet: Boolean?,
    val begrunnelse: String?,
)

internal fun Klage.toJson(): KlageJson {
    return when (this) {
        is OpprettetKlage -> KlageJson(
            id = this.id.toString(),
            sakid = this.sakId.toString(),
            opprettet = this.opprettet.toString(),
            journalpostId = this.journalpostId.toString(),
            status = this.frontendStatus(),
            vedtakId = null,
            innenforFristen = null,
            klagesDetPåKonkreteElementerIVedtaket = null,
            erUnderskrevet = null,
            begrunnelse = null,
        )
        is VilkårsvurdertKlage.Ferdig -> KlageJson(
            id = this.id.toString(),
            sakid = this.sakId.toString(),
            opprettet = this.opprettet.toString(),
            journalpostId = this.journalpostId.toString(),
            status = this.frontendStatus(),
            vedtakId = this.vilkårsvurderinger.vedtakId.toString(),
            innenforFristen = this.vilkårsvurderinger.innenforFristen,
            klagesDetPåKonkreteElementerIVedtaket = this.vilkårsvurderinger.klagesDetPåKonkreteElementerIVedtaket,
            erUnderskrevet = this.vilkårsvurderinger.erUnderskrevet,
            begrunnelse = this.vilkårsvurderinger.begrunnelse,
        )
        is VilkårsvurdertKlage.Påbegynt -> KlageJson(
            id = this.id.toString(),
            sakid = this.sakId.toString(),
            opprettet = this.opprettet.toString(),
            journalpostId = this.journalpostId.toString(),
            status = this.frontendStatus(),
            vedtakId = this.vilkårsvurderinger.vedtakId?.toString(),
            innenforFristen = this.vilkårsvurderinger.innenforFristen,
            klagesDetPåKonkreteElementerIVedtaket = this.vilkårsvurderinger.klagesDetPåKonkreteElementerIVedtaket,
            erUnderskrevet = this.vilkårsvurderinger.erUnderskrevet,
            begrunnelse = this.vilkårsvurderinger.begrunnelse,
        )
    }
}

private enum class Typer(val verdi: String) {
    OPPRETTET("OPPRETTET"),
    VILKÅRSVURDERT_PÅBEGYNT("VILKÅRSVURDERT_PÅBEGYNT"),
    VILKÅRSVURDERT_FERDIG("VILKÅRSVURDERT_FERDIG");

    companion object {
        fun Klage.frontendStatus(): String {
            return when (this) {
                is OpprettetKlage -> OPPRETTET
                is VilkårsvurdertKlage.Ferdig -> VILKÅRSVURDERT_FERDIG
                is VilkårsvurdertKlage.Påbegynt -> VILKÅRSVURDERT_PÅBEGYNT
            }.toString()
        }
    }
    override fun toString() = verdi
}
