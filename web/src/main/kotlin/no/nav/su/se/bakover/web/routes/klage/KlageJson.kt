package no.nav.su.se.bakover.web.routes.klage

import no.nav.su.se.bakover.domain.klage.IverksattKlage
import no.nav.su.se.bakover.domain.klage.Klage
import no.nav.su.se.bakover.domain.klage.KlageTilAttestering
import no.nav.su.se.bakover.domain.klage.OpprettetKlage
import no.nav.su.se.bakover.domain.klage.VilkårsvurdertKlage
import no.nav.su.se.bakover.domain.klage.VurderingerTilKlage
import no.nav.su.se.bakover.domain.klage.VurdertKlage
import no.nav.su.se.bakover.web.routes.klage.KlageJson.VedtaksvurderingJson.Companion.toJson
import no.nav.su.se.bakover.web.routes.klage.Typer.Companion.frontendStatus
import no.nav.su.se.bakover.web.routes.klage.UtfallJson.Companion.toJson
import no.nav.su.se.bakover.web.routes.klage.ÅrsakJson.Companion.toJson
import no.nav.su.se.bakover.web.routes.søknadsbehandling.AttesteringJson
import no.nav.su.se.bakover.web.routes.søknadsbehandling.AttesteringJson.Companion.toJson

internal data class KlageJson(
    val id: String,
    val sakid: String,
    val opprettet: String,
    val journalpostId: String,
    val saksbehandler: String,
    val datoKlageMottatt: String,
    val status: String,
    val vedtakId: String?,
    val innenforFristen: String?,
    val klagesDetPåKonkreteElementerIVedtaket: Boolean?,
    val erUnderskrevet: String?,
    val begrunnelse: String?,
    val fritekstTilBrev: String?,
    val vedtaksvurdering: VedtaksvurderingJson?,
    val attesteringer: List<AttesteringJson>,
) {
    data class VedtaksvurderingJson(
        val type: String,
        val omgjør: OmgjørJson?,
        val oppretthold: OpprettholdJson?,
    ) {

        enum class Type {
            OMGJØR,
            OPPRETTHOLD;
        }

        data class OmgjørJson(
            val årsak: String?,
            val utfall: String?,
        )

        data class OpprettholdJson(
            val hjemler: List<String>,
        )

        companion object {
            fun VurderingerTilKlage.Vedtaksvurdering.toJson(): VedtaksvurderingJson {
                return when (this) {
                    is VurderingerTilKlage.Vedtaksvurdering.Påbegynt.Omgjør -> VedtaksvurderingJson(
                        type = Type.OMGJØR.toString(),
                        omgjør = OmgjørJson(
                            årsak = årsak?.toJson(),
                            utfall = utfall?.toJson(),
                        ),
                        oppretthold = null,
                    )
                    is VurderingerTilKlage.Vedtaksvurdering.Påbegynt.Oppretthold -> VedtaksvurderingJson(
                        type = Type.OPPRETTHOLD.toString(),
                        omgjør = null,
                        oppretthold = OpprettholdJson(
                            hjemler = hjemler.map { it.toString() },
                        ),
                    )
                    is VurderingerTilKlage.Vedtaksvurdering.Utfylt.Omgjør -> VedtaksvurderingJson(
                        type = Type.OMGJØR.toString(),
                        omgjør = OmgjørJson(
                            årsak = årsak.toJson(),
                            utfall = utfall.toJson(),
                        ),
                        oppretthold = null,
                    )
                    is VurderingerTilKlage.Vedtaksvurdering.Utfylt.Oppretthold -> VedtaksvurderingJson(
                        type = Type.OPPRETTHOLD.toString(),
                        omgjør = null,
                        oppretthold = OpprettholdJson(
                            hjemler = hjemler.map { it.toString() },
                        ),
                    )
                }
            }
        }
    }
}

internal fun Klage.toJson(): KlageJson {
    return when (this) {
        is OpprettetKlage -> KlageJson(
            id = this.id.toString(),
            sakid = this.sakId.toString(),
            opprettet = this.opprettet.toString(),
            journalpostId = this.journalpostId.toString(),
            saksbehandler = this.saksbehandler.navIdent,
            datoKlageMottatt = this.datoKlageMottatt.toString(),
            status = this.frontendStatus(),
            vedtakId = null,
            innenforFristen = null,
            klagesDetPåKonkreteElementerIVedtaket = null,
            erUnderskrevet = null,
            begrunnelse = null,
            fritekstTilBrev = null,
            vedtaksvurdering = null,
            attesteringer = emptyList()
        )
        is VilkårsvurdertKlage.Påbegynt -> KlageJson(
            id = this.id.toString(),
            sakid = this.sakId.toString(),
            opprettet = this.opprettet.toString(),
            journalpostId = this.journalpostId.toString(),
            saksbehandler = this.saksbehandler.navIdent,
            datoKlageMottatt = this.datoKlageMottatt.toString(),
            status = this.frontendStatus(),
            vedtakId = this.vilkårsvurderinger.vedtakId?.toString(),
            innenforFristen = this.vilkårsvurderinger.innenforFristen?.toString(),
            klagesDetPåKonkreteElementerIVedtaket = this.vilkårsvurderinger.klagesDetPåKonkreteElementerIVedtaket,
            erUnderskrevet = this.vilkårsvurderinger.erUnderskrevet?.toString(),
            begrunnelse = this.vilkårsvurderinger.begrunnelse,
            fritekstTilBrev = null,
            vedtaksvurdering = null,
            attesteringer = this.attesteringer.toJson()
        )
        is VilkårsvurdertKlage.Utfylt -> KlageJson(
            id = this.id.toString(),
            sakid = this.sakId.toString(),
            opprettet = this.opprettet.toString(),
            journalpostId = this.journalpostId.toString(),
            saksbehandler = this.saksbehandler.navIdent,
            datoKlageMottatt = this.datoKlageMottatt.toString(),
            status = this.frontendStatus(),
            vedtakId = this.vilkårsvurderinger.vedtakId.toString(),
            innenforFristen = this.vilkårsvurderinger.innenforFristen?.toString(),
            klagesDetPåKonkreteElementerIVedtaket = this.vilkårsvurderinger.klagesDetPåKonkreteElementerIVedtaket,
            erUnderskrevet = this.vilkårsvurderinger.erUnderskrevet?.toString(),
            begrunnelse = this.vilkårsvurderinger.begrunnelse,
            fritekstTilBrev = null,
            vedtaksvurdering = null,
            attesteringer = this.attesteringer.toJson()
        )
        is VilkårsvurdertKlage.Bekreftet -> KlageJson(
            id = this.id.toString(),
            sakid = this.sakId.toString(),
            opprettet = this.opprettet.toString(),
            journalpostId = this.journalpostId.toString(),
            saksbehandler = this.saksbehandler.navIdent,
            datoKlageMottatt = this.datoKlageMottatt.toString(),
            status = this.frontendStatus(),
            vedtakId = this.vilkårsvurderinger.vedtakId.toString(),
            innenforFristen = this.vilkårsvurderinger.innenforFristen?.toString(),
            klagesDetPåKonkreteElementerIVedtaket = this.vilkårsvurderinger.klagesDetPåKonkreteElementerIVedtaket,
            erUnderskrevet = this.vilkårsvurderinger.erUnderskrevet?.toString(),
            begrunnelse = this.vilkårsvurderinger.begrunnelse,
            fritekstTilBrev = null,
            vedtaksvurdering = null,
            attesteringer = this.attesteringer.toJson()
        )
        is VurdertKlage.Påbegynt -> KlageJson(
            id = this.id.toString(),
            sakid = this.sakId.toString(),
            opprettet = this.opprettet.toString(),
            journalpostId = this.journalpostId.toString(),
            saksbehandler = this.saksbehandler.navIdent,
            datoKlageMottatt = this.datoKlageMottatt.toString(),
            status = this.frontendStatus(),
            vedtakId = this.vilkårsvurderinger.vedtakId.toString(),
            innenforFristen = this.vilkårsvurderinger.innenforFristen?.toString(),
            klagesDetPåKonkreteElementerIVedtaket = this.vilkårsvurderinger.klagesDetPåKonkreteElementerIVedtaket,
            erUnderskrevet = this.vilkårsvurderinger.erUnderskrevet?.toString(),
            begrunnelse = this.vilkårsvurderinger.begrunnelse,
            fritekstTilBrev = this.vurderinger.fritekstTilBrev,
            vedtaksvurdering = this.vurderinger.vedtaksvurdering?.toJson(),
            attesteringer = this.attesteringer.toJson()
        )
        is VurdertKlage.Utfylt -> KlageJson(
            id = this.id.toString(),
            sakid = this.sakId.toString(),
            opprettet = this.opprettet.toString(),
            journalpostId = this.journalpostId.toString(),
            saksbehandler = this.saksbehandler.navIdent,
            datoKlageMottatt = this.datoKlageMottatt.toString(),
            status = this.frontendStatus(),
            vedtakId = this.vilkårsvurderinger.vedtakId.toString(),
            innenforFristen = this.vilkårsvurderinger.innenforFristen?.toString(),
            klagesDetPåKonkreteElementerIVedtaket = this.vilkårsvurderinger.klagesDetPåKonkreteElementerIVedtaket,
            erUnderskrevet = this.vilkårsvurderinger.erUnderskrevet?.toString(),
            begrunnelse = this.vilkårsvurderinger.begrunnelse,
            fritekstTilBrev = this.vurderinger.fritekstTilBrev,
            vedtaksvurdering = this.vurderinger.vedtaksvurdering.toJson(),
            attesteringer = this.attesteringer.toJson()
        )
        is VurdertKlage.Bekreftet -> KlageJson(
            id = this.id.toString(),
            sakid = this.sakId.toString(),
            opprettet = this.opprettet.toString(),
            journalpostId = this.journalpostId.toString(),
            saksbehandler = this.saksbehandler.navIdent,
            datoKlageMottatt = this.datoKlageMottatt.toString(),
            status = this.frontendStatus(),
            vedtakId = this.vilkårsvurderinger.vedtakId.toString(),
            innenforFristen = this.vilkårsvurderinger.innenforFristen?.toString(),
            klagesDetPåKonkreteElementerIVedtaket = this.vilkårsvurderinger.klagesDetPåKonkreteElementerIVedtaket,
            erUnderskrevet = this.vilkårsvurderinger.erUnderskrevet?.toString(),
            begrunnelse = this.vilkårsvurderinger.begrunnelse,
            fritekstTilBrev = this.vurderinger.fritekstTilBrev,
            vedtaksvurdering = this.vurderinger.vedtaksvurdering.toJson(),
            attesteringer = this.attesteringer.toJson()
        )
        is KlageTilAttestering -> KlageJson(
            id = this.id.toString(),
            sakid = this.sakId.toString(),
            opprettet = this.opprettet.toString(),
            journalpostId = this.journalpostId.toString(),
            saksbehandler = this.saksbehandler.navIdent,
            datoKlageMottatt = this.datoKlageMottatt.toString(),
            status = this.frontendStatus(),
            vedtakId = this.vilkårsvurderinger.vedtakId.toString(),
            innenforFristen = this.vilkårsvurderinger.innenforFristen?.toString(),
            klagesDetPåKonkreteElementerIVedtaket = this.vilkårsvurderinger.klagesDetPåKonkreteElementerIVedtaket,
            erUnderskrevet = this.vilkårsvurderinger.erUnderskrevet?.toString(),
            begrunnelse = this.vilkårsvurderinger.begrunnelse,
            fritekstTilBrev = this.vurderinger.fritekstTilBrev,
            vedtaksvurdering = this.vurderinger.vedtaksvurdering.toJson(),
            attesteringer = this.attesteringer.toJson()
        )
        is IverksattKlage -> KlageJson(
            id = this.id.toString(),
            sakid = this.sakId.toString(),
            opprettet = this.opprettet.toString(),
            journalpostId = this.journalpostId.toString(),
            saksbehandler = this.saksbehandler.navIdent,
            datoKlageMottatt = this.datoKlageMottatt.toString(),
            status = this.frontendStatus(),
            vedtakId = this.vilkårsvurderinger.vedtakId.toString(),
            innenforFristen = this.vilkårsvurderinger.innenforFristen?.toString(),
            klagesDetPåKonkreteElementerIVedtaket = this.vilkårsvurderinger.klagesDetPåKonkreteElementerIVedtaket,
            erUnderskrevet = this.vilkårsvurderinger.erUnderskrevet?.toString(),
            begrunnelse = this.vilkårsvurderinger.begrunnelse,
            fritekstTilBrev = this.vurderinger.fritekstTilBrev,
            vedtaksvurdering = this.vurderinger.vedtaksvurdering.toJson(),
            attesteringer = this.attesteringer.toJson()
        )
    }
}

private enum class Typer(val verdi: String) {

    /**
     * Dette er den første tilstanden som man kommer i når man har opprettet en ny klage.
     * Man kan ikke gå tilbake fra denne tilstanden.
     * Man skal ikke komme tilbake til dette steget.
     * Man går fram til VILKÅRSVURDERT_PÅBEGYNT dersom man fyller ut ingen eller N-1 vilkårsvurderinger.
     * Man går fram til VILKÅRSVURDERT_UTFYLT dersom man fyller ut vilkårsvurderinger.
     */
    OPPRETTET("OPPRETTET"),

    /**
     * Man kommer i denne tilstanden når man fyller ut ingen eller N-1 vilkårsvurderinger.
     * Man kan ikke gå tilbake fra denne tilstanden.
     * Man går fram til VILKÅRSVURDERT_UTFYLT dersom man fyller ut alle vilkårsvurderingene.
     */
    VILKÅRSVURDERT_PÅBEGYNT("VILKÅRSVURDERT_PÅBEGYNT"),

    /**
     * Man kommer i denne tilstanden når man fyller ut alle vilkårsvurderingene.
     * Kan kun gå tilbake til tilstanden VILKÅRSVURDERT_PÅBEGYNT.
     * Går fram til VILKÅRSVURDERT_BEKREFTET etter man 'bekrefter'.
     */
    VILKÅRSVURDERT_UTFYLT("VILKÅRSVURDERT_UTFYLT"),

    /**
     * Man kommer i denne tilstanden dersom man bekrefter VILKÅRSVURDERT_UTFYLT
     * Kan gå tilbake til VILKÅRSVURDERT_PÅBEGYNT
     * Går fram til VURDERT_PÅBEGYNT eller VURDERT_UTFYLT
     */
    VILKÅRSVURDERT_BEKREFTET("VILKÅRSVURDERT_BEKREFTET"),

    /**
     * Man kommer i denne tilstanden dersom man begynner å vurdere (påbegynt/ferdig) etter man er i tilstanden VILKÅRSVURDERT_BEKREFTET
     * Kan gå tilbake til VILKÅRSVURDERT_PÅBEGYNT dersom man fyller ut ingen eller N-1 vilkårsvurderinger.
     * Kan gå fram til VURDERT_UTFYLT dersom man fyller ut alle vurderingene.
     */
    VURDERT_PÅBEGYNT("VURDERT_PÅBEGYNT"),

    /**
     * Man kommer i denne tilstanden etter vilkårsvurderingene er bekreftet og alle vurderingene er utfylt.
     * Kan gå tilbake til VURDERT_PÅBEGYNT dersom man fjerner en eller flere vurderinger
     * Kan gå tilbake til VILKÅRSVURDERT_PÅBEGYNT dersom man fjerner en eller flere vilkårsvurderinger.
     * Kan gå fram til VURDERT_BEKREFTET dersom alle vilkårsvurderingene er utfylt og bekreftet og vurderingene er utfylt og bekreftet.
     */
    VURDERT_UTFYLT("VURDERT_UTFYLT"),

    /**
     * Alias oppsummeringssteget.
     * Man kommer i denne tilstanden dersom alle vilkårsvurderingene er utfylt og bekreftet og vurderingene er utfylt og bekreftet.
     * Kan gå fram til TIL_ATTESTERING
     */
    VURDERT_BEKREFTET("VURDERT_BEKREFTET"),

    /**
     * Man kommer i denne tilstanden dersom man var i VURDERT_BEKREFTET og valgte å sende til attestering.
     * Kan gå tilbake til VURDERT_BEKREFTET (underkjent)
     * Kan gå fram til IVERKSATT
     */
    TIL_ATTESTERING("TIL_ATTESTERING"),

    /**
     * Man kommer i denne tilstanden dersom man var i TIL_ATTESTERING og valgte å iverksette.
     * Dette er en endelig tilstand.
     * Man kommer ikke tilbake fra denne tilstanden.
     */
    IVERKSATT("IVERKSATT");

    companion object {
        fun Klage.frontendStatus(): String {
            return when (this) {
                is OpprettetKlage -> OPPRETTET

                is VilkårsvurdertKlage.Påbegynt -> VILKÅRSVURDERT_PÅBEGYNT
                is VilkårsvurdertKlage.Utfylt -> VILKÅRSVURDERT_UTFYLT
                is VilkårsvurdertKlage.Bekreftet -> VILKÅRSVURDERT_BEKREFTET

                is VurdertKlage.Påbegynt -> VURDERT_PÅBEGYNT
                is VurdertKlage.Utfylt -> VURDERT_UTFYLT
                is VurdertKlage.Bekreftet -> VURDERT_BEKREFTET

                is KlageTilAttestering -> TIL_ATTESTERING

                is IverksattKlage -> IVERKSATT
            }.toString()
        }
    }

    override fun toString() = verdi
}

enum class ÅrsakJson {
    FEIL_LOVANVENDELSE,
    ULIK_SKJØNNSVURDERING,
    SAKSBEHANDLINGSFEIL,
    NYTT_FAKTUM;

    companion object {
        fun VurderingerTilKlage.Vedtaksvurdering.Årsak.toJson(): String {
            return when (this) {
                VurderingerTilKlage.Vedtaksvurdering.Årsak.FEIL_LOVANVENDELSE -> FEIL_LOVANVENDELSE
                VurderingerTilKlage.Vedtaksvurdering.Årsak.ULIK_SKJØNNSVURDERING -> ULIK_SKJØNNSVURDERING
                VurderingerTilKlage.Vedtaksvurdering.Årsak.SAKSBEHANDLINGSFEIL -> SAKSBEHANDLINGSFEIL
                VurderingerTilKlage.Vedtaksvurdering.Årsak.NYTT_FAKTUM -> NYTT_FAKTUM
            }.toString()
        }
    }
}

enum class UtfallJson {
    TIL_GUNST,
    TIL_UGUNST;

    companion object {
        fun VurderingerTilKlage.Vedtaksvurdering.Utfall.toJson(): String {
            return when (this) {
                VurderingerTilKlage.Vedtaksvurdering.Utfall.TIL_GUNST -> TIL_GUNST
                VurderingerTilKlage.Vedtaksvurdering.Utfall.TIL_UGUNST -> TIL_UGUNST
            }.toString()
        }
    }
}
